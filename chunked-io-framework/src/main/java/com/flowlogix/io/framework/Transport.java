/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.IO_THREAD_STACK_SIZE;
import static com.flowlogix.io.framework.IOProperties.Props.MAX_EXEC_THREADS;
import static com.flowlogix.io.framework.IOProperties.Props.MAX_IO_THREADS;
import static com.flowlogix.io.framework.IOProperties.Props.USING_SELECT_LOOP;
import com.flowlogix.io.framework.executor.ScalingThreadPoolExecutor;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.SocketOption;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.nio.channels.NetworkChannel;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 *
 * @author lprimak
 */
public class Transport {
    private static final Logger log = Logger.getLogger(Transport.class.getName());
    final IOProperties props;
    final ExecutorService ioExec;
    final ExecutorService processorExec;
    private final AtomicInteger ioThreadCount = new AtomicInteger();
    private final AtomicInteger processorThreadCount = new AtomicInteger();
    final SelectLoop selectLoop;
    private static final SocketOption<Boolean> useHighPerformanceSockets = new HighPerformanceSocketOption();
    private static final SocketOption<Long> nativeSignalOption = new SignalSocketOption();
    private final ThreadWalkerInterruptor threadWalker = new ThreadWalkerInterruptor(this);
    private final ServerSocketChannel interruptChannel;


    public Transport(IOProperties props) {
        this.props = props;
        int ioStackSize = props.getProperty(IO_THREAD_STACK_SIZE);

        ioExec = newCachedThreadPool(r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        String.format("I/O-Thread-%d", ioThreadCount.incrementAndGet()), ioStackSize),
                props.getProperty(MAX_IO_THREADS));
        processorExec = newCachedThreadPool(r -> new Thread(Thread.currentThread().getThreadGroup(),
                r, String.format("Processor-%d", processorThreadCount.incrementAndGet()), ioStackSize),
                props.getProperty(MAX_EXEC_THREADS));

        selectLoop = props.getProperty(USING_SELECT_LOOP) ? new NonBlockingSelectLoop() : new BlockingSelectLoop(this);
        if (selectLoop.isBlocking()) {
            try {
                interruptChannel = ServerSocketChannel.open();
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        } else {
            interruptChannel = null;
        }
    }

    public void start() {
        selectLoop.start();
        if (selectLoop.isBlocking()) {
            threadWalker.start();
        }
    }

    public void stop() {
        selectLoop.stop();
        ioExec.shutdown();
        processorExec.shutdown();
        if (selectLoop.isBlocking()) {
            threadWalker.stop();
        }
    }

    static Runnable logExceptions(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                logException(t);
            }
        };
    }

    <T> Callable<T> logExceptions(String operation, Callable<Long> nativeThrFn, Callable<T> r) {
        return () -> {
            ThreadWalkerInterruptor.TaskTime taskTime = null;
            try {
                taskTime = threadWalker.addTask(operation, nativeThrFn.call());
                return r.call();
            } catch (Throwable t) {
                logException(t);
            } finally {
                taskTime.taskFinished();
            }
            return null;
        };
    }

    static private void logException(Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        if (t.getCause() instanceof AsynchronousCloseException || t instanceof ClosedSelectorException
                || t.getCause() instanceof ClosedChannelException) {
            log.fine(sw.toString());
        } else {
            log.severe(sw.toString());
        }
    }

    void setHighPerformance(NetworkChannel socket) throws IOException {
        if (selectLoop.isBlocking()) {
            try {
                socket.setOption(useHighPerformanceSockets, true);
            } catch (UnsupportedOperationException e) {
                throw new IllegalStateException("High Performance Chunked I/O mode cannot run without patched NIO module", e);
            }
        }
    }

    Long getNativeThreadFn() throws IOException {
        return interruptChannel.getOption(nativeSignalOption);
    }

    void interrupt(long thread) throws IOException {
        interruptChannel.setOption(nativeSignalOption, thread);
    }

    private static class HighPerformanceSocketOption implements SocketOption<Boolean> {
        @Override
        public String name() {
            return "UseHighPerformanceSockets";
        }

        @Override
        public Class<Boolean> type() {
            return Boolean.class;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    private static class SignalSocketOption implements SocketOption<Long> {
        @Override
        public String name() {
            return "SignalSocketOption";
        }

        @Override
        public Class<Long> type() {
            return Long.class;
        }

        @Override
        public String toString() {
            return name();
        }
    }

    ExecutorService newCachedThreadPool(ThreadFactory threadFactory, int maxThreads) {
        return new ScalingThreadPoolExecutor(0, maxThreads,
                60L, TimeUnit.SECONDS,
                threadFactory);
    }
}

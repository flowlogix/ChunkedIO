/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.USING_SELECT_LOOP;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ClosedSelectorException;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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


    public Transport(IOProperties props) {
        this.props = props;

        ioExec = Executors.newCachedThreadPool(r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        String.format("I/O-Thread-%d", ioThreadCount.incrementAndGet()), 1024));
        processorExec = Executors.newCachedThreadPool(r -> new Thread(Thread.currentThread().getThreadGroup(),
                r, String.format("Processor-%d", processorThreadCount.incrementAndGet()), 1024));

        selectLoop = props.getProperty(USING_SELECT_LOOP) ? new NonBlockingSelectLoop() : new BlockingSelectLoop(this);
    }

    public void start() {
        selectLoop.start();
    }

    public void stop() {
        selectLoop.stop();
        ioExec.shutdown();
        processorExec.shutdown();
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

    static<T> Callable<T> logExceptions(Callable<T> r) {
        return () -> {
            try {
                return r.call();
            } catch (Throwable t) {
                logException(t);
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

    void checkSocket(Object socket) {
        if (selectLoop instanceof BlockingSelectLoop && !socket.getClass()
                .getSimpleName().endsWith("WithBlockingDisabled")) {
            throw new IllegalStateException("High Performance Chunked I/O mode cannot run without patched NIO module");
        }
    }
}

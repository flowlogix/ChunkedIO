/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.Transport.logException;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

/**
 *
 * @author lprimak
 */
public class BlockingSelectLoop implements SelectLoop {
    private static final Logger log = Logger.getLogger(BlockingSelectLoop.class.getName());
    private final Transport transport;
    private volatile boolean started;
    private final ConcurrentLinkedQueue<Callable<Boolean>> queue = new ConcurrentLinkedQueue<>();

    public BlockingSelectLoop(Transport transport) {
        this.transport = transport;
    }

    @Override
    public void start() {
        started = true;
        while (started) {
            Callable<Boolean> callable = queue.poll();
            if (callable == null) {
                break;
            }
            transport.ioExec.submit(callable);
        }
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public void registerAccept(Server server) {
        registerAccept(server.socket, () -> server.accept(server.socket));
    }

    public void registerAccept(java.nio.channels.Channel channel, Callable<Boolean> acceptFn) {
        Callable<Boolean> callable = submitInLoop("accept", acceptFn,
                channel::isOpen, transport::getNativeThread);
        if (!started) {
            queue.offer(callable);
        } else {
            transport.ioExec.submit(callable);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        if (channel.requestedReadCount.incrementAndGet() == 1) {
            transport.ioExec.submit(submitInLoop("read", channel::read,
                    channel.channel::isOpen, transport::getNativeThread));
        }
    }

    @Override
    public boolean unregisterRead(Channel channel) {
        return channel.requestedReadCount.decrementAndGet() != 0;
    }

    @Override
    public void registerWrite(Channel channel) {
        if (channel.requestedWriteCount.incrementAndGet() == 1) {
            transport.ioExec.submit(submitInLoop("write", channel::write,
                    channel.channel::isOpen, transport::getNativeThread));
        }
    }

    @Override
    public boolean unregisterWrite(Channel channel) {
        return channel.requestedWriteCount.decrementAndGet() != 0;
    }

    private<ChannelType> Callable<Boolean> submitInLoop(String operation, Callable<Boolean> callable,
            Callable<Boolean> isOpenFn, Callable<Long> nativeThrFn) {
        return logExceptions(operation, nativeThrFn, () -> {
            boolean resubmit = callable.call();
            if (resubmit && isOpenFn.call()) {
                transport.ioExec.submit(submitInLoop(operation, callable, isOpenFn, nativeThrFn));
            }
            return resubmit;
        });
    }

    private Callable<Boolean> logExceptions(String operation, Callable<Long> nativeThrFn, Callable<Boolean> r) {
        return () -> {
            ThreadWalkerInterruptor.TaskTime taskTime = null;
            try {
                taskTime = transport.threadWalker.addTask(operation, nativeThrFn.call());
                return r.call();
            } catch (Throwable t) {
                logException(t);
            } finally {
                taskTime.taskFinished();
            }
            return null;
        };
    }

    @Override
    public boolean isBlocking() {
        return true;
    }
}

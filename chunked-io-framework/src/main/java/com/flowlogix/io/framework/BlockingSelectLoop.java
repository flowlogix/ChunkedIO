/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

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

    private void run(Callable<Boolean> callable) {
        transport.ioExec.submit(transport.logExceptions(callable));
    }

    @Override
    public void start() {
        started = true;
        while (started) {
            Callable<Boolean> callable = queue.poll();
            if (callable == null) {
                break;
            }
            run(callable);
        }
    }

    @Override
    public void stop() {
        started = false;
    }

    @Override
    public void registerAccept(Server server) {
        Callable<Boolean> callable = submitInLoop(server.socket, () -> server.accept(server.socket), server.socket::isOpen);
        if (!started) {
            queue.offer(callable);
        } else {
            run(callable);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        if (channel.requestedReadCount.incrementAndGet() == 1) {
            transport.ioExec.submit(submitInLoop(channel.channel, channel::read, channel.channel::isOpen));
        }
    }

    @Override
    public boolean unregisterRead(Channel channel) {
        return channel.requestedReadCount.decrementAndGet() != 0;
    }

    @Override
    public void registerWrite(Channel channel) {
        if (channel.requestedWriteCount.incrementAndGet() == 1) {
            transport.ioExec.submit(submitInLoop(channel.channel, channel::write, channel.channel::isOpen));
        }
    }

    @Override
    public boolean unregisterWrite(Channel channel) {
        return channel.requestedWriteCount.decrementAndGet() != 0;
    }

    private<ChannelType> Callable<Boolean> submitInLoop(ChannelType channel, Callable<Boolean> callable,
            Callable<Boolean> isOpenFn) {
        return transport.logExceptions(() -> {
            boolean resubmit = callable.call();
            if (resubmit && isOpenFn.call()) {
                transport.ioExec.submit(submitInLoop(channel, callable, isOpenFn));
            }
            return resubmit;
        });
    }

    @Override
    public boolean isBlocking() {
        return true;
    }
}

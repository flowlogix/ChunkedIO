/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS;
import java.net.SocketException;
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
        transport.ioExec.submit(Transport.logExceptions(callable));
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
        try {
            server.socket.socket().setSoTimeout(transport.props.<Long>getProperty(SOCKET_TIMEOUT_IN_MILLIS).intValue());
        } catch (SocketException ex) {
            throw new RuntimeException(ex);
        }
        Callable<Boolean> callable = submitInLoop(server.socket, () -> server.accept(server.socket));
        if (!started) {
            queue.offer(callable);
        } else {
            run(callable);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        transport.ioExec.submit(submitInLoop(channel.channel, channel::read));
    }

    @Override
    public void registerWrite(Channel channel) {
        if (channel.requestedWriteCount.incrementAndGet() == 1) {
            transport.ioExec.submit(submitInLoop(channel.channel, channel::write));
        }
    }

    @Override
    public boolean unregisterWrite(Channel channel) {
        return channel.requestedWriteCount.decrementAndGet() != 0;
    }

    private Callable<Boolean> submitInLoop(java.nio.channels.Channel channel, Callable<Boolean> callable) {
        return Transport.logExceptions(() -> {
            boolean resubmit = callable.call();
            if (resubmit && channel.isOpen()) {
                transport.ioExec.submit(submitInLoop(channel, callable));
            }
            return resubmit;
        });
    }
}

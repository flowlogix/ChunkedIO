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

/**
 *
 * @author lprimak
 */
public class BlockingSelectLoop implements SelectLoop {
    private final Transport transport;
    private volatile boolean started;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();

    public BlockingSelectLoop(Transport transport) {
        this.transport = transport;
    }

    private void run(Runnable runnable) {
        transport.ioExec.submit(Transport.logExceptions(runnable));
    }

    @Override
    public void start() {
        started = true;
        while (started) {
            Runnable runnable = queue.poll();
            if (runnable == null) {
                break;
            }
            run(runnable);
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
        Runnable runnable = submitInLoop(server.socket, () -> server.accept(server.socket), () -> true);
        if (!started) {
            queue.offer(runnable);
        } else {
            run(runnable);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        transport.ioExec.submit(submitInLoop(channel.channel, channel::read, () -> true));
    }

    @Override
    public void registerWrite(Channel channel) {
        channel.setWriting(true);
        transport.ioExec.submit(submitInLoop(channel.channel, channel::write, channel::isWriting));
    }

    @Override
    public void unregisterWrite(Channel channel) {
        channel.setWriting(false);
    }

    private Runnable submitInLoop(java.nio.channels.Channel channel, Runnable r, Callable<Boolean> resubmitCondition) {
        return Transport.logExceptions(() -> {
            r.run();
            try {
                if (channel.isOpen() && resubmitCondition.call()) {
                    transport.ioExec.submit(submitInLoop(channel, r, resubmitCondition));
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}

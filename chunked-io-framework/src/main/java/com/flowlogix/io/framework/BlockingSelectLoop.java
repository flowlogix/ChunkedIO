/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS;
import java.net.SocketException;
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
            server.socket.socket().setSoTimeout(transport.props.getProperty(SOCKET_TIMEOUT_IN_MILLIS));
        } catch (SocketException ex) {
            throw new RuntimeException(ex);
        }
        Runnable runnable = submitInLoop(server.socket, () -> server.accept(server.socket));
        if (!started) {
            queue.offer(runnable);
        } else {
            run(runnable);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        transport.ioExec.submit(submitInLoop(channel.channel, channel::read));
    }

    private Runnable submitInLoop(java.nio.channels.Channel channel, Runnable r) {
        return Transport.logExceptions(() -> {
            r.run();
            if (channel.isOpen()) {
                transport.ioExec.submit(submitInLoop(channel, r));
            }
        });
    }
}

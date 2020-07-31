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
    private static final LoopControl noopLoopControl = new LoopControl() { };

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
        Runnable runnable = submitInLoop(server.socket, () -> server.accept(server.socket), noopLoopControl);
        if (!started) {
            queue.offer(runnable);
        } else {
            run(runnable);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        transport.ioExec.submit(submitInLoop(channel.channel, channel::read, noopLoopControl));
    }

    @Override
    public void registerWrite(Channel channel) {
        channel.setWriting(true);
        if (!channel.writeLoopControl.isRunning()) {
            transport.ioExec.submit(submitInLoop(channel.channel, channel::write, channel.writeLoopControl));
        }
    }

    @Override
    public void unregisterWrite(Channel channel) {
        channel.setWriting(false);
    }

    interface LoopControl {
        default boolean resubmit() { return true; }
        default void setRunning(boolean tf) { }
        default boolean isRunning() { return false; }
    }

    private Runnable submitInLoop(java.nio.channels.Channel channel, Runnable r, LoopControl loopControl) {
        loopControl.setRunning(true);
        return Transport.logExceptions(() -> {
            r.run();
            try {
                if (channel.isOpen() && loopControl.resubmit()) {
                    transport.ioExec.submit(submitInLoop(channel, r, loopControl));
                } else {
                    loopControl.setRunning(false);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        });
    }
}

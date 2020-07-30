/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
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
    public void configure(Server server) {
        try {
            server.socket.socket().setSoTimeout(transport.props.getProperty(SOCKET_TIMEOUT_IN_MILLIS));
        } catch (SocketException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void register(Server server, int selectionKey) {
        Runnable runnable = () -> {
            while (server.socket.isOpen()) {
                if ((selectionKey & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) {
                    server.accept(server.socket);
                }
            }
        };
        if (!started) {
            queue.offer(runnable);
        } else {
            run(runnable);
        }
    }
}

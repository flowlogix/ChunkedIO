/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.ACCEPTOR_POOL_SIZE;
import static com.flowlogix.io.framework.IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS;
import java.net.SocketException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.stream.IntStream;

/**
 *
 * @author lprimak
 */
public class BlockingSelectLoop implements SelectLoop {
    private final Server server;
    private volatile boolean started;
    private final ConcurrentLinkedQueue<Runnable> queue = new ConcurrentLinkedQueue<>();


    public BlockingSelectLoop(Server server) {
        this.server = server;
    }

    private void run(Runnable runnable) {
        IntStream.rangeClosed(1, server.props.getProperty(ACCEPTOR_POOL_SIZE))
                .forEach(ii -> server.acceptorExec.submit(Server.logExceptions(runnable)));
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
    public void configure() {
        try {
            server.socket.socket().setSoTimeout(server.props.getProperty(SOCKET_TIMEOUT_IN_MILLIS));
        } catch (SocketException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void register(int selectionKey) {
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

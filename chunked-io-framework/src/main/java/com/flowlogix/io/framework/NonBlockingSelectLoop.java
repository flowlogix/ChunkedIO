/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.Transport.logExceptions;
import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.spi.SelectorProvider;

/**
 *
 * @author lprimak
 */
public class NonBlockingSelectLoop implements SelectLoop {
    private final Selector selector;
    private volatile boolean started;
    private final Thread selectLoopThread = new Thread(logExceptions(this::run), "SelectLoop");


    public NonBlockingSelectLoop() {
        try {
            this.selector = SelectorProvider.provider().openSelector();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void run() {
        try {
            while (started) {
                selector.select(key -> {
                    if (key.channel() instanceof ServerSocketChannel) {
                        ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                        Server server = (Server)key.attachment();
                        server.accept(channel);
                    }
                });
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void start() {
        started = true;
        selectLoopThread.start();
    }

    @Override
    public void stop() {
        started = false;
        try {
            selector.close();
            selectLoopThread.join();
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void configure(Server server) {
        try {
            server.socket.configureBlocking(false);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void register(Server server, int selectionKey) {
        try {
            server.socket.register(selector, selectionKey, server);
        } catch (ClosedChannelException ex) {
            throw new RuntimeException(ex);
        }
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.Transport.logExceptions;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author lprimak
 */
public class NonBlockingSelectLoop implements SelectLoop {
    private final Selector selector;
    private volatile boolean started;
    private final Thread selectLoopThread = new Thread(logExceptions(this::run), "SelectLoop");
    private static final Logger log = Logger.getLogger(NonBlockingSelectLoop.class.getName());


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
                    try {
                        if (key.channel() instanceof ServerSocketChannel) {
                            ServerSocketChannel channel = (ServerSocketChannel) key.channel();
                            Server server = (Server) key.attachment();
                            server.accept(channel);
                        }

                        if (key.channel() instanceof SocketChannel) {
                            Channel channel = (Channel) key.attachment();
                            if (key.isValid() && key.isReadable()) {
                                channel.read();
                            }
                            if (key.isValid() && key.isWritable()) {
                                channel.write();
                            }
                        }
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "SelectLoop Exception", ex);
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
    public void registerAccept(Server server) {
        try {
            server.socket.configureBlocking(false);
            server.socket.register(selector, SelectionKey.OP_ACCEPT, server);
            selector.wakeup();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void registerRead(Channel channel) {
        try {
            channel.channel.configureBlocking(false);
            channel.channel.register(selector, SelectionKey.OP_READ, channel);
            selector.wakeup();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void registerWrite(Channel channel) {
        try {
            channel.channel.register(selector, SelectionKey.OP_WRITE, channel);
            selector.wakeup();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean unregisterWrite(Channel channel) {
        channel.channel.keyFor(selector).interestOps(SelectionKey.OP_READ);
        return false;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }
}

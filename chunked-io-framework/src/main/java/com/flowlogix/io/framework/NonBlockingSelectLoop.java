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
import java.util.concurrent.LinkedBlockingQueue;
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
    private final LinkedBlockingQueue<NewOps> registerQueue = new LinkedBlockingQueue<>();
    private static final Logger log = Logger.getLogger(NonBlockingSelectLoop.class.getName());

    class NewOps {
        Channel channel;
        boolean isWrite;

        public NewOps(Channel channel, boolean iswrite) {
            this.channel = channel;
            this.isWrite = iswrite;
        }
    }

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
                                if (!channel.read() && channel.channel.isOpen()) {
                                    channel.channel.keyFor(selector).interestOpsAnd(SelectionKey.OP_WRITE);
                                }
                            }
                            if (key.isValid() && key.isWritable()) {
                                if (!channel.write() && channel.channel.isOpen()) {
                                    channel.channel.keyFor(selector).interestOpsAnd(SelectionKey.OP_READ);
                                }
                            }
                        }
                    } catch (Exception ex) {
                        log.log(Level.WARNING, "SelectLoop Exception", ex);
                    }
                });
                NewOps newOps;
                while ((newOps = registerQueue.poll()) != null) {
                    SelectionKey selectionKey = newOps.channel.channel.keyFor(selector);
                    int ops = newOps.isWrite? SelectionKey.OP_WRITE : SelectionKey.OP_READ;
                    if (selectionKey != null && selectionKey.isValid()) {
                        selectionKey.interestOpsOr(ops);
                    } else if (selectionKey == null) {
                        newOps.channel.channel.register(selector, ops, newOps.channel);
                    }
                }
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
            if (channel.requestedReadCount.incrementAndGet() == 1) {
                registerQueue.add(new NewOps(channel, false));
                selector.wakeup();
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public boolean unregisterRead(Channel channel) {
        return channel.requestedReadCount.decrementAndGet() != 0;
    }

    @Override
    public void registerWrite(Channel channel) {
        if (channel.requestedWriteCount.incrementAndGet() == 1) {
            registerQueue.add(new NewOps(channel, true));
            selector.wakeup();
        }
    }

    @Override
    public boolean unregisterWrite(Channel channel) {
        return channel.requestedWriteCount.decrementAndGet() != 0;
    }

    @Override
    public boolean isBlocking() {
        return false;
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.ACCEPTOR_POOL_SIZE;
import static com.flowlogix.io.framework.IOProperties.Props.ACCEPT_BACKLOG;
import static com.flowlogix.io.framework.IOProperties.Props.PORT;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.net.StandardSocketOptions;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 *
 * @author lprimak
 */
public class Server {
    private static final Logger log = Logger.getLogger(Server.class.getName());
    private final IOProperties props;
    private final ServerSocketChannel socket;
    private final ExecutorService acceptorExec;
    final ExecutorService channelExec;
    private final AtomicInteger acceptorThreadCount = new AtomicInteger();
    private final AtomicInteger channelThreadCount = new AtomicInteger();
    private final ConcurrentLinkedQueue<Channel> channels = new ConcurrentLinkedQueue<>();
    private final Runnable acceptorRunnable;

    public Server(IOProperties props) {
        this.props = props;
        try {
            socket = ServerSocketChannel.open();
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        acceptorExec = Executors.newFixedThreadPool(props.getProperty(ACCEPTOR_POOL_SIZE),
                r -> new Thread(Thread.currentThread().getThreadGroup(), r,
                        String.format("Acceptor-%d", acceptorThreadCount.incrementAndGet()), 1024));
        channelExec = Executors.newCachedThreadPool(r -> new Thread(Thread.currentThread().getThreadGroup(),
                r, String.format("Processor-%d", channelThreadCount.incrementAndGet()), 1024));

        acceptorRunnable = logExceptions(() -> {
            while (socket.isOpen()) {
                try {
                    channels.add(new Channel(this, socket.accept()));
                } catch (AsynchronousCloseException ex) {
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        });
    }

    public void start() {
        try {
            socket.bind(new InetSocketAddress(props.getProperty(PORT)), props.getProperty(ACCEPT_BACKLOG));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        // +++ hook here
        IntStream.rangeClosed(1, props.getProperty(ACCEPTOR_POOL_SIZE)).forEach((ii) -> acceptorExec.submit(acceptorRunnable));
    }

    public void stop() {
        try {
            socket.close();
            channels.forEach(Channel::close);
            channels.clear();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        acceptorExec.shutdown();
        channelExec.shutdown();
    }

    static Runnable logExceptions(Runnable r) {
        return () -> {
            try {
                r.run();
            } catch (Throwable t) {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                t.printStackTrace(pw);
                if (t.getCause() instanceof AsynchronousCloseException) {
                    log.fine(sw.toString());
                } else {
                    log.severe(sw.toString());
                }
            }
        };

    }
}

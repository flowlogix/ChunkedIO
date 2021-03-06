/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.ACCEPT_BACKLOG;
import static com.flowlogix.io.framework.IOProperties.Props.MAX_ACCEPT_THREADS;
import com.flowlogix.io.framework.SelectLoop.IOResult;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.channels.ServerSocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;
import java.util.stream.IntStream;

/**
 *
 * @author lprimak
 */
public class Server {
    private static final Logger log = Logger.getLogger(Server.class.getName());
    final ServerSocketChannel socket;
    private final Transport transport;
    private final int port;
    private final ConcurrentLinkedQueue<Channel> channels = new ConcurrentLinkedQueue<>();
    private final MessageHandler messageHandler;

    public Server(Transport transport, int port, MessageHandler messageHandler) {
        this.transport = transport;
        this.port = port;
        this.messageHandler = messageHandler;
        try {
            socket = ServerSocketChannel.open();
            socket.setOption(StandardSocketOptions.SO_REUSEADDR, true);
            transport.setHighPerformance(socket);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    IOResult accept(ServerSocketChannel channel) {
        try {
            channels.add(new Channel(transport, messageHandler, channel.accept()));
        } catch (SocketTimeoutException ex) {
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        return new IOResult(true, null);
    }

    public void start() {
        try {
            socket.bind(new InetSocketAddress(port), transport.props.getProperty(ACCEPT_BACKLOG));
            IntStream.rangeClosed(1, transport.props.getProperty(MAX_ACCEPT_THREADS))
                    .forEach(ii -> transport.selectLoop.registerAccept(this));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void stop() {
        try {
            socket.close();
            channels.forEach(Channel::close);
            channels.clear();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
}

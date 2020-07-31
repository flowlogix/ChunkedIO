/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;

/**
 *
 * @author lprimak
 */
public class Channel {
    final SocketChannel channel;
    private final ByteBuffer readBuf;
    private final ByteBuffer writeBuf;
    private StringBuilder sb;
    private MessageHandler handler = (c, m) -> { throw new IllegalStateException("No Message Handler"); };


    Channel(Transport transport, SocketChannel channel) {
        this.channel = channel;
        try {
            readBuf = ByteBuffer.allocateDirect(channel.getOption(StandardSocketOptions.SO_RCVBUF));
            writeBuf = ByteBuffer.allocateDirect(channel.getOption(StandardSocketOptions.SO_SNDBUF));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        transport.selectLoop.registerRead(this);
    }

    void close() {
        try {
            channel.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public Channel setHandler(MessageHandler handler) {
        this.handler = handler;
        return this;
    }

    public void write(String message) {
        try {
            // +++ BLOCKING
            channel.write(ByteBuffer.wrap(message.getBytes()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    void read() {
        try {
            channel.read(readBuf);
            if (!readBuf.hasRemaining()) {
                if (sb == null) {
                    sb = new StringBuilder();
                }
                sb.append(StandardCharsets.UTF_8.decode(readBuf.flip()));
                readBuf.clear();
                if (sb.charAt(sb.length() - 1) != System.lineSeparator().charAt(0)) {
                    return;
                }
            } else if (readBuf.position() != 0
                    && StandardCharsets.UTF_8.decode(readBuf.slice(readBuf.position() - 1, 1)).charAt(0)
                    != System.lineSeparator().charAt(0)) {
                return;
            }

            if (sb == null) {
                sb = new StringBuilder();
            }
            sb.append(StandardCharsets.UTF_8.decode(readBuf.flip()));
            handler.onMessage(this, sb.toString());
            readBuf.clear();
            sb = null;
        } catch (IOException | BufferOverflowException ex) {
            throw new RuntimeException(ex);
        }
    }
}

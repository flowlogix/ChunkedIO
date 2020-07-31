/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TransferQueue;

/**
 *
 * @author lprimak
 */
public class Channel {
    final SocketChannel channel;
    private final Transport transport;
    private final ByteBuffer readBuf;
    private ByteBuffer writeBuf;
    private ByteBuffer writeChunk;
    private final int writeChunkSize;
    private StringBuilder readerMessageBuilder;
    private MessageHandler handler = (c, m) -> { throw new IllegalStateException("No Message Handler"); };
    private TransferQueue<String> writeQ = new LinkedTransferQueue<>();
    private final long timeout;


    Channel(Transport transport, SocketChannel channel) {
        this.channel = channel;
        this.transport = transport;
        try {
            readBuf = ByteBuffer.allocateDirect(channel.getOption(StandardSocketOptions.SO_RCVBUF));
            writeChunkSize = 5; //channel.getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        timeout = transport.props.getProperty(SOCKET_TIMEOUT_IN_MILLIS);
        transport.selectLoop.registerReadWrite(this);
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
            if (channel.isOpen()) {
                writeQ.transfer(message);
            }
        } catch (InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    void write() {
        try {
            if (writeBuf == null) {
                String message = writeQ.poll(timeout, TimeUnit.MILLISECONDS);
                if (message != null) {
                    writeBuf = StandardCharsets.UTF_8.encode(message);
                }
            }
            if (writeBuf != null) {
                if (writeChunk == null) {
                    int nextChunk = Math.min(writeBuf.remaining(), writeChunkSize);
                    int position = writeBuf.position();
                    writeChunk = writeBuf.slice(position, nextChunk);
                    writeBuf.position(position + nextChunk);
                }
                channel.write(writeChunk);
                if (!writeChunk.hasRemaining()) {
                    writeChunk.clear();
                    writeChunk = null;
                }
                if (!writeBuf.hasRemaining()) {
                    writeBuf.clear();
                    writeBuf = null;
                }
            }
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
    }

    void read() {
        try {
            int readVal = channel.read(readBuf);
            if (readVal == -1) {
                channel.close();
                return;
            }
            if (!readBuf.hasRemaining()) {
                if (readerMessageBuilder == null) {
                    readerMessageBuilder = new StringBuilder();
                }
                readerMessageBuilder.append(StandardCharsets.UTF_8.decode(readBuf.flip()));
                readBuf.clear();
                if (readerMessageBuilder.charAt(readerMessageBuilder.length() - 1) != System.lineSeparator().charAt(0)) {
                    return;
                }
            } else if (readBuf.position() != 0
                    && StandardCharsets.UTF_8.decode(readBuf.slice(readBuf.position() - 1, 1)).charAt(0)
                    != System.lineSeparator().charAt(0)) {
                return;
            }

            if (readerMessageBuilder == null) {
                readerMessageBuilder = new StringBuilder();
            }
            readerMessageBuilder.append(StandardCharsets.UTF_8.decode(readBuf.flip()));
            String message = readerMessageBuilder.toString();
            transport.processorExec.submit(Transport.logExceptions(() -> handler.onMessage(this, message)));
            readBuf.clear();
            readerMessageBuilder = null;
        } catch (IOException | BufferOverflowException ex) {
            throw new RuntimeException(ex);
        }
    }
}

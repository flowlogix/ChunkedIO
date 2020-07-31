/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import com.flowlogix.io.framework.BlockingSelectLoop.LoopControl;
import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TransferQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

/**
 *
 * @author lprimak
 */
public class Channel {
    private static final Logger log = Logger.getLogger(Channel.class.getName());
    final SocketChannel channel;
    private final Transport transport;
    private final ByteBuffer readBuf;
    private ByteBuffer writeBuf;
    private ByteBuffer writeChunk;
    private final int writeChunkSize;
    private StringBuilder readerMessageBuilder;
    private final MessageHandler handler;
    private TransferQueue<String> writeQ = new LinkedTransferQueue<>();
    final AtomicInteger writingCount = new AtomicInteger();
    final LoopControl writeLoopControl = new WriteLoopControl();

    Channel(Transport transport, MessageHandler messageHandler, SocketChannel channel) {
        this.channel = channel;
        this.transport = transport;
        try {
            readBuf = ByteBuffer.allocateDirect(channel.getOption(StandardSocketOptions.SO_RCVBUF));
            writeChunkSize = channel.getOption(StandardSocketOptions.SO_SNDBUF);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        this.handler = messageHandler;
        transport.selectLoop.registerRead(this);
    }

    void close() {
        try {
            channel.close();
            writeQ.clear();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void write(String message) {
        try {
            if (channel.isOpen()) {
                transport.selectLoop.registerWrite(this);
                writeQ.transfer(message);
            }
        } catch (InterruptedException ex) {
            if (channel.isOpen()) {
                throw new RuntimeException(ex);
            }
        }
    }

    void write() {
        try {
            if (writeBuf == null && channel.isOpen()) {
                String message = writeQ.take();
                writeBuf = StandardCharsets.UTF_8.encode(message);
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
                    transport.selectLoop.unregisterWrite(this);
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
                close();
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
            close();
            throw new RuntimeException(ex);
        }
    }

    private class WriteLoopControl implements LoopControl {
        private volatile boolean isRunning;

        @Override
        public boolean resubmit() {
            return writingCount.get() > 0;
        }

        @Override
        public void setRunning(boolean tf) {
            isRunning = tf;
        }

        @Override
        public boolean isRunning() {
            return isRunning;
        }
    }
}

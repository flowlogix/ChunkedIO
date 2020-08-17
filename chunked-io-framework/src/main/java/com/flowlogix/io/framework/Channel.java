/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.IOProperties.Props.MAX_WRITE_QUEUE;
import com.flowlogix.io.framework.SelectLoop.IOResult;
import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
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
    private final LinkedBlockingQueue<String> writeQ;
    final AtomicInteger requestedReadCount = new AtomicInteger();
    final AtomicInteger requestedWriteCount = new AtomicInteger();


    Channel(Transport transport, MessageHandler messageHandler, SocketChannel channel) {
        this.channel = channel;
        this.transport = transport;
        readBuf = ByteBuffer.allocateDirect(transport.recvbuf);
        writeChunkSize = transport.sendbuf;
        this.handler = messageHandler;
        this.writeQ = new LinkedBlockingQueue<>((int)transport.props.getProperty(MAX_WRITE_QUEUE));
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

    public void scheduleRead() {
        if (channel.isOpen()) {
            transport.selectLoop.registerRead(this);
        }
    }

    public void write(String message) {
        try {
            if (channel.isOpen()) {
                transport.selectLoop.registerWrite(this);
                if (!writeQ.offer(message, 5, TimeUnit.SECONDS)) {
                    transport.selectLoop.unregisterWrite(this);
                    throw new IllegalStateException(String.format("Timed Out Writing, writeCount = %d, queue size: %d, channel = %s",
                            requestedWriteCount.get(), writeQ.size(), System.identityHashCode(this)));
                }
            }
        } catch (InterruptedException ex) {
            if (channel.isOpen()) {
                throw new RuntimeException(ex);
            }
        }
    }

    IOResult write() {
        IOResult result = new IOResult(true, null);
        try {
            if (writeBuf == null && channel.isOpen()) {
                String message = writeQ.take();
                writeBuf = StandardCharsets.UTF_8.encode(message);
                // First stage - take message from queue and put into buffer
            } else if (writeBuf != null) {
                if (writeChunk == null) {
                    int nextChunk = Math.min(writeBuf.remaining(), writeChunkSize);
                    int position = writeBuf.position();
                    writeChunk = writeBuf.slice(position, nextChunk);
                    writeBuf.position(position + nextChunk);
                    // Second stage - get next chunk (or full buffer)
                }
                channel.write(writeChunk);
                if (!writeChunk.hasRemaining()) {
                    writeChunk.clear();
                    writeChunk = null;
                    // this should always be the case in blocknig write, the
                    // whole chunk should be written out
                }
                if (!writeBuf.hasRemaining()) {
                    writeBuf.clear();
                    writeBuf = null;
                    result = new IOResult(transport.selectLoop.unregisterWrite(this), null);
                    // Fourth stage - whole message is written
                }
            } else { // channel closed
                close();
            }
        } catch (SocketTimeoutException e) {
            return result;
        } catch (IOException | InterruptedException ex) {
            throw new RuntimeException(ex);
        }
        return result;
    }

    IOResult read() {
        IOResult result = new IOResult(true, null);
        try {
            int readVal = channel.read(readBuf);
            if (readVal == -1) {
                close();
                return new IOResult(false, null);
            }
            if (!readBuf.hasRemaining()) {
                if (readerMessageBuilder == null) {
                    readerMessageBuilder = new StringBuilder();
                }
                // Stage one - first chunk has been completely read
                readerMessageBuilder.append(StandardCharsets.UTF_8.decode(readBuf.flip()));
                readBuf.clear();
                if (readerMessageBuilder.charAt(readerMessageBuilder.length() - 1) != System.lineSeparator().charAt(0)) {
                    // if whole message not ends with a newline, read more
                    return result;
                }
            } else if (readBuf.position() != 0
                    && StandardCharsets.UTF_8.decode(readBuf.slice(readBuf.position() - 1, 1)).charAt(0)
                    != System.lineSeparator().charAt(0)) {
                // if current chunk doesn't end with a newline, read more
                return result;
            }

            if (readerMessageBuilder == null) {
                readerMessageBuilder = new StringBuilder();
            }
            readerMessageBuilder.append(StandardCharsets.UTF_8.decode(readBuf.flip()));
            String message = readerMessageBuilder.toString();
            readBuf.clear();
            readerMessageBuilder = null;
            return new IOResult(transport.selectLoop.unregisterRead(this),
                    Transport.logExceptions(Transport.logExceptions(() -> handler.onMessage(this, message))));
            // end of message
        } catch (SocketTimeoutException e) {
                return new IOResult(true, null);
        } catch (IOException | BufferOverflowException ex) {
            close();
            throw new RuntimeException(ex);
        }
    }
}

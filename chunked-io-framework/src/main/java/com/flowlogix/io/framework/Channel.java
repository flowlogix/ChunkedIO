/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.io.IOException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

/**
 *
 * @author lprimak
 */
public class Channel {
    private final Server server;
    private final SocketChannel channel;
    private final int readChunkSize;
    private final int writeChunkSize;
    private final ByteBuffer readBuf;
    private final ByteBuffer writeBuf;


    Channel(Server server, SocketChannel channel) {
        this.server = server;
        this.channel = channel;
        try {
            readChunkSize = channel.getOption(StandardSocketOptions.SO_RCVBUF);
            writeChunkSize = channel.getOption(StandardSocketOptions.SO_SNDBUF);
            readBuf = ByteBuffer.allocateDirect(readChunkSize * 20);
            writeBuf = ByteBuffer.allocateDirect(writeChunkSize * 20);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        // hook +++
        server.channelExec.submit(Server.logExceptions(this::read));
    }

    void close() {
        try {
            channel.close();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void read() {
        try {
            // +++ TODO chunks
            channel.read(readBuf);
            readBuf.flip();
            channel.write(readBuf);
            readBuf.flip();
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        // blocking +++
        server.channelExec.submit(Server.logExceptions(this::read));
    }
}

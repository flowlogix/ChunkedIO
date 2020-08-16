/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import static com.flowlogix.io.framework.Transport.logExceptions;
import java.io.IOException;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 *
 * @author lprimak
 */
public class BlockInterruptorTest {
    private Transport transport;
    private ServerSocketChannel server;
    private Thread interrupterThread;
    private SocketAddress serverAddr;
    private final AtomicBoolean isRunning = new AtomicBoolean();

    @BeforeEach
    void setup() {
        transport = new Transport(new IOProperties());
        isRunning.set(true);
    }

    @AfterEach
    void teardown() throws InterruptedException {
        isRunning.set(false);
        if (interrupterThread != null) {
            interrupterThread.join();
        }
    }

    @Test
    void patchedModulePresent() throws IOException {
        assertEquals("sun.nio.ch.SocketProviderWithBlockingDisabled", System.getProperty("java.nio.channels.spi.SelectorProvider"));
        final String suffix = "WithBlockingDisabled";
        assertTrue(ServerSocketChannel.open().getClass().getName().endsWith(suffix), "ServerSocketChannel class incorrect");
        assertTrue(SocketChannel.open().getClass().getName().endsWith(suffix), "SocketChannel class incorrect");
    }

    @Test
    void nativeThreadGetter() throws IOException {
        SocketChannel sock = SocketChannel.open();
        sock.setOption(Transport.useHighPerformanceSockets, true);
        assertNotEquals(-1, sock.<Long>getOption(null));
    }

    private void setupServerSocketForInterruption() throws IOException {
        server = ServerSocketChannel.open();
        server.setOption(StandardSocketOptions.SO_REUSEADDR, true);
        server.setOption(Transport.useHighPerformanceSockets, true);
        serverAddr = server.bind(null).getLocalAddress();
        long nativeThread = transport.getNativeThread();
        interrupterThread = new Thread(logExceptions(() -> {
            while (isRunning.get()) {
                try {
                    LockSupport.parkNanos(10);
                    transport.interrupt(nativeThread);
                } catch (IOException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }));
    }

    @Test
    @Timeout(value = 50, unit = TimeUnit.MILLISECONDS)
    void acceptInterruptor() throws IOException, InterruptedException {
        setupServerSocketForInterruption();
        interrupterThread.start();
        assertThrows(SocketTimeoutException.class, () -> {
            SocketChannel channel = server.accept();
            assertNull(channel, "accept did not interrupt correctly");
        }, "did not throw SocketTimedoutException correctly from accept");
    }

    @Test
    @Timeout(value = 50, unit = TimeUnit.MILLISECONDS)
    void readInterruptor() throws IOException, InterruptedException {
        setupServerSocketForInterruption();
        Thread thread = new Thread(logExceptions(() -> {
            try {
                SocketChannel.open(serverAddr);
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));
        thread.start();
        SocketChannel channel = server.accept();
        thread.join();
        interrupterThread.start();
        assertThrows(SocketTimeoutException.class, () -> {
            assertTrue(channel.read(ByteBuffer.allocate(10)) == -1, "did not interrupt read correctly");
        }, "did not throw SocketTimedoutException correctly from read");
    }

    @Test
    @Timeout(value = 150, unit = TimeUnit.MILLISECONDS)
    void writeInterruptor() throws IOException, InterruptedException {
        setupServerSocketForInterruption();
        SocketChannel clientChannel = SocketChannel.open();
        Thread clientThread = new Thread(logExceptions(() -> {
            try {
                clientChannel.connect(serverAddr);
//                clientChannel.read(ByteBuffer.allocate(10));
            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }));
        clientThread.start();
        SocketChannel channel = server.accept();
        interrupterThread.start();
        int len = transport.sendbuf * 100;
        ByteBuffer buf = ByteBuffer.allocate(len);
        IntStream.rangeClosed(1, len).forEach(ii -> buf.put((byte) 6));
        int nwr = channel.write(buf.flip());
        clientChannel.close();
        clientThread.join();
//        System.out.format("Written: %d, len = %d", nwr, len);
        assertTrue(len > nwr, "did not interrupt write correctly");
    }
}

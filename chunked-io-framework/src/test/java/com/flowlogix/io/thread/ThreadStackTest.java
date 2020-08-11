/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.thread;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 * @author lprimak
 */
@Disabled
public class ThreadStackTest {
    private final List<Thread> threads = new ArrayList<>();
    Runnable r = () -> {
        Object lock = new Object();
        synchronized (lock) {
            try {
                lock.wait();
            } catch (InterruptedException e) {}
        }
    };

    @BeforeEach
    public void setUp() {
        IntStream.rangeClosed(0, 4000).forEach(ii -> threads.add(new Thread(Thread.currentThread().getThreadGroup(), r, "test-thread", 1024 * 144)));
        AtomicInteger count = new AtomicInteger();
        threads.stream().peek(thr -> count.incrementAndGet()).forEach(thread -> {
            try {
                thread.start();
            } catch (Throwable e) {
                e.printStackTrace();
                System.out.println("Count: " + count.get());
                throw e;
            }
        });
    }

    @AfterEach
    public void tearDown() {
        threads.forEach(thr -> {
            thr.interrupt();
            try {
                thr.join();
            } catch (InterruptedException ex) {
            }
        });
    }

    @Test
    public void hello() {

        Assertions.assertEquals("hello", "hello");
    }

    public static void main(String[] args) {
        var test = new ThreadStackTest();
        try {
            test.setUp();
        } finally {
            System.out.println("Press any key to stop ...");
            new Scanner(System.in).nextLine();
            test.tearDown();
        }
    }
}

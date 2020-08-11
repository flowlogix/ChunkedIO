/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

/**
 *
 * @author lprimak
 */
@Disabled
public class FuturesTest {

    public FuturesTest() {
    }

    @BeforeEach
    public void setUp() {
    }

    @AfterEach
    public void tearDown() {
    }

    @Test
    void hello() {
        Transport transport = new Transport(null);
        ForkJoinPool.commonPool();
        CompletableFuture.supplyAsync(() -> "hello", transport.newCachedThreadPool(null, 5));
    }
}

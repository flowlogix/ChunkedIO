/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.echoserver;

import com.flowlogix.io.framework.Server;

/**
 *
 * @author lprimak
 */
public class EchoServer {
    private final Server server;

    EchoServer(int port) {
        this.server = new Server(port);
    }


    void start() {
        server.start();
    }
}

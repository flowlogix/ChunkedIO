/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.echoserver;

import com.flowlogix.io.framework.IOProperties;
import com.flowlogix.io.framework.Server;

/**
 *
 * @author lprimak
 */
public class EchoServer {
    private final Server server;
    private final IOProperties props = new IOProperties();

    EchoServer(int port) {
        props.setProperty(IOProperties.Props.PORT, port);
        props.setProperty(IOProperties.Props.ACCEPTOR_POOL_SIZE, 5);
        props.setProperty(IOProperties.Props.ACCEPT_BACKLOG, 15);
        System.out.println(props);
        this.server = new Server(props);
    }


    EchoServer start() {
        server.start();
        return this;
    }

    void stop() {
        server.stop();
    }
}

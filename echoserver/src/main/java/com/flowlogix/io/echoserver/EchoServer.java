/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.echoserver;

import com.flowlogix.io.framework.Channel;
import com.flowlogix.io.framework.IOProperties;
import com.flowlogix.io.framework.Server;
import com.flowlogix.io.framework.Transport;

/**
 *
 * @author lprimak
 */
public class EchoServer {
    private final Transport transport;
    private final Server server;
    private final IOProperties props = new IOProperties();

    EchoServer(int port) {
//        props.setProperty(IOProperties.Props.ACCEPT_BACKLOG, 4096);
//        props.setProperty(IOProperties.Props.SOCKET_TIMEOUT_IN_MILLIS, 100L);
//        props.setProperty(IOProperties.Props.IO_THREAD_STACK_SIZE, 1024);
//        props.setProperty(IOProperties.Props.MAX_ACCEPT_THREADS, 5);
//        props.setProperty(IOProperties.Props.MAX_READ_THREADS, 3);
//        props.setProperty(IOProperties.Props.MAX_WRITE_THREADS, 3);
//        props.setProperty(IOProperties.Props.MAX_WRITE_QUEUE, 10);
//        props.setProperty(IOProperties.Props.EVENTS_UNDER_LOAD_TIMEOUT_NANOS, 5L * 1000000);
//        props.setProperty(IOProperties.Props.EVENTS_IDLE_TIMEOUT_IN_MILLIS, 50L);
//        props.setProperty(IOProperties.Props.USING_SELECT_LOOP, false);
        System.out.println(props);
        this.transport = new Transport(props);
        this.server = new Server(transport, 7777,
                (Channel channel, String msg) -> {
                    channel.write(msg);
                    channel.scheduleRead();
                });
    }


    EchoServer start() {
        server.start();
        transport.start();
        return this;
    }

    void stop() {
        server.stop();
        transport.stop();
    }
}

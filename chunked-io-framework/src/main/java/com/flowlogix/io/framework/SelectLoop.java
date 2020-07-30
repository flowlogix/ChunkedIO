/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.framework;

/**
 *
 * @author lprimak
 */
interface SelectLoop {
    void start();
    void stop();
    void registerAccept(Server server);
    void registerRead(Channel channel);
}

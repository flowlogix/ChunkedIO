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
@FunctionalInterface
public interface MessageHandler {
    void onMessage(Channel channel, String message);
}

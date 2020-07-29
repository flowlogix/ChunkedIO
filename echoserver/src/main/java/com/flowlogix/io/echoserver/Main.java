/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.echoserver;

/**
 *
 * @author lprimak
 */
public class Main {
    public static void main(String[] args) {
        new EchoServer(7777).start();
    }
}

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.flowlogix.io.echoserver;

import java.util.Scanner;

/**
 *
 * @author lprimak
 */
public class Main {
    public static void main(String[] args) {
        EchoServer server = new EchoServer(7777).start();
        System.out.println("Press any key to stop ...");
        new Scanner(System.in).nextLine();
        server.stop();
    }
}

package org.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;


public class Main {
    public static void main(String[] args)  throws IOException{
        final Logger logger = LoggerFactory.getLogger(Main.class);
        int port = 8080;
        ServerSocket serverSocket = new ServerSocket(port);
        logger.info("Proxy client started on port {}", port);
        while (true) {
            Socket clientSocket = serverSocket.accept();
            new Thread(new ProxyHandler(clientSocket)).start();
        }
    }
}
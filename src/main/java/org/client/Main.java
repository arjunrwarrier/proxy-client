package org.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


public class Main {
    private static final int PORT = 8080;
    private static final int WORKER_COUNT = 1;
    public static void main(String[] args)  throws IOException{
        final Logger logger = LoggerFactory.getLogger(Main.class);
        ServerSocket serverSocket = new ServerSocket(PORT);
        logger.info("Proxy client started on port {}", PORT);
        BlockingQueue<Socket> requestQueue = new LinkedBlockingQueue<>();
        // Start worker threads
        for (int i = 0; i < WORKER_COUNT; i++) {
            new Thread(new ProxyHandler(requestQueue)).start();
        }
        while (true) {
            Socket clientSocket = serverSocket.accept();
            requestQueue.add(clientSocket);
            logger.info("Accepted connection. Queue size: {}", requestQueue.size());
        }
    }
}
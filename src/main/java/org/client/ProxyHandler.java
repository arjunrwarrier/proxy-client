package org.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxyHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
    private static final String PROXY_SERVER_HOST = "proxy-server";
    private static final int PROXY_SERVER_PORT = 9090;
    private final BlockingQueue<Socket> requestQueue;
    private Socket proxySocket = null;
    private int connectionCount = 0;
    public ProxyHandler(BlockingQueue<Socket> requestQueue){
        this.requestQueue = requestQueue;
    }


// run() method runs forever, taking one request from the queue at a time.
    @Override
    public void run() {
        while (true) {
            try {
                Socket clientSocket = requestQueue.take();
                if (proxySocket == null || proxySocket.isClosed()) {
                    try {
                        connectionCount+=1;
                        logger.info("Attempting to connect to proxy server  {}:{}", PROXY_SERVER_HOST, PROXY_SERVER_PORT);
                        proxySocket = new Socket(PROXY_SERVER_HOST, PROXY_SERVER_PORT);
                        logger.info("Connections made to proxy server.{}", connectionCount);
                    } catch (IOException e) {
                        logger.error("Error connecting to proxy server: {}", e.getMessage());
                        Thread.sleep(1000);
                        continue;
                    }
                }
                processRequest(clientSocket);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Worker thread interrupted", e);
            }
        }
    }

    private void processRequest(Socket clientSocket) {
        try (BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()))) {

            String requestLine = in.readLine();
            if (requestLine == null) return;

            logger.info("Processing Request: {}", requestLine);

            String[] tokens = requestLine.split(" ");
            if (tokens.length < 2) return;

            String method = tokens[0];

            if (method.equalsIgnoreCase("CONNECT") || method.contains("Host:")) {
                logger.warn("Received HTTPS CONNECT request. Not supported.");
                return;
            }
            logger.info("IN HTTP");
            logger.info("Received request: {}",requestLine);
            forwardHttpRequest(requestLine, in, out);

        } catch (IOException e) {
            logger.error("Exception processing request: {}", e.getMessage());
        }
    }


    private void forwardHttpRequest(String requestLine, BufferedReader in, BufferedWriter out) throws IOException {
        logger.info("Forwarding HTTP request");
        try{
            BufferedWriter proxyOut = new BufferedWriter(new OutputStreamWriter(proxySocket.getOutputStream()));
            BufferedReader proxyIn = new BufferedReader(new InputStreamReader(proxySocket.getInputStream()));
            proxyOut.write(requestLine + "\r\n");

            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                proxyOut.write(headerLine + "\r\n");
            }
            proxyOut.write("\r\n");
            proxyOut.flush();

            String responseLine;
            while ((responseLine = proxyIn.readLine()) != null) {
                if (responseLine.equals("END_OF_RESPONSE")) {
                    logger.info("Received END_OF_RESPONSE from server. Finishing response processing.");
                    break;
                }
                out.write(responseLine + "\r\n");
            }
            out.flush();
            logger.info("Completed HTTP request");
        } catch (IOException e) {
            logger.error("Error forwarding HTTP request: {}", e.getMessage());
            if (proxySocket != null && !proxySocket.isClosed()) {
                try {
                    proxySocket.close();
                    logger.warn("Closed broken proxy socket. PLEASE TRY AGAIN...");
                } catch (IOException ex) {
                    logger.error("Error closing broken proxy socket: {}", ex.getMessage());
                }
                proxySocket = null;
            }
        }
    }
}

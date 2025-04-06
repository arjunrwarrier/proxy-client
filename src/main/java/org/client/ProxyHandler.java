package org.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxyHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
    private static final String PROXY_SERVER_HOST = "proxy-server";
    private static final int PROXY_SERVER_PORT = 9090;
//    Instead of handling requests in separate threads, to process requests one at a time
    private final BlockingQueue<Socket> requestQueue;

    public ProxyHandler(BlockingQueue<Socket> requestQueue) {
        this.requestQueue = requestQueue;
    }


// run() method runs forever, taking one request from the queue at a time.
    @Override
    public void run() {
        while (true) {
            try {
                // Take the next request from the queue (blocks if empty)
                Socket clientSocket = requestQueue.take();
                processRequest(clientSocket);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Worker thread interrupted", e);
                break;
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

            if (method.equalsIgnoreCase("CONNECT")) {
                logger.warn("Received HTTPS CONNECT request. Not supported. Closing connection.");
            }
            logger.info("IN HTTP");
            logger.info("Received request: {}",requestLine);
            forwardHttpRequest(requestLine, in, out);

        } catch (IOException e) {
            logger.error("Exception processing request: {}", e.getMessage());
        }
    }


    private void forwardHttpRequest(String requestLine, BufferedReader in, BufferedWriter out) {
        logger.info("Forwarding HTTP request");
        try (Socket proxySocket = new Socket(PROXY_SERVER_HOST, PROXY_SERVER_PORT);
             BufferedWriter proxyOut = new BufferedWriter(new OutputStreamWriter(proxySocket.getOutputStream()));
             BufferedReader proxyIn = new BufferedReader(new InputStreamReader(proxySocket.getInputStream()))) {

            proxyOut.write(requestLine + "\r\n");

            String headerLine;
            while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
                proxyOut.write(headerLine + "\r\n");
            }
            proxyOut.write("\r\n");
            proxyOut.flush();

            String responseLine;
            while ((responseLine = proxyIn.readLine()) != null) {
                out.write(responseLine + "\r\n");
            }
            out.flush();
            logger.info("Completed HTTP request");
        } catch (IOException e) {
            logger.error("Error forwarding HTTP request: {}", e.getMessage());
        }
    }


    //TODO: IMPLEMENT HTTPS handling
    private void handleConnect(String target, BufferedWriter out, Socket clientSocket) {
        try {
            Socket proxySocket = new Socket(PROXY_SERVER_HOST, PROXY_SERVER_PORT);
            OutputStream proxyOutStream = proxySocket.getOutputStream();
            InputStream proxyInStream = proxySocket.getInputStream();

            OutputStream clientOut = clientSocket.getOutputStream();
            InputStream clientIn = clientSocket.getInputStream();

            BufferedWriter proxyOut = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));
            proxyOut.write("CONNECT " + target + "\r\n\r\n");
            proxyOut.flush();

            // Read response (you can use BufferedReader here temporarily)
            BufferedReader proxyIn = new BufferedReader(new InputStreamReader(proxyInStream));
            String responseLine = proxyIn.readLine();
            if (responseLine != null && responseLine.contains("200")) {
                out.write("HTTP/1.1 200 Connection Established\r\n\r\n");
                out.flush();

                // Switch to raw streams
                relayData(clientSocket, proxySocket);
            } else {
                out.write("HTTP/1.1 502 Bad Gateway\r\n\r\n");
                out.flush();
            }
        } catch (IOException e) {
            System.err.println("Error handling CONNECT through proxy-server: " + e.getMessage());
        }
    }

    //TODO: IMPLEMENT HTTPS method
    private void relayData(Socket clientSocket, Socket proxySocket) {
        Thread clientToProxy = new Thread(() -> forwardStream(clientSocket, proxySocket));
        Thread proxyToClient = new Thread(() -> forwardStream(proxySocket, clientSocket));

        clientToProxy.start();
        proxyToClient.start();
    }

    //TODO: IMPLEMENT HTTPS method
    private void forwardStream(Socket inputSocket, Socket outputSocket) {
        try (InputStream input = inputSocket.getInputStream();
             OutputStream output = outputSocket.getOutputStream()) {

            byte[] buffer = new byte[8192];
            int bytesRead;

            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
                output.flush();
            }
        } catch (SocketException e) {
            logger.error("Socket closed: {}", e.getMessage());
        } catch (IOException ignored) {
        } finally {
            try {
                inputSocket.close();
                outputSocket.close();
            } catch (IOException ignored) {
            }
        }
    }


}

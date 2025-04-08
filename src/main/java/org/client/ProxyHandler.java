package org.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.Arrays;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class ProxyHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
    private static final String PROXY_SERVER_HOST = "localhost";
    private static final int PROXY_SERVER_PORT = 9090;
//    Instead of handling requests in separate threads, to process requests one at a time
    private final BlockingQueue<Socket> requestQueue;
    Socket proxySocket = new Socket(PROXY_SERVER_HOST, PROXY_SERVER_PORT);

    public ProxyHandler(BlockingQueue<Socket> requestQueue) throws IOException {
        this.requestQueue = requestQueue;
    }


// run() method runs forever, taking one request from the queue at a time.
    @Override
    public void run() {
            try {
                // Take the next request from the queue (blocks if empty)
                Socket clientSocket = requestQueue.take();
                processRequest(clientSocket);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Worker thread interrupted", e);
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
        try (
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
            proxyIn.close();
            proxyOut.close();
            in.close();
            out.close();
            logger.info("Completed HTTP request");
        } catch (IOException e) {
            logger.error("Error forwarding HTTP request: {}", e.getMessage());
        }
    }

}

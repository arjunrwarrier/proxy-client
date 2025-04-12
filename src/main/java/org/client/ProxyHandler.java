package org.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.MalformedURLException;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.BlockingQueue;

public class ProxyHandler implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(ProxyHandler.class);
    private static final int READ_TIMEOUT_MS = 20000;
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
                        proxySocket.setSoTimeout(READ_TIMEOUT_MS);
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
            logger.info("Processing Request: {}", requestLine);
            if (requestLine == null || requestLine.isEmpty()) {
                logger.warn("Received empty or null request line from client {}. Closing connection.", clientSocket.getRemoteSocketAddress());
                return;
            }
            String[] tokens = requestLine.split(" ");
            if (tokens.length < 3) {
                logger.warn("Invalid request line format ({} parts): {}", tokens.length, requestLine);
                return;
            }
            String method = tokens[0];
            String urlString = tokens[1];
            URL requestedUrl;
            try {
                requestedUrl = new URL(urlString);
                String head = requestedUrl.getProtocol();
                String host = requestedUrl.getHost();
                if (method.equalsIgnoreCase("CONNECT")) {
                    logger.warn("Received HTTPS CONNECT request. Not supported.");
                    return;
                }
                if (!head.equalsIgnoreCase("http") && !head.equalsIgnoreCase("https")) {
                    logger.warn("Unsupported URL received: {}", head);
                    return;
                }

                if (host == null || host.trim().isEmpty()) {
                    logger.warn("URL is missing host: {}", urlString);
                    return;
                }
                if (!host.contains(".")) {
                    if (!host.equalsIgnoreCase("localhost")) {
                        logger.warn("Host '{}' does not contain a dot and is not localhost. Might not be a valid domain.", host);
                        return;
                    }
                }

                logger.debug("valid url recieved: {}", urlString);

            } catch (MalformedURLException e) {
                logger.warn("not a valid url: {} error: {}", urlString, e.getMessage());
                return;
            }
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
                    logger.error("Closed broken proxy socket. PLEASE TRY AGAIN...");
                } catch (IOException ex) {
                    logger.error("Error closing broken proxy socket: {}", ex.getMessage());
                }
                proxySocket = null;
            }
        }
    }
}

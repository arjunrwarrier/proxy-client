# Proxy Client Setup using Docker

This project sets up a proxy client running in a Docker container, which acts as a bridge between the userClient and the proxy-server. The proxy client is configured to run on port 8080. This guide will walk you through the steps required to set up a running environment for the proxy client using Docker.

TO SETUP A RUNNING ENVIRONMENT USING DOCKER
------------------------------
## SIMPLY USING DOCKER COMPOSE NO OTHER STEPS REQUIRED    https://github.com/arjunrwarrier/proxy-docker-compose
### 1. clone the project
### 2. Docker build, maven packaging will be done inside the image creation

`docker build -t proxy-client:latest .`

### 3.By default, Docker containers don’t share localhost unless they are on the same network.Create a custom docker network if not already created, make sure to run both in same network.

  `docker network create proxy-network`
  
### 4.Run both containers in same network exposing required ports

  `docker run -d --name proxy-client --network=proxy-network -p 8080:8080 proxy-client:latest`
  
### 5.Use curl command to check if the proxy is working, check logs of both server and client containers

  `curl -x http://localhost:8080 http://httpforever.com/`

  To see multiple images loaded live, use localhost 8080 as proxy and load in browser :http://www.http2demo.io/
 
  `curl -x http://localhost:8080 http://www.http2demo.io/`
    

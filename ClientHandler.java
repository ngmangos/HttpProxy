import java.io.*;
import java.net.*;
// Conditions:
// No body: GET req, HEAD, Connect -> Do not perform loop
// Body: GET res, POST
// If 'Transfer-encoding' present -> wait until 0 or -1 returned
// If 'Content-length' present
// In java implementation for InputStream, 0 return for InputStream.read(byte[1024]) is unpredicted
//      outputStream.write("".getBytes()) will not contact the client
// bytesRead = -1,0 -> Always stop loop
// bytesRead cannot already be equal to 0,-1 as the iteration would not continue

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private Proxy proxy;

    public ClientHandler(Socket socket, Proxy proxyServer) {
        clientSocket = socket;
        proxy = proxyServer;
    }

    public void run() {
        try (InputStream clientInputStream = this.clientSocket.getInputStream();
            OutputStream clientOutputStream = this.clientSocket.getOutputStream()) {
            boolean keepAlive = true;
            while (keepAlive) {
                try {
                    clientSocket.setSoTimeout(proxy.getTimeOut());
                    byte[] buffer = new byte[1024];
                    int bytesRead = clientInputStream.read(buffer);
                    ByteArrayOutputStream bytesArrayStream = new ByteArrayOutputStream();
                    
                    if (bytesRead == -1 || bytesRead == 0) break;
                    bytesArrayStream.write(buffer, 0, bytesRead);
                    
                    String requestString = new String(buffer, 0, bytesRead);
                    Request request = new Request(requestString, bytesArrayStream.toByteArray());
                    Response response;
                    if (request.getHost().isEmpty()) {
                        response = new Response(request, new ResponseFile(400, "No host in request."));
                        returnException(clientOutputStream, request, response);
                        break;
                    } else if (request.getPort() == proxy.getPort() && request.getHost().equals(proxy.getHost())) {
                        response = new Response(request, new ResponseFile(421, "Proxy address detected in request."));
                        returnException(clientOutputStream, request, response);
                        break;
                    } else if (request.isEmpty() || request.isInvalid()) {
                        response = new Response(request, new ResponseFile(400, "Invalid request."));
                        returnException(clientOutputStream, request, response);
                        break;
                    }

                    if (!request.messageComplete()) {
                        bytesArrayStream.reset();
                        bytesArrayStream.write(buffer, request.getHeaderEndLocation(), bytesRead - request.getHeaderEndLocation());
                        while (!request.messageComplete())  {
                            bytesRead = clientInputStream.read(buffer);
                            if (bytesRead == -1 || bytesRead == 0)
                                break;
                            bytesArrayStream.write(buffer, 0, bytesRead);
                            request.addToMessage(bytesArrayStream.toByteArray());
                        }
                    }

                    if (request.getRequestType().equals("CONNECT")) {
                        handleConnect(clientInputStream, clientOutputStream, request);
                        break;
                    }

                    Cache cache = proxy.getCache();
                    String cachedlog = "-";
                    if (request.getRequestType().equals("GET")) {
                        cache.lock();
                        if (cache.responseInCache(request)) {
                            cachedlog = "H";
                            response = cache.getResponse(request);
                        } else {
                            cachedlog = "M";
                            response = handleOrigin(request, proxy);
                            cache.addResponseToCache(response);
                        }
                        cache.unlock();
                    } else {
                        response = handleOrigin(request, proxy);
                        
                    }
                    clientOutputStream.write(response.buildClientHeaders().getBytes());
                    clientOutputStream.write(response.getMessageBody());
                    clientOutputStream.flush();

                    if (request.connectionClose()) {
                        keepAlive = false;
                    }
                    printLog(cachedlog, request, response);
                } catch (SocketTimeoutException e) {
                    break;
                }  catch (IOException e) {
                    break;
                }
            }
        } catch (IOException e) {
        }  finally {
            try {
                if (!clientSocket.isClosed()) this.clientSocket.close();
            } catch (IOException e) {}        
        }   
    }

    private static Response handleOrigin(Request request, Proxy proxy) {
        Response response;
        try (Socket originServerSocket = new Socket(request.getHost(), request.getPort());
            InputStream originInputStream = originServerSocket.getInputStream();
            OutputStream originOutputStream = originServerSocket.getOutputStream()
        ) {
            originServerSocket.setSoTimeout(proxy.getTimeOut());
            originOutputStream.write(request.buildServerHeaders().getBytes());
            originOutputStream.write(request.getMessageBody());
            originOutputStream.flush();

            byte[] buffer = new byte[1024];
            int bytesRead = originInputStream.read(buffer);
            ByteArrayOutputStream bytesArrayStream = new ByteArrayOutputStream();

            if (bytesRead == -1 || bytesRead == 0) throw new IOException("Closed unexpectedly.");
            
            String responseString = new String(buffer, 0, bytesRead);
            bytesArrayStream.write(buffer, 0, bytesRead);
            response = new Response(responseString, bytesArrayStream.toByteArray(), request);
            
            if (response.isInvalid()) 
                return new Response(request, new ResponseFile(500, "Invalid response returned."));

            if (!response.messageComplete()) {
                bytesArrayStream.reset();
                bytesArrayStream.write(buffer, response.getHeaderEndLocation(), bytesRead - response.getHeaderEndLocation());
                while (!response.messageComplete())  {
                    bytesRead = originInputStream.read(buffer);
                    if (bytesRead == -1 || bytesRead == 0)
                        break;
                    bytesArrayStream.write(buffer, 0, bytesRead);
                    response.addToMessage(bytesArrayStream.toByteArray());
                }
            }
        } catch (UnknownHostException e) {
            response = new Response(request, new ResponseFile(502, "Could not resolve."));
        } catch (ConnectException e) {
            response = new Response(request, new ResponseFile(502, "Connection refused."));
        } catch (SocketTimeoutException e) {
            response = new Response(request, new ResponseFile(504, "Gateway timed out."));
        } catch (IOException e) {
            response = new Response(request, new ResponseFile(502, "Closed unexpectedly."));
        }
        return response;
    }

    private void handleConnect(InputStream clientInputStream, OutputStream clientOutputStream, Request request) {
        // Construct response to send to client
        // Check if request is sent to the server this.proxy port and stuff not equal to request port
        Response response;
        if (request.getPort() != 443) {
            response = new Response(request, new ResponseFile(400, "Invalid port for connect."));
            returnException(clientOutputStream, request, response);
            return;
        }
        try (Socket originServerSocket = new Socket(request.getHost(), request.getPort());
            InputStream serverInputStream = originServerSocket.getInputStream();
            OutputStream serverOutputStream = originServerSocket.getOutputStream()) {
            response = new Response(200, "Connection Established", "CONNECT");
            clientOutputStream.write(response.buildClientHeaders().getBytes());
            clientOutputStream.flush();
            printLog("-", request, response);
            
            Thread clientToServerThread = new Thread(new ConnectionThread(clientOutputStream, serverInputStream));
            Thread serverToClientThread = new Thread(new ConnectionThread(serverOutputStream, clientInputStream));
            clientToServerThread.start();
            serverToClientThread.start();
            
            clientToServerThread.join();
            serverToClientThread.join();
        } catch (ConnectException e) {
            response = new Response(request, new ResponseFile(502, "Connection refused."));
            returnException(clientOutputStream, request, response);
        } catch (IOException e) {
            // IO Error with streams
        } catch (InterruptedException e) {
            // do something
        }
    }

    private void returnException(OutputStream clientOutputStream, Request request, Response response) {
        // This function is used to send exceptions to the client, if there is an IO exception there
        // is nothing else we can do but stop gracefully
        try {
            clientOutputStream.write(response.buildClientHeaders().getBytes());
            clientOutputStream.write(response.getMessageBody());
        } catch (IOException e) {
        } finally {
            printLog("-", request, response);
        }
    }

    private class ConnectionThread implements Runnable {
        private OutputStream outputStream;
        private InputStream inputStream;

        public ConnectionThread(OutputStream outputStreamArg, InputStream inputStreamArg) {
            outputStream = outputStreamArg;
            inputStream = inputStreamArg;
        }

        public void run() {
            while (true) {
                try {
                    byte[] buffer = new byte[1024];
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1 || bytesRead == 0) break;

                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                } catch (IOException e) {
                    return;
                }
            }
        }
    }

    private void printLog(String cachelog, Request request, Response response) {
        String[] hostArray = this.clientSocket.getRemoteSocketAddress().toString().split(":");
        String host = hostArray[0].startsWith("/") ? hostArray[0].substring(1) : hostArray[0];
        int port = this.clientSocket.getPort();
        String output = String.format("%s %d %s [%s] \"%s\" %d %d", host, port, cachelog, response.getDateString(), 
            request.getRequestLine(), response.getStatusCode(), response.getContentLength());
        System.out.println(output);
    }
}  
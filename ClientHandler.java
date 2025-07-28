/**
 * @author Nicholas-Mangos
 * @since 28-07-2025
 * Code for assignment 1 of UNSW course COMP3331, Computer Networks
 */
import java.io.*;
import java.net.*;

// ClientHandler is the class that handles client connections concurrently by implementing runnable
public class ClientHandler implements Runnable {
    private final Socket clientSocket;
    private final Proxy proxy;
    private static final int BUFFER_SIZE = 8192;

    public ClientHandler(Socket socket, Proxy proxy) {
        this.clientSocket = socket;
        this.proxy = proxy;
    }

    // This function takes in a client connection, and reads and processes requests
    public void run() {
        try (final InputStream clientInputStream = this.clientSocket.getInputStream();
            final OutputStream clientOutputStream = this.clientSocket.getOutputStream()) {
            boolean keepAlive = true;
            while (keepAlive) {
                try {
                    clientSocket.setSoTimeout(proxy.getTimeOut());

                    // If request is null then client connection was closed or the request did not contain CRLF
                    // With 8192 buffer, request string should contain CRLF
                    Request request = readRequest(clientInputStream);
                    if (request == null) {
                        break;
                    } else if (requestException(request, clientOutputStream)) {
                        break;
                    } else if (request.getHttpMethod().equals("CONNECT")) {
                        handleConnect(clientInputStream, clientOutputStream, clientSocket, request);
                        break;
                    }

                    Cache cache = proxy.getCache();
                    Response response;
                    String cachedlog = "-";
                    if (request.getHttpMethod().equals("GET")) {
                        cache.lock();
                        if (cache.responseInCache(request)) {
                            cachedlog = "H";
                            response = cache.getResponse(request);
                            cache.unlock();
                        } else {
                            // Cache is unlocked for handleOrigin (prevent stall)
                            cache.unlock();
                            cachedlog = "M";
                            response = handleOrigin(request, proxy);
                            cache.lock();
                            cache.cacheResponse(response);
                            cache.unlock();
                        }
                    } else {
                        response = handleOrigin(request, proxy); 
                    }
                    // Message body stored as bytes so not converted
                    clientOutputStream.write(response.buildHeaders().getBytes());
                    clientOutputStream.write(response.getMessageBody());
                    clientOutputStream.flush();

                    if (request.connectionClose()) {
                        keepAlive = false;
                    }
                    printLog(cachedlog, request, response);
                } catch (SocketTimeoutException e) {
                    // client timed out: end connection quietly
                    break;
                }  catch (IOException e) {
                    // client disconnected or network error: end connection quietly
                    break;
                }
            }
        } catch (IOException e) {
            // client disconnected or network error: end connection quietly
        }  finally {
            try {
                if (!clientSocket.isClosed()) this.clientSocket.close();
            } catch (IOException e) {
                // client disconnected or network error: end connection quietly
            }        
        }     
    }

    // Read in request bytes from the client input stream
    // Convert into the request object
    private Request readRequest (InputStream clientInputStream) {
        try {
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = clientInputStream.read(buffer);
            ByteArrayOutputStream bytesArrayStream = new ByteArrayOutputStream();
            
            // Return null if client connection was closed or the request did not contain CRLF
            // With 8192 buffer, request string should contain CRLF
            if (bytesRead == -1 || bytesRead == 0) {
                return null;
            }
            // Find the end of the headers with CRLF. Copy bytes directly after that
            String requestString = new String(buffer, 0, bytesRead);
            int headerEnd = requestString.indexOf("\r\n\r\n");
            if (headerEnd == -1) {
                return null;
            }
            int bodyStart = headerEnd == -1 ? 0 : headerEnd + 4;
            bytesArrayStream.write(buffer, bodyStart, bytesRead - bodyStart);

            Request request = new Request(requestString.substring(0, headerEnd), bytesArrayStream.toByteArray());
            // If more bytes expected, read in until connection closed or until length is correct
            if (!request.messageComplete()) {
                while (!request.messageComplete())  {
                    bytesRead = clientInputStream.read(buffer);
                    if (bytesRead == -1 || bytesRead == 0)
                        break;
                    bytesArrayStream.write(buffer, 0, bytesRead);
                    request.setMessageBody(bytesArrayStream.toByteArray());
                }
            }
            return request;
        }  catch (IOException e) {
            // client disconnected or network error: end connection quietly
            return null;
        }
    }

    // Determine if request is invalid and return expections accordingly
    private boolean requestException(Request request, OutputStream clientOutputStream) {
        Response response;
        if (request.getHost().isEmpty()) {
            response = new Response(request, new ResponseFile(400, "No host in request."));
            returnException(clientOutputStream, request, response);
            return true;
        } else if (request.getPort() == proxy.getPort() && request.getHost().equals(proxy.getHost())) {
            response = new Response(request, new ResponseFile(421, "Proxy address detected in request."));
            returnException(clientOutputStream, request, response);
            return true;
        } else if (request.isInvalid()) {
            response = new Response(request, new ResponseFile(400, "Invalid request."));
            returnException(clientOutputStream, request, response);
            return true;
        }
        return false;
    }

    // Simple helper function to reduce repetition and return exceptions to client
    private void returnException(OutputStream clientOutputStream, Request request, Response response) {
        try {
            clientOutputStream.write(response.buildHeaders().getBytes());
            clientOutputStream.write(response.getMessageBody());
            clientOutputStream.flush();
        } catch (IOException e) {
            // Stop quietly if there is an IO exception with client
        } finally {
            printLog("-", request, response);
        }
    }

    // Format and print log based on input request and response
    private void printLog(String cachelog, Request request, Response response) {
        String[] hostArray = this.clientSocket.getRemoteSocketAddress().toString().split(":");
        String host = hostArray[0].startsWith("/") ? hostArray[0].substring(1) : hostArray[0];
        int port = this.clientSocket.getPort();
        String output = String.format("%s %d %s [%s] \"%s\" %d %d", host, port, cachelog, request.getDateString(), 
            request.getRequestLine(), response.getStatusCode(), response.getContentLength());
        System.out.println(output);
    }

    // Forward request to the origin server and process/return the response
    private Response handleOrigin(Request request, Proxy proxy) {
        Response response;
        try (final Socket originServerSocket = new Socket(request.getHost(), request.getPort());
            final InputStream originInputStream = originServerSocket.getInputStream();
            final OutputStream originOutputStream = originServerSocket.getOutputStream()
        ) {
            originServerSocket.setSoTimeout(proxy.getTimeOut());
            originOutputStream.write(request.buildHeaders().getBytes());
            originOutputStream.write(request.getMessageBody());
            originOutputStream.flush();

            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead = originInputStream.read(buffer);
            ByteArrayOutputStream bytesArrayStream = new ByteArrayOutputStream();

            if (bytesRead == -1 || bytesRead == 0) {
                throw new IOException("Closed unexpectedly.");
            }
            
            String responseString = new String(buffer, 0, bytesRead);
            int headerEnd = responseString.indexOf("\r\n\r\n");
            int bodyStart = headerEnd == -1 ? 0 : headerEnd + 4;
            bytesArrayStream.write(buffer, bodyStart, bytesRead - bodyStart);
            response = new Response(responseString.substring(0, headerEnd), bytesArrayStream.toByteArray(), request);
            // Similar to the client, if more bytes expected, read in until connection closed or until length is correct
            if (headerEnd == -1 || response.isInvalid()) {
                return new Response(request, new ResponseFile(500, "Invalid response returned."));
            }
            if (!response.messageComplete()) {
                while (!response.messageComplete())  {
                    bytesRead = originInputStream.read(buffer);
                    if (bytesRead == -1 || bytesRead == 0)
                        break;
                    bytesArrayStream.write(buffer, 0, bytesRead);
                    response.setMessageBody(bytesArrayStream.toByteArray());
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

    // Handles HTTP CONNECT between client and origin server
    private void handleConnect(InputStream clientInputStream, OutputStream clientOutputStream, Socket clientSocket, Request request) {
        Response response;
        boolean logPrinted = false;
        if (request.getPort() != 443) {
            response = new Response(request, new ResponseFile(400, "Invalid port for connect."));
            returnException(clientOutputStream, request, response);
            return;
        }
        try (final Socket originServerSocket = new Socket(request.getHost(), request.getPort());
            final InputStream serverInputStream = originServerSocket.getInputStream();
            final OutputStream serverOutputStream = originServerSocket.getOutputStream()) {
            // Send extremely simple, headerless response to client if connection established
            response = new Response(200, "Connection Established", "CONNECT");
            clientOutputStream.write(response.buildHeaders().getBytes());
            clientOutputStream.flush();
            printLog("-", request, response);
            logPrinted = true;
            clientSocket.setSoTimeout(0);
            
            // Create concurrent threads to send information between client and server and vice versa
            Thread clientToServerThread = new Thread(new ConnectionThread(clientOutputStream, serverInputStream));
            Thread serverToClientThread = new Thread(new ConnectionThread(serverOutputStream, clientInputStream));
            clientToServerThread.start();
            serverToClientThread.start();
            
            // Wait until both threads are closed before running any more code
            // (make sure sockets not accidentally closed prematurely)
            clientToServerThread.join();
            serverToClientThread.join();
            // Return/print exception responses if initial response was never sent
        } catch (UnknownHostException e) {
            if (!logPrinted) {
                response = new Response(request, new ResponseFile(502, "Could not resolve."));
                returnException(clientOutputStream, request, response);
            }
        } catch (ConnectException e) {
            if (!logPrinted) {
                response = new Response(request, new ResponseFile(502, "Connection refused."));
                returnException(clientOutputStream, request, response);
            }
        } catch (IOException e) {
            if (!logPrinted) {
                response = new Response(request, new ResponseFile(502, "Closed unexpectedly."));
                returnException(clientOutputStream, request, response);
            }
        } catch (InterruptedException e) {
            // Error with thread.join(), gracefully close
        }
    }

    // Private thread class (implement runnable) to send code between any input to an output
    private class ConnectionThread implements Runnable {
        private final OutputStream outputStream;
        private final InputStream inputStream;

        public ConnectionThread(OutputStream outputStream, InputStream inputStream) {
            this.outputStream = outputStream;
            this.inputStream = inputStream;
        }

        public void run() {
            while (true) {
                try {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int bytesRead = inputStream.read(buffer);
                    if (bytesRead == -1 || bytesRead == 0) break;

                    outputStream.write(buffer, 0, bytesRead);
                    outputStream.flush();
                } catch (IOException e) {
                    // Gracefully close socket if exception occurs
                    return;
                }
            }
        }
    }
}  
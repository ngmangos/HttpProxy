/**
 * @author Nicholas-Mangos
 * @since 28-07-2025
 * Code for assignment 1 of UNSW course COMP3331, Computer Networks
 */
import java.io.*;
import java.net.*;

// Class for the proxy server itself
// Main function contains executable code for assignment
public class Proxy {
    private static final String host = "127.0.0.1"; // 20 seconds in milliseconds
    private final int port;
    private final int timeOut;
    private final Cache cache;

    // Class used as easy method to store and pass on essential information
    private Proxy(int port, int timeOut, int maxObjectSize, int maxCacheSize) {
        this.port = port;
        this.timeOut = timeOut;
        cache = new Cache(maxObjectSize, maxCacheSize);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public int getTimeOut() {
        return timeOut;
    }

    public Cache getCache() {
        return cache;
    }

    // Main function for assignment. Only prints error messages if original arguments incorrect
    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java WebServer PORT TIMEOUT MAXOBJECT MAXCACHE");
            System.exit(1);
        }
        Proxy proxy;
        try {
            int portArg = Integer.parseInt(args[0]);
            int timeOutArg = Integer.parseInt(args[1]) * 1000;
            int maxObjectSizeArg = Integer.parseInt(args[2]);
            int maxCacheSizeArg = Integer.parseInt(args[3]);
            proxy = new Proxy(portArg, timeOutArg, maxObjectSizeArg, maxCacheSizeArg);            
        } catch (NumberFormatException e) {
            String errorPrintout = "Invalid input numbers; Port: %s, Timeout: %s, MaxObjectSize: %s, MaxCacheSize: %s";
            System.err.println(String.format(errorPrintout, args[0], args[1], args[2], args[3]));
            System.exit(1);
            return;
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;  
        }
        
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
            while (true) {
                try {
                    // Accept new connections to proxy server and create threads for each (concurrent clients)
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket, proxy)).start();
                } catch (IOException e) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {}
    }
}
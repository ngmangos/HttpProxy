import java.io.*;
import java.net.*;

public class Proxy {
    private static final String host = "127.0.0.1"; // 20 seconds in milliseconds
    private int port;
    private int timeOut;
    private Cache cache;

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

    private Proxy(int port, int timeOut, int maxObjectSize, int maxCacheSize) {
        this.port = port;
        this.timeOut = timeOut;
        cache = new Cache(maxObjectSize, maxCacheSize);
    }

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java WebServer PORT TIMEOUT MAXOBJECT MAXCACHE");
            System.exit(1);
        }
        Proxy proxy;
        try {
            int port = Integer.parseInt(args[0]);
            int timeOut = Integer.parseInt(args[1]) * 1000;
            int maxObjectSize = Integer.parseInt(args[2]);
            int maxCacheSize = Integer.parseInt(args[3]);
            proxy = new Proxy(port, timeOut, maxObjectSize, maxCacheSize);            
        } catch (NumberFormatException e) {
            String errorPrintout = "Invalid input numbers; Port: %s, Timeout: %s, MaxObjectSize: %s, MaxCacheSize: %s";
            System.err.println(String.format(errorPrintout, args[0], args[1], args[2], args[3]));
            System.exit(1);
            return;
        } catch (IllegalArgumentException e) {
            System.err.println(String.format("MaxCacheSize (%s) cannot be smaller than MaxCacheSize (%s)", args[3], args[2]));
            System.exit(1);
            return;  
        }
        
        try (ServerSocket serverSocket = new ServerSocket()) {
            serverSocket.setReuseAddress(true);
            serverSocket.bind(new InetSocketAddress(proxy.getHost(), proxy.getPort()));
            
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    new Thread(new ClientHandler(clientSocket, proxy)).start();
                } catch (IOException e) {
                    System.err.println("Error accepting connection: " + e.getMessage());
                }
            }
        } catch (IOException e) {}
    }
}
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Request extends Message {
    private String host = "";
    private int port = 80;
    private String file = "";
    private Header header;
    private boolean empty = false;
    private String clientConnectionHeader = "Connection: keep-alive";
    private boolean connectionClose = false;
    private String requestLine = "";

    public String getURL() {
        return host + ":" + port;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public boolean isEmpty() {
        return empty;
    }

    public boolean connectionClose() {
        return connectionClose;
    }

    public String getClientConnectionHeader() {
        return clientConnectionHeader;
    }

    public boolean contentExpected() {
        return getRequestType().equals("POST");
    }


    public String getRequestLine() {
        return requestLine;
    }

    public String buildHeaders() {
        String request = getRequestType() + " " + file + " " + getConnectionType() + "\r\n" +
            header.getHeaderString() + "\r\n";
        return request;
    }

    public Request(String request, byte[] requestBytes) {
        int bodyLocation = request.indexOf("\r\n\r\n");
        if (bodyLocation == -1) {
            empty = true;
            return;
        }

        String[] headerLines = request.substring(0, bodyLocation).split("\r\n");
        setMessageBody(requestBytes);
        
        requestLine = headerLines.length > 0 ? headerLines[0].trim() : "";
        if (requestLine.isEmpty()) {
            empty = true;
            return;
        }

        String[] requestLineArray = requestLine.split(" ");
        setRequestType(requestLineArray[0].trim());
        if (!Arrays.asList("GET", "HEAD", "POST", "CONNECT").stream().anyMatch(method -> method.equals(getRequestType()))) {
            setInvalid(true);
            return;
        }

        if (requestLineArray.length != 3) {
            setInvalid(true);
            return;
        }

        setConnectionType(requestLineArray[2]);

        Pattern getPattern = Pattern.compile(getRequestType() + "\\s+(.*)\\s+HTTP/1\\.1");
        Matcher matcher = getPattern.matcher(requestLine);

        if (!matcher.matches()) {
            setInvalid(true);
            return;
        }

        header = new Header(Arrays.copyOfRange(headerLines, 1, headerLines.length));

        String requestTarget = requestLineArray[1].trim();

        if (getRequestType().equals("CONNECT")) {
            handleConnect(requestTarget);
            return;
        }
        
        clientConnectionHeader = "Connection: " + header.getHeader("Connection");
        connectionClose = header.getHeader("Proxy-Connection").toLowerCase().contains("close");
        connectionClose = header.getHeader("Connection").toLowerCase().contains("close");

        header.updateHeader("Via: 1.1 z5417382");
        header.updateHeader("Connection: close");
        header.removeHeader("Proxy-Connection");
        setHeader(header);

        if (requestTarget.toLowerCase().startsWith("http://")) {
            requestTarget = requestTarget.substring(7);
        } else {
            setInvalid(true);
            return;
        }  
        
        String[] requestTargetArray = requestTarget.split("/", 2);
        host = requestTargetArray[0].trim().toLowerCase();
        if (requestTargetArray.length < 2) {
            file = "/";
        } else 
            file = "/" + requestTargetArray[1];

        if (host.contains(":")) {
            String[] hostNamesParts = host.split(":");
            host = hostNamesParts[0];
            port = Integer.parseInt(hostNamesParts[1]);
        }
    }

    private void handleConnect(String requestTarget) {
        // Request in authority form
        if (!requestTarget.contains(":")) {
            setInvalid(true);
            return;
        }
        String[] fileCheckArray = requestTarget.split("/");
        if (fileCheckArray.length > 1 && !fileCheckArray[1].equals("")) {
            setInvalid(true);
            return;
        }
        
        String[] hostNamesParts = requestTarget.split(":");
        this.host = hostNamesParts[0].toLowerCase();
        this.port = Integer.parseInt(hostNamesParts[1]);
    }
}

import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Request {
    private String requestType = "";
    private String host = "";
    private int port = 80;
    private String file = "";
    private String connectionType = "HTTP/1.1";
    private Header header;
    private boolean empty = false;
    private boolean invalid = false;
    private String clientConnectionHeader = "Connection: keep-alive";
    private boolean connectionClose = false;
    private byte[] messageBody = new byte[0];
    private String requestLine = "";
    private int headerEndLocation;

    public String getURL() {
        return host + ":" + port;
    }

    public String getRequestType() {
        return requestType;
    }

    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
    }

    public boolean isInvalid() {
        return invalid;
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
        return requestType.equals("POST");
    }

    public String getRequestLine() {
        return requestLine;
    }

    public boolean messageComplete() {
        if (!contentExpected())
            return true;
        if (header.hasHeader("transfer-encoding"))
            return false;
        if (!header.hasHeader("content-length"))
            return true;
        String headerString = header.getHeader("content-length");
        int contentLength = 0;
        try {
            contentLength = Integer.parseInt(headerString);
        } catch (NumberFormatException e) {
            return true;
        }
        return messageBody.length - headerEndLocation >= contentLength;
    }

    public void addToMessage(byte[] continuedBody) {
       messageBody = continuedBody;
    }

    public String buildServerHeaders() {
        String request = requestType + " " + file + " " + connectionType + "\r\n" +
                        header.getHeaderString() + "\r\n";
        return request;
    }

    public byte[] getMessageBody() {
        System.out.println("Message body length: " + Integer.toString(messageBody.length) + " HeaderEnd: " + Integer.toString(headerEndLocation));
        return Arrays.copyOfRange(messageBody, headerEndLocation, messageBody.length);
    }

    public Request(String request, byte[] requestBytes) {
        int bodyLocation = request.indexOf("\r\n\r\n");
        if (bodyLocation == -1) {
            System.out.println(1);
            empty = true;
            return;
        }
        headerEndLocation = bodyLocation + 4;

        String[] headerLines = request.substring(0, bodyLocation).split("\r\n");
        messageBody = requestBytes;
        
        requestLine = headerLines.length > 0 ? headerLines[0].trim() : "";
        if (requestLine.isEmpty()) {
            System.out.println(2);
            empty = true;
            return;
        }

        String[] requestLineArray = requestLine.split(" ");
        requestType = requestLineArray[0].trim();
        if (!Arrays.asList("GET", "HEAD", "POST", "CONNECT").stream().anyMatch(method -> method.equals(requestType))) {
            System.out.println(3);
            invalid = true;
            return;
        }

        if (requestLineArray.length != 3) {
            System.out.println(4);
            invalid = true;
            return;
        }

        connectionType = requestLineArray[2];

        Pattern getPattern = Pattern.compile(requestType + "\\s+(.*)\\s+HTTP/1\\.1");
        Matcher matcher = getPattern.matcher(requestLine);

        if (!matcher.matches()) {
            System.out.println(5);
            invalid = true;
            return;
        }

        header = new Header(Arrays.copyOfRange(headerLines, 1, headerLines.length));

        String requestTarget = requestLineArray[1].trim();

        if (requestType.equals("CONNECT")) {
            System.out.println(6);
            handleConnect(requestTarget);
            return;
        }
        
        clientConnectionHeader = "Connection: " + header.getHeader("Connection");
        connectionClose = header.getHeader("Proxy-Connection").toLowerCase().contains("close");
        connectionClose = header.getHeader("Connection").toLowerCase().contains("close");

        header.updateHeader("Via: 1.1 z5417382");
        header.updateHeader("Connection: close");
        header.removeHeader("Proxy-Connection");

        if (requestTarget.toLowerCase().startsWith("http://")) {
            requestTarget = requestTarget.substring(7);
        } else {
            System.out.println(7);
            invalid = true;
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
            this.invalid = true;
            return;
        }
        String[] fileCheckArray = requestTarget.split("/");
        if (fileCheckArray.length > 1 && !fileCheckArray[1].equals("")) {
            this.invalid = true;
            return;
        }
        
        String[] hostNamesParts = requestTarget.split(":");
        this.host = hostNamesParts[0].toLowerCase();
        this.port = Integer.parseInt(hostNamesParts[1]);
    }
}

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
    private String messageBody = "";
    private String requestLine = "";

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
        return messageBody.length() >= contentLength;
    }

    public void addToMessage(String messageContinued) {
        messageBody += messageContinued;
    }

    public String buildServerRequest() {
        String request = requestType + " " + file + " " + connectionType + "\r\n" +
                        header.getHeaderString() + "\r\n" +
                        messageBody;
        return request;
    }

    public Request(String request) {
        String[] requestArray = request.split("\r\n\r\n", 2);
        if (requestArray.length != 2) {
            empty = true;
            return;
        }

        String[] headerLines = requestArray[0].split("\r\n");
        messageBody = requestArray[1];

        requestLine = headerLines.length > 0 ? headerLines[0].trim() : "";

        if (requestLine.isEmpty()) {
            empty = true;
            return;
        }

        String[] requestLineArray = requestLine.split(" ");
        requestType = requestLineArray[0].trim();
        if (!Arrays.asList("GET", "HEAD", "POST", "CONNECT").stream().anyMatch(method -> method.equals(requestType))) {
            invalid = true;
            return;
        }

        if (requestLineArray.length != 3) {
            invalid = true;
            return;
        }

        connectionType = requestLineArray[2];

        Pattern getPattern = Pattern.compile(requestType + "\\s+(.*)\\s+HTTP/1\\.1");
        Matcher matcher = getPattern.matcher(requestLine);

        if (!matcher.matches()) {
            invalid = true;
            return;
        }

        header = new Header(Arrays.copyOfRange(headerLines, 1, headerLines.length));

        String requestTarget = requestLineArray[1].trim();

        if (requestType.equals("CONNECT")) {
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
            invalid = true;
            return;
        }  
        
        String[] requestTargetArray = requestTarget.split("/", 2);
        host = requestTargetArray[0].toLowerCase();
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

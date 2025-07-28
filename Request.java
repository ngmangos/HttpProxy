import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Request extends Message {
    private final ZonedDateTime requestDate = ZonedDateTime.now();
    private String host = "";
    private int port = 80;
    private String file = "";
    private String clientConnectionHeader = "Connection: keep-alive";
    private boolean connectionClose = false;
    private String requestLine = "";

    public Request(String request, byte[] requestBytes) {
        String[] headerLines = request.split("\r\n");
        setMessageBody(requestBytes);
        
        requestLine = headerLines.length > 0 ? headerLines[0].trim() : "";
        if (requestLine.isEmpty()) {
            setInvalid(true);
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

        Pattern getPattern = Pattern.compile(getRequestType() + "\\s+(.*)\\s+HTTP/1\\.1");
        Matcher matcher = getPattern.matcher(requestLine);

        if (!matcher.matches()) {
            setInvalid(true);
            return;
        }
        String requestTarget = requestLineArray[1].trim();

        if (getRequestType().equals("CONNECT")) {
            handleConnect(requestTarget);
            return;
        }                
        
        Header header = new Header(Arrays.copyOfRange(headerLines, 1, headerLines.length));

        clientConnectionHeader = "Connection: " + header.getHeader("Connection");
        connectionClose = header.getHeader("Proxy-Connection").toLowerCase().contains("close");
        connectionClose = header.getHeader("Connection").toLowerCase().contains("close");

        header.updateHeader("Via: 1.1 z5417382");
        header.updateHeader("Connection: close");
        header.removeHeader("Proxy-Connection");
        setHeader(header);
        processRequestTarget(requestTarget);
    }

    private void processRequestTarget(String requestTarget) {
        if (requestTarget.toLowerCase().startsWith("http://")) {
            requestTarget = requestTarget.substring(7);
        } else {
            setInvalid(true);
            return;
        }  
        
        String[] requestTargetArray = requestTarget.split("/", 2);
        this.host = requestTargetArray[0].trim().toLowerCase();
        if (requestTargetArray.length < 2) {
            this.file = "/";
        } else {
            this.file = "/" + requestTargetArray[1];
        }
            
        if (host.contains(":")) {
            String[] hostNamesParts = host.split(":");
            this.host = hostNamesParts[0];
            this.port = Integer.parseInt(hostNamesParts[1]);
        }
    }  

    public String getURL() {
        return host + ":" + Integer.toString(port) + file;
    }

    public String getDateString() {
        return requestDate.format(DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z"));
    }
    public int getPort() {
        return port;
    }

    public String getHost() {
        return host;
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
        StringBuilder sb = new StringBuilder();
        sb.append(getRequestType() + " " + file + " " + getConnectionType() + "\r\n");
        sb.append(getHeader().getHeaderString() + "\r\n");
        return sb.toString();
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

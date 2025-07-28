/**
 * @author Nicholas-Mangos
 * @since 28-07-2025
 * Code for assignment 1 of UNSW course COMP3331, Computer Networks
 */
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Request extends Message {
    // Request stores time it was created (for log)
    private final ZonedDateTime requestDate = ZonedDateTime.now();
    private String host = "";
    private int port = 80;
    private String file = "";
    private String clientConnectionHeader = "Connection: keep-alive";
    private boolean connectionClose = false;
    private String requestLine = "";

    // Create request from string of header, and bytes of body
    public Request(String headerString, byte[] bodyBytes) {
        String[] headerLines = headerString.split("\r\n");
        setMessageBody(bodyBytes);
        
        // If requestLine empty, there is no host, request invalid
        this.requestLine = headerLines.length > 0 ? headerLines[0].trim() : "";
        if (requestLine.isEmpty()) {
            setInvalid(true);
            return;
        }

        String[] requestLineArray = requestLine.split(" ");
        setHttpMethod(requestLineArray[0].trim());
        // Currently do not need (501 Not Implemented) response, can use other tiered exceptions
        if (!Arrays.asList("GET", "HEAD", "POST", "CONNECT").stream().anyMatch(method -> method.equals(getHttpMethod()))) {
            setInvalid(true);
            return;
        }

        if (requestLineArray.length != 3) {
            setInvalid(true);
            return;
        }

        // Use regex to determine if first line is formatted correctly (both absolute + authority)
        Pattern getPattern = Pattern.compile(getHttpMethod() + "\\s+(.*)\\s+HTTP/1\\.1");
        Matcher matcher = getPattern.matcher(requestLine);
        if (!matcher.matches()) {
            setInvalid(true);
            return;
        }
        String requestTarget = requestLineArray[1].trim();

        if (getHttpMethod().equals("CONNECT")) {
            handleConnect(requestTarget);
            return;
        }                
        
        // Take all header lines except top to create a header object
        Header header = new Header(Arrays.copyOfRange(headerLines, 1, headerLines.length));

        this.clientConnectionHeader = "Connection: " + header.getHeader("Connection");
        this.connectionClose = header.getHeader("Proxy-Connection").toLowerCase().contains("close");
        this.connectionClose = header.getHeader("Connection").toLowerCase().contains("close");

        header.updateHeader("Via: 1.1 z5417382");
        header.updateHeader("Connection: close");
        header.removeHeader("Proxy-Connection");
        setHeader(header);
        processRequestTarget(requestTarget);
    }

    // Helper function to set the host, file and port of the request (absolute form)
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

    // getURL is used to get the key for the cache
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

    // Connection close set in the constructor, based on original request's headers
    public boolean connectionClose() {
        return connectionClose;
    }

    public String getClientConnectionHeader() {
        return clientConnectionHeader;
    }

    public boolean contentExpected() {
        return getHttpMethod().equals("POST");
    }


    public String getRequestLine() {
        return requestLine;
    }

    public String buildHeaders() {
        StringBuilder sb = new StringBuilder();
        sb.append(getHttpMethod() + " " + file + " " + getConnectionType() + "\r\n");
        sb.append(getHeader().getHeaderString() + "\r\n");
        return sb.toString();
    }

    // Process the request for a CONNECT
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

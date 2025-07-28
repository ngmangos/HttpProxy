
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Response extends Message {
    private int statusCode = 0;
    private String reasonPhrase = "";
    private String requestURL = "";

    // Constructor to generate the most simple response: No headers, no body
    public Response(int statusCode, String reasonPhrase, String requestType) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        setRequestType(requestType);
        setHeader(new Header(new String[0]));
    }

    // Constructor to generate simple exception responses detected by proxy
    public Response(Request request, ResponseFile responseFile) {
        statusCode = responseFile.getStatusCode();
        reasonPhrase = responseFile.getReasonPhrase();
        setMessageBody(responseFile.getMessageBody().getBytes());
        setRequestType(request.getRequestType());

        Header header = new Header(new String[0]);
        header.updateHeader(request.getClientConnectionHeader());
        header.updateHeader("Content-Length: " + Integer.toString(getContentLength()));
        header.updateHeader("Content-Type: " + responseFile.getContentType());
        setHeader(header);
    }

    // Constructor to generate responses based on origin server output
    public Response(String response, byte[] responseBytes, Request request) {
        String[] lines = response.split("\r\n");

        String responseLine = lines.length > 0 ? lines[0].trim() : "";
        if (responseLine.isEmpty()) {
            setInvalid(true);
            return;
        }

        String[] responseLineArray = responseLine.split(" ", 3);
        if (responseLineArray.length < 2) {
            setInvalid(true);
            return;
        }

        statusCode = Integer.parseInt(responseLineArray[1]);
        reasonPhrase = responseLineArray.length == 3 ? responseLineArray[2] : "";
        if (statusCode != 204 || statusCode != 304) {
            setMessageBody(responseBytes);
        }

        Pattern getPattern = Pattern.compile("HTTP/1\\.1\\s+(\\d{3})(.*)");
        Matcher matcher = getPattern.matcher(responseLine);
        if (!matcher.find()) {
            setInvalid(true);
            return;
        }

        Header header = new Header(Arrays.copyOfRange(lines, 1, lines.length));
        header.updateHeader("Via: 1.1 z5417382");
        header.updateHeader(request.getClientConnectionHeader());
        setHeader(header);
        setRequestType(request.getRequestType());
        requestURL = request.getURL();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public boolean contentExpected() {
        if (statusCode < 200 || statusCode > 299) {
            return true;
        }
        return getRequestType().equals("POST") || getRequestType().equals("GET");
    }

    public String buildHeaders() {
        StringBuilder sb = new StringBuilder();
        sb.append(getConnectionType() + " " + statusCode + " " + reasonPhrase + "\r\n");
        sb.append(getHeader().getHeaderString() + "\r\n");
        return sb.toString();
    }
}

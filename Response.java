
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Response extends Message {
    private int statusCode = 200;
    private String reasonPhrase = "OK";
    private String requestURL = "";

    public int getStatusCode() {
        return statusCode;
    }

    public String getRequestURL() {
        return requestURL;
    }

    public boolean contentExpected() {
        if (statusCode < 200 || statusCode > 299)
            return true;
        return getRequestType().equals("POST") || getRequestType().equals("GET");
    }

    public String buildHeaders() {
        String response = getConnectionType() + " " + statusCode + " " + reasonPhrase + "\r\n" +
            getHeader().getHeaderString() + "\r\n";
        return response;
    }

    public Response(String response, byte[] responseBytes, Request request) {
        int bodyLocation = response.indexOf("\r\n\r\n");
        if (bodyLocation == -1) {
            setInvalid(true);
            return;
        }

        String[] responseArray = response.split("\r\n\r\n", 2);
        if (responseArray.length != 2) {
            setInvalid(true);
            return;
        }

        String[] lines = response.substring(0, bodyLocation).split("\r\n");;

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

        setConnectionType(responseLineArray[0]);
        statusCode = Integer.parseInt(responseLineArray[1]);
        reasonPhrase = responseLineArray.length == 3 ? responseLineArray[2] : "";
        if (statusCode != 204 || statusCode != 304)
            setMessageBody(responseBytes);

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

    public Response(int statusCode, String reasonPhrase, String requestType) {
        this.statusCode = statusCode;
        this.reasonPhrase = reasonPhrase;
        setRequestType(requestType);
        setHeader(new Header(new String[0]));
    }
}

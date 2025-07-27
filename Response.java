import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Response {
    private ZonedDateTime responseDate = ZonedDateTime.now();
    private String requestType = "GET";
    private String connectionType = "HTTP/1.1";
    private int statusCode = 200;
    private String reasonPhrase = "OK";
    private Header header;
    private boolean invalid = false;
    private String messageBody = "";
    private int originServerPort = 80;
    private String originServerName;

    public int getContentLength() {
        return messageBody.length();
    }

    public String getRequestType() {
        return requestType;
    }

    public boolean isInvalid() {
        return invalid;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public String getServerURL() {
        return originServerName + ":" + originServerPort;
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

    public boolean contentExpected() {
        if (statusCode < 200 || statusCode > 299)
            return true;
        return requestType.equals("POST") || requestType.equals("GET");
    }

    public void addToMessage(String messageContinued) {
        messageBody += messageContinued;
    }

    public String buildClientResponse() {
        String response = connectionType + " " + statusCode + " " + reasonPhrase + "\r\n" +
                        header.getHeaderString() + "\r\n" +
                        messageBody;
        return response;
    }
    
    public String getDateString() {
        return responseDate.format(DateTimeFormatter.ofPattern("dd/MMM/yyyy:HH:mm:ss Z"));
    }

    public static String generateConnectionResponse() {
        return "HTTP/1.1 200 Connection Established\r\n\r\n";
    }

    public Response(String response, Request request) {
        String[] responseArray = response.split("\r\n\r\n", 2);
        if (responseArray.length != 2) {
            invalid = true;
            return;
        }

        String[] lines = responseArray[0].split("\r\n");

        String responseLine = lines.length > 0 ? lines[0].trim() : "";

        if (responseLine.isEmpty()) {
            invalid = true;
            return;
        }

        String[] responseLineArray = responseLine.split(" ", 3);

        if (responseLineArray.length < 2) {
            invalid = true;
            return;
        }

        connectionType = responseLineArray[0];
        statusCode = Integer.parseInt(responseLineArray[1]);
        reasonPhrase = responseLineArray.length == 3 ? responseLineArray[2] : "";
        if (statusCode != 204 || statusCode != 304)
            messageBody = responseArray[1];

        Pattern getPattern = Pattern.compile("HTTP/1\\.1\\s+(\\d{3})(.*)");
        Matcher matcher = getPattern.matcher(responseLine);

        if (!matcher.find()) {
            invalid = true;
            return;
        }

        header = new Header(Arrays.copyOfRange(lines, 1, lines.length));

        header.updateHeader("Via: 1.1 z5417382");
        header.updateHeader(request.getClientConnectionHeader());
        requestType = request.getRequestType();
        originServerPort = request.getPort();
        originServerName = request.getHost();
    }

    public Response(Request request, ResponseFile responseFile) {
        statusCode = responseFile.getStatusCode();
        reasonPhrase = responseFile.getReasonPhrase();
        messageBody = responseFile.getMessageBody();
        requestType = request.getRequestType();

        header = new Header(new String[0]);
        header.updateHeader(request.getClientConnectionHeader());
        header.updateHeader("Content-Length: " + responseFile.getContentLength().toString());
        header.updateHeader("Content-Type: " + responseFile.getContentType());
    }

    public Response(int statusCodeArg, String reasonPhraseArg, String requestTypeArg) {
        statusCode = statusCodeArg;
        reasonPhrase = reasonPhraseArg;
        requestType = requestTypeArg;
        header = new Header(new String[0]);
    }
}

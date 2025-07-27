import java.util.Map;

public class ResponseFile {
    private Integer statusCode;
    private String messageBody;
    private String contentType = "text/html";
    private static final Map<Integer, String> reasonPhraseDict = Map.of(
        200, "OK",
        400, "Bad Request",
        421, "Misdirected Request",
        500, "Internal Server Error",
        502, "Bad Gateway",
        504, "Gateway Timeout"
    );
    
    
    public Integer getStatusCode() {
        return statusCode;
    }

    public String getReasonPhrase() {
        return reasonPhraseDict.get(statusCode);
    }

    public String getMessageBody() {
        return messageBody;
    }

    public Integer getContentLength() {
        return messageBody.length();
    }

    public String getContentType() {
        return contentType;
    }

    public ResponseFile(int statusCodeArg, String bodyPhrase) {
        statusCode = statusCodeArg;
        messageBody = "<h1> Exception " + statusCode + reasonPhraseDict.get(statusCode) + "</h1>" +
            "<p>" + bodyPhrase + "</p>";
    }
}

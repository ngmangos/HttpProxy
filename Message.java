/**
 * @author Nicholas-Mangos
 * @since 28-07-2025
 * Code for assignment 1 of UNSW course COMP3331, Computer Networks
 */

 // Parent abstract class of Request and Response to reduce repetition
// Header and messageBody both initialised to 0 for edge cases (CONNECT 200 response)
public abstract class Message {
    private String httpMethod = "";
    private final String connectionType = "HTTP/1.1";
    private Header header = new Header(new String[0]);
    private boolean invalid = false;
    private byte[] messageBody = new byte[0];

    public abstract boolean contentExpected();

    public abstract String buildHeaders();

    public String getHttpMethod() {
        return httpMethod;
    }

    protected void setHttpMethod(String httpMethod) {
        this.httpMethod = httpMethod;
    }

    public boolean isInvalid() {
        return invalid;
    }

    protected void setInvalid(boolean invalid) {
        this.invalid = invalid;
    }

    public int getContentLength() {
        return messageBody.length;
    }

    public String getConnectionType() {
        return connectionType;
    }

    protected Header getHeader() {
        return header;
    }

    protected void setHeader(Header header) {
        this.header = header;
    }

    public byte[] getMessageBody() {
        return messageBody;
    }

    public void setMessageBody(byte[] messageBody) {
        this.messageBody = messageBody;
    }

    // Determine if the current message (request/response) contains
    // all expected information
    // If transfer-encoding header present, this will always return true
    // and response/request will be read until connection closed
    public boolean messageComplete() {
        if (!contentExpected()) {
            return true;
        } else if (header.hasHeader("transfer-encoding")) {
            return false;
        } else if (!header.hasHeader("content-length")) {
            return true;
        }
        String headerString = header.getHeader("content-length");
        int contentLength = 0;
        try {
            contentLength = Integer.parseInt(headerString);
        } catch (NumberFormatException e) {
            return true;
        }
        return messageBody.length >= contentLength;
    } 
}
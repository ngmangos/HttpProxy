public abstract class Message {
    private String requestType = "";
    private final String connectionType = "HTTP/1.1";
    private Header header = new Header(new String[0]);
    private boolean invalid = false;
    private byte[] messageBody = new byte[0];

    public String getRequestType() {
        return requestType;
    }

    protected void setRequestType(String requestType) {
        this.requestType = requestType;
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

    public abstract boolean contentExpected();

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

    public abstract String buildHeaders();
}
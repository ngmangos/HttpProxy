import java.util.Map;
import java.util.Hashtable;

public class Header {
    private Map<String, String> headers = new Hashtable<>();

    public Header(String[] lines) {
        for (String header : lines) {
            String[] headerParts = header.split(":", 2);
            headers.put(headerParts[0].toLowerCase().trim(), headerParts[1].trim().toLowerCase());
        }
    }

    public void updateHeader(String newHeader) {
        String[] headerParts = newHeader.split(":", 2);
        if (headerParts.length < 2)
            return;
        headers.put(headerParts[0].trim().toLowerCase(), headerParts[1].trim().toLowerCase());
    }

    public void removeHeader(String keyword) {
        headers.remove(keyword.toLowerCase());
    }

    public String getHeader(String keyword) {
        return headers.getOrDefault(keyword.toLowerCase(), "");
    }

    public boolean hasHeader(String keyword) {
        return headers.containsKey(keyword.toLowerCase());
    }

    public String getHeaderString() {
        String resultString = "";
        for (Map.Entry<String, String> entry : headers.entrySet()) 
            resultString += entry.getKey() + ":" + entry.getValue() + "\r\n";
        return resultString;
    }
}

/**
 * @author Nicholas-Mangos
 * @since 28-07-2025
 * Code for assignment 1 of UNSW course COMP3331, Computer Networks
 */
import java.util.Map;
import java.util.HashMap;

// Header stores header values in a map
public class Header {
    private final Map<String, String> headers = new HashMap<>();

    // Header constructor takes in string array and returns header object
    // Field-names are case insensitive
    // Field-values treated as case sensitive (not converted)
    // If headerParts.length != 2 then there is no ":" and it is an invalid header
    public Header(String[] lines) {
        for (String header : lines) {
            String[] headerParts = header.split(":", 2);
            if (headerParts.length != 2) {
                continue;
            }
            headers.put(headerParts[0].toLowerCase().trim(), headerParts[1].trim());
        }
    }

    // Reject addition to header if an empty string
    public void updateHeader(String newHeader) {
        String[] headerParts = newHeader.split(":", 2);
        if (headerParts.length < 2) {
            return;
        }
        headers.put(headerParts[0].trim().toLowerCase(), headerParts[1].trim());
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

    // Return single string of all headers
    // Does not contain final CRLF (added by the response/request)
    public String getHeaderString() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : headers.entrySet()) 
            sb.append(entry.getKey() + ":" + entry.getValue() + "\r\n");
        return sb.toString();
    }
}

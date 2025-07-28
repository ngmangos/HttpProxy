/**
 * @author Nicholas-Mangos
 * @since 28-07-2025
 * Code for assignment 1 of UNSW course COMP3331, Computer Networks
 */
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

// Cache stores response in a map and linked list based the URL (host:port/file)
// Cache stores the map keys in a linked list to determine least recently used (LRU)
public class Cache {
    private final ReentrantLock lock = new ReentrantLock();
    private final int maxCacheSize;
    private final int maxObjectSize;
    private final Map<String, Response> cacheMap = new HashMap<String, Response>();
    private final LinkedList<String> cacheList = new LinkedList<String>();
    private int totalCacheStorage = 0;

    public Cache(int maxObjectSize, int maxCacheSize) throws IllegalArgumentException {
        if (maxObjectSize > maxCacheSize) {
            String error = String.format("MaxObjectSize (%d) greater than MaxCacheSize (%d)", maxObjectSize, maxCacheSize);
            throw new IllegalArgumentException(error);
        }
        this.maxCacheSize = maxCacheSize;
        this.maxObjectSize = maxObjectSize;
    }

    // Cache stores a ReentrantLock that can be accessed by public
    // Important for concurrent sockets sharing resources
    public void lock() { 
        lock.lock(); 
    }

    public void unlock() { 
        lock.unlock(); 
    }

    public boolean responseInCache(Request request) {
        String key = request.getURL();
        return cacheMap.containsKey(key);
    }

    // Retrieve a GET response from the cache (recommend to use responseInCache) first
    // function still works w/out if key not present and returns null
    // If key present, will push to start of linked list and return cached response
    public Response getResponse(Request request) {
        String key = request.getURL();
        cacheList.removeFirstOccurrence(key);
        cacheList.addFirst(key);
        return cacheMap.get(key);
    }

    // Store a GET response in the cache
    // If status code not 200, content size > maxObjectSize, reject response
    // If key already in cache, atleast the input is more recent,
    // so delete currently held (from list and map) and continue
    // Remove items from cache until totalCached + response size is less than the max cache
    // (removing based on LRU)
    // Then add item
    public void cacheResponse(Response response) {
        int contentSize = response.getContentLength();
        if (contentSize > maxObjectSize || response.getStatusCode() != 200) {
            return;
        }
        String key = response.getRequestURL();
        if (cacheMap.containsKey(key)) {
            cacheList.removeFirstOccurrence(key);
            totalCacheStorage -= cacheMap.get(key).getContentLength();
            cacheMap.remove(key);
        }
        while (maxCacheSize < totalCacheStorage + contentSize) {
            String removedResponeKey = cacheList.removeLast();
            totalCacheStorage -= cacheMap.get(removedResponeKey).getContentLength();
            cacheMap.remove(removedResponeKey);
        }
        cacheList.addFirst(key);
        totalCacheStorage += contentSize;
        cacheMap.put(key, response);
    }
}

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.locks.ReentrantLock;

public class Cache {
    private final ReentrantLock lock = new ReentrantLock();
    private int maxCacheSize;
    private int maxObjectSize;
    private Map<String, Response> cacheMap = new HashMap<String, Response>();
    private LinkedList<String> cacheList = new LinkedList<String>();
    private int totalCacheStorage = 0;

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

    public Response getResponse(Request request) throws NoSuchElementException {
        String key = request.getURL();
        if (!cacheMap.containsKey(key)) {
            throw new NoSuchElementException("Key " + key + " not found");
        }
        cacheList.removeFirstOccurrence(key);
        cacheList.addFirst(key);
        return cacheMap.get(key);
    }

    public void addResponseToCache(Response response) {
        int contentSize = response.getContentLength();
        if (contentSize > maxObjectSize || response.getStatusCode() != 200)
            return;

        String key = response.getServerURL();
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

    public Cache(int maxCacheSizeInput, int maxObjectSizeInput) throws IllegalArgumentException {
        if (maxObjectSizeInput > maxCacheSizeInput) {
            throw new IllegalArgumentException("MaxObjectSize greater than MaxCacheSize");
        }
        maxCacheSize = maxCacheSizeInput;
        maxObjectSize = maxObjectSizeInput;
    }
}

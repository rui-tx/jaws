package org.ruitx.jaws.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a queued HTTP request that can be serialized and sent through the message queue.
 * Used by Loki to queue requests from the head instance to worker instances.
 */
public class QueuedRequest {
    
    private final String id;
    private final String method;
    private final String path;
    private final String body;
    private final Map<String, String> headers;
    private final Map<String, String> queryParams;
    private final long timestamp;
    
    @JsonCreator
    public QueuedRequest(
            @JsonProperty("id") String id,
            @JsonProperty("method") String method,
            @JsonProperty("path") String path,
            @JsonProperty("body") String body,
            @JsonProperty("headers") Map<String, String> headers,
            @JsonProperty("queryParams") Map<String, String> queryParams,
            @JsonProperty("timestamp") long timestamp) {
        this.id = id;
        this.method = method;
        this.path = path;
        this.body = body;
        this.headers = headers;
        this.queryParams = queryParams;
        this.timestamp = timestamp;
    }
    
    public String getId() {
        return id;
    }
    
    public String getMethod() {
        return method;
    }
    
    public String getPath() {
        return path;
    }
    
    public String getBody() {
        return body;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public Map<String, String> getQueryParams() {
        return queryParams;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    @Override
    public String toString() {
        return "QueuedRequest{" +
                "id='" + id + '\'' +
                ", method='" + method + '\'' +
                ", path='" + path + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
} 
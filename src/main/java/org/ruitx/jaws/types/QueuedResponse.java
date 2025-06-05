package org.ruitx.jaws.types;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * Represents a queued HTTP response that can be serialized and sent back through the message queue.
 * Used by Loki to send responses from worker instances back to the head instance.
 */
public class QueuedResponse {
    
    private final String requestId;
    private final int statusCode;
    private final String body;
    private final Map<String, String> headers;
    private final long timestamp;
    private final boolean success;
    private final String errorMessage;
    
    @JsonCreator
    public QueuedResponse(
            @JsonProperty("requestId") String requestId,
            @JsonProperty("statusCode") int statusCode,
            @JsonProperty("body") String body,
            @JsonProperty("headers") Map<String, String> headers,
            @JsonProperty("timestamp") long timestamp,
            @JsonProperty("success") boolean success,
            @JsonProperty("errorMessage") String errorMessage) {
        this.requestId = requestId;
        this.statusCode = statusCode;
        this.body = body;
        this.headers = headers;
        this.timestamp = timestamp;
        this.success = success;
        this.errorMessage = errorMessage;
    }
    
    // Success constructor
    public static QueuedResponse success(String requestId, int statusCode, String body, Map<String, String> headers) {
        return new QueuedResponse(requestId, statusCode, body, headers, System.currentTimeMillis(), true, null);
    }
    
    // Error constructor
    public static QueuedResponse error(String requestId, int statusCode, String errorMessage) {
        return new QueuedResponse(requestId, statusCode, null, null, System.currentTimeMillis(), false, errorMessage);
    }
    
    public String getRequestId() {
        return requestId;
    }
    
    public int getStatusCode() {
        return statusCode;
    }
    
    public String getBody() {
        return body;
    }
    
    public Map<String, String> getHeaders() {
        return headers;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public boolean isSuccess() {
        return success;
    }
    
    public String getErrorMessage() {
        return errorMessage;
    }
    
    @Override
    public String toString() {
        return "QueuedResponse{" +
                "requestId='" + requestId + '\'' +
                ", statusCode=" + statusCode +
                ", success=" + success +
                ", timestamp=" + timestamp +
                '}';
    }
} 
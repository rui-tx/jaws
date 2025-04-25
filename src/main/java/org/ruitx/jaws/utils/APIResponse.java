package org.ruitx.jaws.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

import org.ruitx.jaws.strings.ResponseCode;

/**
 * APIResponse is a class for representing API responses.
 * @param <T> the type of the response data.
 */
public record APIResponse<T>(
        @JsonProperty("success") boolean success,
        @JsonProperty("code") String code,
        @JsonProperty("timestamp") long timestamp,

        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        @JsonProperty("info") String info,
        
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonProperty("data") T data
) {
    /**
     * Creates a success response with data
     */
    public static <T> APIResponse<T> success(String code, T data) {
        return new APIResponse<>(true, code, Instant.now().getEpochSecond(), "", data);
    }

    public static <T> APIResponse<T> success(ResponseCode code, T data) {
        return new APIResponse<>(true, code.getCodeAndMessage(), Instant.now().getEpochSecond(), "", data);
    }

    /**
     * Creates a success response with data and info message
     */
    public static <T> APIResponse<T> success(String code, String info, T data) {
        return new APIResponse<>(true, code, Instant.now().getEpochSecond(), info, data);
    }

    public static <T> APIResponse<T> success(ResponseCode code, String info, T data) {
        return new APIResponse<>(true, code.getCodeAndMessage(), Instant.now().getEpochSecond(), info, data);
    }

    /**
     * Creates an error response
     */
    public static <T> APIResponse<T> error(String code, String message) {
        return new APIResponse<>(false, code, Instant.now().getEpochSecond(), message, null);
    }

    public static <T> APIResponse<T> error(ResponseCode code, String message) {
        return new APIResponse<>(false, code.getCodeAndMessage(), Instant.now().getEpochSecond(), message, null);
    }

    /**
     * Constructor with all fields
     */
    public APIResponse {
        // Validate required fields
        if (code == null || code.isEmpty()) {
            throw new IllegalArgumentException("Response code cannot be null or empty");
        }
    }
}

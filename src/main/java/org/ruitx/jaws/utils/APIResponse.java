package org.ruitx.jaws.utils;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * APIResponse is a class for representing API responses.
 *
 * @param <T>       the type of the response data.
 * @param success   a boolean indicating whether the API call was successful.
 * @param data      the response data, or null if the API call was not successful.
 * @param reason    a string indicating the reason for the API call failure, or null if the API call was successful.
 * @param timestamp the Unix timestamp when the response was generated.
 */
public record APIResponse<T>(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") T data,
        @JsonProperty("reason") String reason,
        @JsonProperty("timestamp") long timestamp
) {

    /**
     * APIResponse constructor with default Unix timestamp.
     *
     * @param success a boolean indicating whether the API call was successful.
     * @param data    the response data, or null if the API call was not successful.
     * @param reason  a string indicating the reason for the API call failure, or null if the API call was successful.
     */
    public APIResponse(boolean success, T data, String reason) {
        this(success, data, reason, Instant.now().getEpochSecond());
    }
}

package org.ruitx.jaws.utils;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * APIResponse is a class for representing API responses.
 *
 * @param <T>       the type of the response data.
 * @param success   a boolean indicating whether the API call was successful.
 * @param code      a string indicating the status code of the API call.
 * @param info      a string indicating additional information about the API call.
 * @param timestamp the Unix timestamp when the response was generated.
 * @param data      the response data, or null if the API call was not successful.
 */
public record APIResponse<T>(
        @JsonProperty("success") boolean success,
        @JsonProperty("code") String code,
        @JsonProperty("info") String info,
        @JsonProperty("timestamp") long timestamp,
        @JsonProperty("data") T data
) {

    /**
     * APIResponse constructor with default Unix timestamp.
     *
     * @param success a boolean indicating whether the API call was successful.
     * @param code    a string indicating the status code of the API call.
     * @param info    a string indicating additional information about the API call.
     * @param data    the response data, or null if the API call was not successful.
     */
    public APIResponse(boolean success,String code, String info, T data) {
        this(success, code, info, Instant.now().getEpochSecond(), data);
    }
}

package org.ruitx.server.utils;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * APIResponse is a class for representing API responses.
 *
 * @param <T>     the type of the response data.
 * @param success a boolean indicating whether the API call was successful.
 * @param data    the response data, or null if the API call was not successful.
 * @param reason  a string indicating the reason for the API call failure, or null if the API call was successful.
 */
public record APIResponse<T>(
        @JsonProperty("success") boolean success,
        @JsonProperty("data") T data,
        @JsonProperty("reason") String reason
) {
}

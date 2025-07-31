package org.ruitx.jaws.types;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.Instant;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.utils.TimestampDeserializer;

/**
 * APIResponse is a class for representing API responses.
 *
 * @param <T> the type of data contained in the response
 */
public record APIResponse<T>(
    @JsonProperty("success") boolean success,
    @JsonProperty("code") String code,

    @JsonDeserialize(using = TimestampDeserializer.class)
    @JsonProperty("timestamp") long timestamp,

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    @JsonProperty("info") String info,

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonProperty("data") T data
) {

  /**
   * Constructor with all fields
   *
   * @param success   indicates if the response is successful
   * @param code      the response code
   * @param timestamp the timestamp of the response
   * @param info      additional information about the response
   * @param data      the response data
   */
  public APIResponse {
    // Validate required fields
    if (code == null || code.isEmpty()) {
      throw new IllegalArgumentException("Response code cannot be null or empty");
    }
  }

  /**
   * Creates a success response with data
   *
   * @param code the response code
   * @param data the response data
   * @param <T>  the type of the response data
   * @return a new APIResponse instance indicating success with the provided code and data and info
   * message as an empty string
   */
  public static <T> APIResponse<T> success(String code, T data) {
    return new APIResponse<>(true, code, Instant.now().getEpochSecond(), "", data);
  }

  /**
   * Creates a success response with data and default response code
   *
   * @param code the default response code
   * @param data the response data
   * @param <T>  the type of the response data
   * @return a new APIResponse instance indicating success with default code and provided data and
   * info message as an empty string
   */
  public static <T> APIResponse<T> success(ResponseCode code, T data) {
    return new APIResponse<>(true, code.getCodeAndMessage(),
        Instant.now().getEpochSecond(), "", data);
  }

  /**
   * Creates a success response with data and info message
   *
   * @param code the response code
   * @param info the info message
   * @param data the response data
   * @param <T>  the type of the response data
   * @return a new APIResponse instance indicating success with the provided code, info, and data
   */
  public static <T> APIResponse<T> success(String code, String info, T data) {
    return new APIResponse<>(true, code, Instant.now().getEpochSecond(), info, data);
  }

  /**
   * Creates a success response with data, info message, and default response code
   *
   * @param code the default response code
   * @param info the info message
   * @param data the response data
   * @param <T>  the type of the response data
   * @return a new APIResponse instance indicating success with default code, provided info, and
   * data
   */
  public static <T> APIResponse<T> success(ResponseCode code, String info, T data) {
    return new APIResponse<>(true, code.getCodeAndMessage(),
        Instant.now().getEpochSecond(), info, data);
  }

  /**
   * Creates an error response
   *
   * @param code    the error code
   * @param message the error message
   * @param <T>     the type of the response data
   * @return a new APIResponse instance indicating failure with the provided code and message
   */
  public static <T> APIResponse<T> error(String code, String message) {
    return new APIResponse<>(false, code, Instant.now().getEpochSecond(), message,
        null);
  }

  /**
   * Creates an error response with a default response code
   *
   * @param code    the default error code
   * @param message the error message
   * @param <T>     the type of the response data
   * @return a new APIResponse instance indicating failure with the default code and provided
   * message
   */
  public static <T> APIResponse<T> error(ResponseCode code, String message) {
    return new APIResponse<>(false, code.getCodeAndMessage(),
        Instant.now().getEpochSecond(), message, null);
  }
}

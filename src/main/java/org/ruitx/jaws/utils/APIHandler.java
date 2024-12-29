package org.ruitx.jaws.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * APIHandler is a utility class for making API calls and parsing the responses.
 */
public class APIHandler {
    private static final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Get the ObjectMapper instance.
     *
     * @return the ObjectMapper instance.
     */
    public static ObjectMapper getMapper() {
        return objectMapper;
    }

    /**
     * Encode an object to JSON.
     *
     * @param obj the object to encode.
     * @return the encoded JSON string.
     * @throws JsonProcessingException if an error occurs while encoding the object.
     */
    public static String encode(Object obj) throws JsonProcessingException {
        return APIHandler.getMapper().writeValueAsString(obj);
    }

    /**
     * Call an API endpoint and parse the response.
     *
     * @param endpoint     the API endpoint to call.
     * @param responseType the Java type of the response.
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, JavaType responseType) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                Logger.error("API request failed with status code: {}", response.statusCode());
                return new APIResponse<>(false, null, "Server returned error status: " + response.statusCode());
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.contains("application/json")) {
                Logger.error("Unexpected content type: {}", contentType);
                Logger.error("Response body: {}", response.body());
                return new APIResponse<>(false, null, "Server returned non-JSON response");
            }

            return parseResponse(response.body(), responseType);
        } catch (IOException | InterruptedException e) {
            Logger.error("HTTP request failed: {}", e.getMessage());
            return new APIResponse<>(false, null, "Failed to fetch data from jaws");
        }
    }

    /**
     * Parse the response from the API call.
     *
     * @param response     the response from the API call.
     * @param responseType the Java type of the response.
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    private <T> APIResponse<T> parseResponse(String response, JavaType responseType) {
        try {
            JavaType fullType = objectMapper.getTypeFactory()
                    .constructParametricType(APIResponse.class, responseType);
            return objectMapper.readValue(response, fullType);
        } catch (JsonParseException e) {
            Logger.error("Failed to parse JSON (invalid format): {}", e.getMessage());
            return new APIResponse<>(false, null, "Invalid JSON response from jaws");
        } catch (IOException e) {
            Logger.error("Failed to parse JSON response: {}", e.getMessage());
            return new APIResponse<>(false, null, "Failed to process jaws response");
        }
    }
}
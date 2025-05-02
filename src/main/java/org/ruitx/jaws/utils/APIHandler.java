package org.ruitx.jaws.utils;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.ruitx.jaws.exceptions.APIParsingException;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashMap;
import java.util.Map;

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
     */
    public static String encode(Object obj) {
        try {
            return APIHandler.getMapper().writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new APIParsingException("Failed to encode object to JSON", e);
        }
    }

    /**
     * Call an API endpoint and parse the response.
     *
     * @param endpoint     the API endpoint to call.
     * @param method       the HTTP method (GET, POST, PUT, etc.)
     * @param body         the request body for POST/PUT requests (can be null for GET)
     * @param responseType the Java type of the response.
     * @param headers      the HTTP headers to include in the request (can be null)
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, String body, JavaType responseType) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(URI.create(endpoint)).header("accept", "*/*");

        Map<String, String> allHeaders = headers != null ? new HashMap<>(headers) : new HashMap<>();
        for (Map.Entry<String, String> header : allHeaders.entrySet()) {
            requestBuilder.header(header.getKey(), header.getValue());
        }

        if (method == RequestType.POST) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""))
                    .header("Content-Type", "application/json");
        } else {
            requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                Logger.error("API request failed with status code: {}", response.statusCode());
                return APIResponse.error(
                        response.statusCode() + "",
                        "Server returned error status: " + response.statusCode()
                );
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.contains("application/json")) {
                Logger.error("Unexpected content type: {}", contentType);
                Logger.error("Response body: {}", response.body());
                return APIResponse.error(
                        response.statusCode() + "",
                        "Server returned non-JSON response"
                );
            }
            return parseResponse(response.body(), responseType);

        } catch (IOException | InterruptedException e) {
            Logger.error("HTTP request failed: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to fetch data from JAWS"
            );
        }
    }

    /**
     * Call an API endpoint and parse the response using a Class type.
     *
     * @param endpoint      the API endpoint to call.
     * @param method        the HTTP method (GET, POST, PUT, etc.)
     * @param body          the request body for POST/PUT requests (can be null for GET)
     * @param responseClass the Class of the response.
     * @param headers       the HTTP headers to include in the request (can be null)
     * @param <T>           the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, String body, Class<T> responseClass) {
        JavaType responseType = objectMapper.getTypeFactory().constructType(responseClass);
        return callAPI(endpoint, method, headers, body, responseType);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint      the API endpoint to call.
     * @param method        the HTTP method (GET, POST, PUT, etc.)
     * @param body          the request body object for POST/PUT requests
     * @param responseClass the Class of the response.
     * @param <T>           the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Object body, Class<T> responseClass) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, null, jsonBody, responseClass);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint     the API endpoint to call.
     * @param method       the HTTP method (GET, POST, PUT, etc.)
     * @param body         the request body object for POST/PUT requests
     * @param responseType the Java type of the response.
     * @param <T>          the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Object body, JavaType responseType) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, null, jsonBody, responseType);
    }

    /**
     * Call an API endpoint with an object body and parse the response.
     *
     * @param endpoint      the API endpoint to call.
     * @param method        the HTTP method (GET, POST, PUT, etc.)
     * @param headers       the HTTP headers to include in the request
     * @param body          the request body object for POST/PUT requests
     * @param responseClass the Class of the response.
     * @param <T>           the type of the response.
     * @return the parsed API response.
     */
    public <T> APIResponse<T> callAPI(String endpoint, RequestType method, Map<String, String> headers, Object body, Class<T> responseClass) {
        String jsonBody = body != null ? encode(body) : null;
        return callAPI(endpoint, method, headers, jsonBody, responseClass);
    }

    public <T> APIResponse<T> callAPI(String endpoint, JavaType type) {
        return callAPI(endpoint, RequestType.GET, null, null, type);
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
            try {
                T directValue = objectMapper.readValue(response, responseType);
                return APIResponse.success(ResponseCode.OK.getCodeAndMessage(), directValue);
            } catch (IOException e) {
                // If direct parsing fails, try to parse as APIResponse
                JavaType fullType = objectMapper.getTypeFactory()
                        .constructParametricType(APIResponse.class, responseType);
                return objectMapper.readValue(response, fullType);
            }
        } catch (JsonParseException e) {
            Logger.error("Failed to parse JSON (invalid format): {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Invalid JSON response from JAWS"
            );
        } catch (IOException e) {
            Logger.error("Failed to parse JSON response: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to process JAWS response"
            );
        }
    }
}

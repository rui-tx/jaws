package org.ruitx.server.utils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class APIHandler {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    /**
     * Calls the given API endpoint and returns the response parsed into a desired type.
     * This method can handle any generic type, specified at the point of the API call.
     *
     * @param endpoint      The API URL to make the GET request to.
     * @param typeReference The TypeReference representing the expected response type (e.g., List of Strings).
     * @param <T>           The type of the response object.
     * @return The deserialized response body in the form of type T.
     * @throws IOException If there is an error with the HTTP request or JSON parsing.
     */
    public <T> T callAPI(String endpoint, TypeReference<T> typeReference) throws IOException {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .GET()
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            return parseResponse(response.body(), typeReference);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Parses the raw JSON response string into the specified type using Jackson's ObjectMapper.
     *
     * @param response      The raw JSON response body as a String.
     * @param typeReference The TypeReference representing the target type for deserialization.
     * @param <T>           The target type of the deserialized object.
     * @return The deserialized object of type T.
     * @throws RuntimeException If there is an error during JSON parsing.
     */
    private <T> T parseResponse(String response, TypeReference<T> typeReference) {
        try {
            return objectMapper.readValue(response, typeReference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}

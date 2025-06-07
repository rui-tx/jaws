package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.jobs.BaseJob;
import org.ruitx.jaws.jobs.JobResultStore;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.api.Post;
import org.tinylog.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.jaws.types.TypeDefinition.LIST_POST;

/**
 * ExternalApiJob - Replaces fetchExternalDataAsync route method
 * 
 * This job fetches data from external APIs without depending on the web framework.
 * It implements its own HTTP client instead of depending on Bragi.
 * 
 * Migration from:
 * @Route(endpoint = API_ENDPOINT + "fetch-external-data", method = POST)
 * @Async(timeout = 30000, priority = 5, maxRetries = 2)
 * public void fetchExternalDataAsync()
 */
public class ExternalApiJob extends BaseJob {
    
    public static final String JOB_TYPE = "external-api-call";
    
    /**
     * Constructor - jobs must have a constructor that takes Map<String, Object>
     */
    public ExternalApiJob(Map<String, Object> payload) {
        super(JOB_TYPE, 5, 2, 30000L, payload); // priority 5, 2 retries, 30s timeout
    }
    
    @Override
    public void execute() throws Exception {
        Logger.info("Starting external API job: {}", getId());
        
        try {
            // Get URL from payload, default to the original URL
            String url = getString("url");
            if (url == null) {
                url = "https://jsonplaceholder.typicode.com/posts";
            }
            
            // Simulate network delay (from original method)
            long networkDelay = getLong("networkDelayMs") != null ? getLong("networkDelayMs") : 2000L;
            Logger.info("Simulating network delay of {} ms...", networkDelay);
            Thread.sleep(networkDelay);
            
            // Make the API call using our own HTTP client (no framework dependency!)
            APIResponse<List<Post>> response = callExternalAPI(url);

            if (!response.success()) {
                // Store error result
                String errorMessage = "Failed to fetch external data: " + response.info();
                JobResultStore.storeError(getId(), getStatusCode(response.code()), errorMessage);
                return;
            }

            // Create successful result
            Map<String, Object> result = new HashMap<>();
            result.put("dataFetched", true);
            result.put("itemCount", response.data().size());
            result.put("data", response.data());
            result.put("source", url);
            result.put("timestamp", Instant.now().toEpochMilli());
            result.put("jobId", getId());
            result.put("jobType", getType());
            result.put("networkDelay", networkDelay + " ms");
            
            // Additional metadata from payload
            if (getString("requestedBy") != null) {
                result.put("requestedBy", getString("requestedBy"));
            }
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("External API job completed successfully: {}", getId());
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Logger.warn("External API job was interrupted: {}", getId());
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "Data fetch was interrupted");
            throw e;
        } catch (Exception e) {
            Logger.error("External API job failed: {}", e.getMessage(), e);
            
            // Store error result
            JobResultStore.storeError(getId(), 500, "API call failed: " + e.getMessage());
            throw e;
        }
    }
    
    /**
     * Framework-agnostic API call method
     */
    private APIResponse<List<Post>> callExternalAPI(String url) {
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("accept", "application/json")
                .GET()
                .build();
        
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200 && response.statusCode() != 201) {
                Logger.error("API request failed with status code: {}", response.statusCode());
                return APIResponse.error(
                        String.valueOf(response.statusCode()),
                        "Server returned error status: " + response.statusCode()
                );
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.contains("application/json")) {
                Logger.error("Unexpected content type: {}", contentType);
                return APIResponse.error(
                        String.valueOf(response.statusCode()),
                        "Server returned non-JSON response"
                );
            }
            
            // Parse the response
            List<Post> posts = Odin.getMapper().readValue(response.body(), LIST_POST);
            return APIResponse.success(String.valueOf(response.statusCode()), "Data fetched successfully", posts);

        } catch (IOException | InterruptedException e) {
            Logger.error("HTTP request failed: {}", e.getMessage());
            return APIResponse.error(
                    ResponseCode.INTERNAL_SERVER_ERROR.getCodeAndMessage(),
                    "Failed to fetch data from API"
            );
        }
    }
    
    /**
     * Extract status code from response code string
     */
    private int getStatusCode(String responseCode) {
        try {
            // Try to parse as integer first
            return Integer.parseInt(responseCode);
        } catch (NumberFormatException e) {
            // If it's a compound code like "500 - Internal Server Error", extract the number
            if (responseCode.contains(" - ")) {
                try {
                    return Integer.parseInt(responseCode.split(" - ")[0]);
                } catch (NumberFormatException ex) {
                    // Default to 500 if we can't parse
                    return 500;
                }
            }
            // Default to 500 for unknown formats
            return 500;
        }
    }
} 
package org.ruitx.www.jobs;

import org.ruitx.jaws.components.Odin;
import org.ruitx.jaws.components.freyr.BaseJob;
import org.ruitx.jaws.components.freyr.CircuitBreaker;
import org.ruitx.jaws.components.freyr.JobResultStore;
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
 * Now includes circuit breaker protection to prevent cascading failures when
 * external APIs are down or experiencing issues.
 * 
 * CIRCUIT BREAKER INTEGRATION:
 * - Each unique API endpoint gets its own circuit breaker instance
 * - Circuit breaker states: CLOSED (normal), OPEN (failing fast), HALF_OPEN (testing recovery)
 * - Failure tracking: HTTP errors, timeouts, and non-JSON responses trigger circuit breaker
 * - Protection: When circuit is open, jobs fail fast with 503 status without making API calls
 * - Recovery: Circuit automatically transitions to HALF_OPEN after timeout, then CLOSED if calls succeed
 * - Monitoring: Circuit breaker state and statistics are included in job results
 * 
 * USAGE EXAMPLES:
 * 
 * 1. Normal API call (circuit breaker CLOSED):
 *    JobQueue.getInstance().submit(new ExternalApiJob(Map.of(
 *        "url", "https://api.example.com/data"
 *    )));
 * 
 * 2. When API is failing (circuit breaker OPEN):
 *    - Multiple failures will open the circuit
 *    - Subsequent jobs fail fast with CircuitBreakerOpenException
 *    - No actual API calls are made until circuit recovers
 * 
 * 3. Monitoring circuit breaker state:
 *    - Check job results for "circuitBreaker" field containing current state
 *    - Use admin endpoints: GET /admin/api/circuit-breakers
 *    - Circuit breaker name format: "external-api-{domain}"
 * 
 * Migration from:
 * @Route(endpoint = API_ENDPOINT + "fetch-external-data", method = POST)
 * @Async(timeout = 30000, priority = 5, maxRetries = 2)
 * public void fetchExternalDataAsync()
 */
public class ExternalApiJob extends BaseJob {
    
    public static final String JOB_TYPE = "external-api-call";
    
    private final CircuitBreaker circuitBreaker;
    
    /**
     * Constructor - jobs must have a constructor that takes Map<String, Object>
     */
    public ExternalApiJob(Map<String, Object> payload) {
        super(JOB_TYPE, 5, 2, 30000L, payload); // priority 5, 2 retries, 30s timeout
        
        // Initialize circuit breaker for external API protection
        // Each unique API endpoint gets its own circuit breaker instance
        String url = payload.get("url") != null ? payload.get("url").toString() : "jsonplaceholder";
        String serviceName = extractServiceName(url);
        this.circuitBreaker = CircuitBreaker.forExternalAPI("external-api-" + serviceName);
        
        Logger.debug("ExternalApiJob created with circuit breaker for service: {}", serviceName);
    }

    @Override
    public void execute() throws Exception {
        Logger.info("Starting external API job: {} with circuit breaker protection", getId());
        
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
            
            // Make the API call with circuit breaker protection
            APIResponse<List<Post>> response = callExternalAPIWithCircuitBreaker(url);

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
            
            // Add circuit breaker state to result for monitoring
            CircuitBreaker.CircuitBreakerStats stats = circuitBreaker.getStatistics();
            result.put("circuitBreaker", Map.of(
                "serviceName", stats.serviceName,
                "state", stats.state.name(),
                "successfulCalls", stats.successfulCalls,
                "failedCalls", stats.failedCalls,
                "lastStateChange", stats.lastStateChangeTime
            ));
            
            // Additional metadata from payload
            if (getString("requestedBy") != null) {
                result.put("requestedBy", getString("requestedBy"));
            }
            
            // Store the result
            String jsonResult = Odin.getMapper().writeValueAsString(result);
            JobResultStore.storeSuccess(getId(), jsonResult);
            
            Logger.info("External API job completed successfully: {} (circuit breaker state: {})", 
                       getId(), stats.state.name());
            
        } catch (CircuitBreaker.CircuitBreakerOpenException e) {
            // Circuit breaker is open - fail fast without making the API call
            Logger.warn("External API job failed due to open circuit breaker: {} - {}", getId(), e.getMessage());
            
            // Store circuit breaker error result
            JobResultStore.storeError(getId(), 503, 
                "Service temporarily unavailable due to circuit breaker protection: " + e.getMessage());
            throw e;
            
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
     * API call method with circuit breaker protection
     */
    private APIResponse<List<Post>> callExternalAPIWithCircuitBreaker(String url) throws Exception {
        return circuitBreaker.execute(() -> {
            Logger.debug("Making API call through circuit breaker to: {}", url);
            return callExternalAPI(url);
        });
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
                
                // Throw exception for circuit breaker to track failures
                throw new IOException("API returned error status: " + response.statusCode());
            }

            String contentType = response.headers().firstValue("content-type").orElse("");
            if (!contentType.contains("application/json")) {
                Logger.error("Unexpected content type: {}", contentType);
                
                // Throw exception for circuit breaker to track failures
                throw new IOException("Server returned non-JSON response: " + contentType);
            }
            
            // Parse the response
            List<Post> posts = Odin.getMapper().readValue(response.body(), LIST_POST);
            Logger.debug("Successfully parsed {} posts from API response", posts.size());
            
            return APIResponse.success(String.valueOf(response.statusCode()), "Data fetched successfully", posts);

        } catch (IOException | InterruptedException e) {
            Logger.error("HTTP request failed: {}", e.getMessage());
            // Re-throw so circuit breaker can track the failure
            throw new RuntimeException("Failed to fetch data from API: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract service name from URL for circuit breaker identification
     */
    private String extractServiceName(String url) {
        if (url == null || url.isEmpty()) {
            return "unknown";
        }
        
        try {
            // Extract domain from URL for service identification
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host != null) {
                // Remove www. prefix and use domain as service name
                return host.startsWith("www.") ? host.substring(4) : host;
            }
        } catch (Exception e) {
            Logger.debug("Failed to parse URL for service name: {}", url);
        }
        
        // Fallback to a hash of the URL
        return "service-" + Math.abs(url.hashCode() % 10000);
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
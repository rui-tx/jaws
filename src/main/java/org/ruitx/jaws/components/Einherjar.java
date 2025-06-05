package org.ruitx.jaws.components;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.QueuedRequest;
import org.ruitx.jaws.types.QueuedResponse;
import org.tinylog.Logger;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Einherjar - The Chosen Workers
 * 
 * Einherjar manages worker threads that process queued requests. Named after the
 * warriors chosen by Odin to fight in Ragnarök - fitting for components that
 * battle through the workload to process requests.
 * 
 * Features:
 * - Multi-threaded request processing
 * - Reuses existing JAWS routing and controller logic
 * - Graceful shutdown handling
 * - Worker health monitoring
 */
public class Einherjar {
    
    private static Einherjar instance;
    
    private final ExecutorService workerPool;
    private final AtomicBoolean running;
    private final AtomicInteger activeWorkers;
    private final Loki loki;
    private final Njord njord;
    private final int poolSize;
    
    private Einherjar() {
        this.poolSize = ApplicationConfig.WORKER_POOL_SIZE;
        this.workerPool = Executors.newFixedThreadPool(poolSize);
        this.running = new AtomicBoolean(false);
        this.activeWorkers = new AtomicInteger(0);
        this.loki = Loki.getInstance();
        this.njord = Njord.getInstance();
        
        Logger.info("Einherjar assembled! Worker pool size: {}", poolSize);
    }
    
    public static synchronized Einherjar getInstance() {
        if (instance == null) {
            instance = new Einherjar();
        }
        return instance;
    }
    
    /**
     * Starts the worker threads to process requests from the queue.
     */
    public void start() {
        if (running.compareAndSet(false, true)) {
            Logger.info("Einherjar march to battle! Starting {} workers", poolSize);
            
            for (int i = 0; i < poolSize; i++) {
                final int workerId = i + 1;
                workerPool.execute(() -> workerLoop(workerId));
            }
        }
    }
    
    /**
     * Stops all worker threads gracefully.
     */
    public void stop() {
        if (running.compareAndSet(true, false)) {
            Logger.info("Einherjar retreat! Stopping workers...");
            workerPool.shutdown();
            
            // Wait for workers to finish current tasks
            try {
                if (!workerPool.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)) {
                    Logger.warn("Workers did not stop gracefully, forcing shutdown");
                    workerPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                Logger.warn("Interrupted while waiting for workers to stop");
                workerPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
            
            Logger.info("Einherjar have fallen back to Valhalla");
        }
    }
    
    /**
     * Alias for stop() method to match shutdown naming convention.
     */
    public void shutdown() {
        stop();
    }
    
    /**
     * Main worker loop - polls for requests and processes them.
     */
    private void workerLoop(int workerId) {
        Logger.info("Worker {} enters the battlefield", workerId);
        activeWorkers.incrementAndGet();
        
        try {
            while (running.get()) {
                try {
                    // Poll for a request (1 second timeout)
                    QueuedRequest request = loki.pollRequest(1000);
                    
                    if (request != null) {
                        Logger.debug("Worker {} processing request: {}", workerId, request);
                        processRequest(workerId, request);
                    }
                    
                } catch (Exception e) {
                    Logger.error("Worker {} encountered error: {}", workerId, e.getMessage(), e);
                    // Continue working despite errors
                }
            }
        } finally {
            activeWorkers.decrementAndGet();
            Logger.info("Worker {} leaves the battlefield", workerId);
        }
    }
    
    /**
     * Processes a single queued request using existing JAWS routing logic.
     */
    private void processRequest(int workerId, QueuedRequest request) {
        long startTime = System.currentTimeMillis();
        
        try {
            // Find the route using Njord
            RequestType requestType = RequestType.valueOf(request.getMethod());
            Method routeMethod = njord.getRoute(request.getPath(), requestType);
            
            if (routeMethod == null) {
                // No route found
                Logger.warn("Worker {} - No route found for {} {}", workerId, request.getMethod(), request.getPath());
                QueuedResponse response = QueuedResponse.error(
                    request.getId(), 
                    404, 
                    "No route found for " + request.getMethod() + " " + request.getPath()
                );
                loki.sendResponse(response);
                return;
            }
            
            // Get the controller instance
            Object controller = njord.getControllerInstance(routeMethod.getDeclaringClass().getSimpleName());
            if (controller == null) {
                Logger.error("Worker {} - No controller found for route", workerId);
                QueuedResponse response = QueuedResponse.error(
                    request.getId(), 
                    500, 
                    "No controller found for route"
                );
                loki.sendResponse(response);
                return;
            }
            
            // Create a worker response context to capture the response
            WorkerResponseContext responseContext = new WorkerResponseContext();
            
            // Set context if controller extends Bragi
            if (controller instanceof Bragi bragi) {
                // Create a simple proxy that can capture response calls
                WorkerBragiProxy proxy = new WorkerBragiProxy(request, responseContext);
                
                // Set the proxy in Bragi's ThreadLocal using reflection
                try {
                    java.lang.reflect.Field requestContextField = Bragi.class.getDeclaredField("requestContext");
                    requestContextField.setAccessible(true);
                    ThreadLocal<Yggdrasill.RequestContext> requestContextThreadLocal = 
                        (ThreadLocal<Yggdrasill.RequestContext>) requestContextField.get(null);
                    requestContextThreadLocal.set(proxy);
                    
                    Logger.debug("Worker {} - Set WorkerBragiProxy for Bragi controller", workerId);
                } catch (Exception e) {
                    Logger.error("Worker {} - Failed to set RequestContext: {}", workerId, e.getMessage());
                    // Continue without context - will likely fail but we'll handle it
                }
            }
            
            try {
                // Execute the controller method directly
                Object result = routeMethod.invoke(controller);
                
                // Get the response from the context
                QueuedResponse response;
                if (responseContext.hasResponse()) {
                    response = QueuedResponse.success(
                        request.getId(),
                        responseContext.getStatusCode(),
                        responseContext.getResponseBody(),
                        responseContext.getHeaders()
                    );
                } else {
                    // Fallback for non-Bragi controllers or methods that return values directly
                    String responseBody = result != null ? result.toString() : "Request processed successfully";
                    response = QueuedResponse.success(
                        request.getId(),
                        200,
                        responseBody,
                        new HashMap<>()
                    );
                }
                
                long duration = System.currentTimeMillis() - startTime;
                Logger.debug("Worker {} completed request {} in {}ms", workerId, request.getId(), duration);
                
                loki.sendResponse(response);
                
            } finally {
                // Clean up the request context
                if (controller instanceof Bragi bragi) {
                    bragi.cleanup();
                    
                    // Clear the ThreadLocal
                    try {
                        java.lang.reflect.Field requestContextField = Bragi.class.getDeclaredField("requestContext");
                        requestContextField.setAccessible(true);
                        ThreadLocal<Yggdrasill.RequestContext> requestContextThreadLocal = 
                            (ThreadLocal<Yggdrasill.RequestContext>) requestContextField.get(null);
                        requestContextThreadLocal.remove();
                    } catch (Exception e) {
                        Logger.warn("Worker {} - Failed to clear RequestContext: {}", workerId, e.getMessage());
                    }
                }
            }
            
        } catch (Exception e) {
            Logger.error("Worker {} failed to process request {}: {}", workerId, request.getId(), e.getMessage(), e);
            
            QueuedResponse response = QueuedResponse.error(
                request.getId(),
                500,
                "Internal server error: " + e.getMessage()
            );
            loki.sendResponse(response);
        }
    }
    
    /**
     * Gets worker statistics for monitoring.
     */
    public WorkerStats getStats() {
        return new WorkerStats(
            poolSize,
            activeWorkers.get(),
            running.get()
        );
    }
    
    /**
     * Worker statistics for monitoring.
     */
    public static class WorkerStats {
        private final int totalWorkers;
        private final int activeWorkers;
        private final boolean running;
        
        public WorkerStats(int totalWorkers, int activeWorkers, boolean running) {
            this.totalWorkers = totalWorkers;
            this.activeWorkers = activeWorkers;
            this.running = running;
        }
        
        public int getTotalWorkers() { return totalWorkers; }
        public int getActiveWorkers() { return activeWorkers; }
        public boolean isRunning() { return running; }
        
        @Override
        public String toString() {
            return String.format("WorkerStats{running=%s, active=%d/%d}", 
                running, activeWorkers, totalWorkers);
        }
    }
    
    /**
     * Context for capturing responses from worker processing.
     */
    private static class WorkerResponseContext {
        private boolean hasResponse = false;
        private int statusCode = 200;
        private String responseBody = "";
        private Map<String, String> headers = new HashMap<>();
        
        public void setResponse(int statusCode, String responseBody, Map<String, String> headers) {
            this.hasResponse = true;
            this.statusCode = statusCode;
            this.responseBody = responseBody;
            this.headers = headers != null ? headers : new HashMap<>();
        }
        
        public void addHeader(String name, String value) {
            this.headers.put(name, value);
        }
        
        public boolean hasResponse() { return hasResponse; }
        public int getStatusCode() { return statusCode; }
        public String getResponseBody() { return responseBody; }
        public Map<String, String> getHeaders() { return headers; }
    }
    
    /**
     * Worker-specific RequestContext that safely handles the lack of real HTTP request/response objects.
     * This avoids the null pointer exceptions that would occur in the parent constructor.
     */
    private static class WorkerBragiProxy extends Yggdrasill.RequestContext {
        private final QueuedRequest queuedRequest;
        private final WorkerResponseContext responseContext;
        private final Map<String, String> headers = new HashMap<>();
        private final Map<String, String> queryParams = new HashMap<>();
        private final String requestBody;
        
        public WorkerBragiProxy(QueuedRequest request, WorkerResponseContext responseContext) {
            // Call parent constructor with dummy/empty values to avoid issues
            super(new DummyHttpServletRequest(request), null, "");
            this.queuedRequest = request;
            this.responseContext = responseContext;
            this.requestBody = request.getBody() != null ? request.getBody() : "";
            
            // Populate our own headers and query params from the queued request
            if (request.getHeaders() != null) {
                this.headers.putAll(request.getHeaders());
            }
            if (request.getQueryParams() != null) {
                this.queryParams.putAll(request.getQueryParams());
            }
        }
        
        @Override
        public String getRequestBody() {
            return requestBody;
        }
        
        @Override
        public Map<String, String> getHeaders() {
            return headers;
        }
        
        @Override
        public Map<String, String> getQueryParams() {
            return queryParams;
        }
        
        @Override
        public Map<String, String> getPathParams() {
            return new HashMap<>();
        }
        
        @Override
        public Map<String, String> getBodyParams() {
            return new HashMap<>();
        }
        
        @Override
        public boolean isHTMX() {
            return false;
        }
        
        @Override
        public String getClientIpAddress() {
            return "worker";
        }
        
        @Override
        public String getCurrentToken() {
            return null;
        }
        
        @Override
        public void sendJSONResponse(org.ruitx.jaws.strings.ResponseCode code, String jsonResponse) {
            responseContext.setResponse(code.getCode(), jsonResponse, new HashMap<>());
        }
        
        @Override
        public void sendHTMLResponse(org.ruitx.jaws.strings.ResponseCode code, String htmlResponse) {
            responseContext.setResponse(code.getCode(), htmlResponse, new HashMap<>());
        }
        
        @Override
        public void addCustomHeader(String name, String value) {
            responseContext.addHeader(name, value);
        }
        
        @Override
        public String getHeader(String name) {
            return headers.get(name);
        }
        
        @Override
        public boolean isConnectionClosed() {
            return false; // Always false in worker context
        }
        
        @Override
        public HttpServletRequest getRequest() {
            throw new UnsupportedOperationException("HttpServletRequest not available in worker context");
        }
        
        @Override
        public HttpServletResponse getResponse() {
            throw new UnsupportedOperationException("HttpServletResponse not available in worker context");
        }
    }
    
    /**
     * Dummy HTTP request implementation to prevent null pointer exceptions in RequestContext constructor.
     */
    private static class DummyHttpServletRequest implements HttpServletRequest {
        private final QueuedRequest queuedRequest;
        
        public DummyHttpServletRequest(QueuedRequest request) {
            this.queuedRequest = request;
        }
        
        @Override
        public String getMethod() {
            return queuedRequest.getMethod();
        }
        
        @Override
        public String getRequestURI() {
            return queuedRequest.getPath();
        }
        
        @Override
        public String getQueryString() {
            return null; // We handle query params separately
        }
        
        @Override
        public String getHeader(String name) {
            return queuedRequest.getHeaders() != null ? queuedRequest.getHeaders().get(name) : null;
        }
        
        @Override
        public int getIntHeader(String name) {
            String header = getHeader(name);
            return header != null ? Integer.parseInt(header) : -1;
        }
        
        @Override
        public long getDateHeader(String name) {
            String header = getHeader(name);
            return header != null ? Long.parseLong(header) : -1;
        }
        
        @Override
        public Enumeration<String> getHeaders(String name) {
            String header = getHeader(name);
            List<String> headers = header != null ? List.of(header) : List.of();
            return Collections.enumeration(headers);
        }
        
        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> headerNames = queuedRequest.getHeaders() != null ? 
                queuedRequest.getHeaders().keySet() : new HashSet<>();
            return Collections.enumeration(headerNames);
        }
        
        @Override
        public Map<String, String[]> getParameterMap() {
            Map<String, String[]> paramMap = new HashMap<>();
            if (queuedRequest.getQueryParams() != null) {
                queuedRequest.getQueryParams().forEach((key, value) -> 
                    paramMap.put(key, new String[]{value}));
            }
            return paramMap;
        }
        
        @Override
        public String getContentType() {
            return getHeader("Content-Type");
        }
        
        @Override
        public jakarta.servlet.http.Cookie[] getCookies() {
            return null; // Not needed for our use case
        }
        
        @Override
        public String getRemoteAddr() {
            return "worker";
        }
        
        @Override
        public java.io.BufferedReader getReader() throws java.io.IOException {
            String body = queuedRequest.getBody() != null ? queuedRequest.getBody() : "";
            return new java.io.BufferedReader(new java.io.StringReader(body));
        }
        
        @Override
        public boolean isRequestedSessionIdFromUrl() {
            return false;
        }
        
        @Override
        public String getRealPath(String path) {
            return null;
        }
        
        // All other methods throw UnsupportedOperationException
        @Override public String getAuthType() { throw new UnsupportedOperationException(); }
        @Override public String getPathInfo() { throw new UnsupportedOperationException(); }
        @Override public String getPathTranslated() { throw new UnsupportedOperationException(); }
        @Override public String getContextPath() { throw new UnsupportedOperationException(); }
        @Override public String getRemoteUser() { throw new UnsupportedOperationException(); }
        @Override public boolean isUserInRole(String role) { throw new UnsupportedOperationException(); }
        @Override public java.security.Principal getUserPrincipal() { throw new UnsupportedOperationException(); }
        @Override public String getRequestedSessionId() { throw new UnsupportedOperationException(); }
        @Override public StringBuffer getRequestURL() { throw new UnsupportedOperationException(); }
        @Override public String getServletPath() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.http.HttpSession getSession(boolean create) { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.http.HttpSession getSession() { throw new UnsupportedOperationException(); }
        @Override public String changeSessionId() { throw new UnsupportedOperationException(); }
        @Override public boolean isRequestedSessionIdValid() { throw new UnsupportedOperationException(); }
        @Override public boolean isRequestedSessionIdFromCookie() { throw new UnsupportedOperationException(); }
        @Override public boolean isRequestedSessionIdFromURL() { throw new UnsupportedOperationException(); }
        @Override public boolean authenticate(jakarta.servlet.http.HttpServletResponse response) { throw new UnsupportedOperationException(); }
        @Override public void login(String username, String password) { throw new UnsupportedOperationException(); }
        @Override public void logout() { throw new UnsupportedOperationException(); }
        @Override public java.util.Collection<jakarta.servlet.http.Part> getParts() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.http.Part getPart(String name) { throw new UnsupportedOperationException(); }
        @Override public <T extends jakarta.servlet.http.HttpUpgradeHandler> T upgrade(Class<T> handlerClass) { throw new UnsupportedOperationException(); }
        @Override public Object getAttribute(String name) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> getAttributeNames() { throw new UnsupportedOperationException(); }
        @Override public String getCharacterEncoding() { throw new UnsupportedOperationException(); }
        @Override public void setCharacterEncoding(String env) { throw new UnsupportedOperationException(); }
        @Override public int getContentLength() { throw new UnsupportedOperationException(); }
        @Override public long getContentLengthLong() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.ServletInputStream getInputStream() { throw new UnsupportedOperationException(); }
        @Override public String getParameter(String name) { throw new UnsupportedOperationException(); }
        @Override public Enumeration<String> getParameterNames() { throw new UnsupportedOperationException(); }
        @Override public String[] getParameterValues(String name) { throw new UnsupportedOperationException(); }
        @Override public String getProtocol() { throw new UnsupportedOperationException(); }
        @Override public String getScheme() { throw new UnsupportedOperationException(); }
        @Override public String getServerName() { throw new UnsupportedOperationException(); }
        @Override public int getServerPort() { throw new UnsupportedOperationException(); }
        @Override public String getRemoteHost() { throw new UnsupportedOperationException(); }
        @Override public void setAttribute(String name, Object o) { throw new UnsupportedOperationException(); }
        @Override public void removeAttribute(String name) { throw new UnsupportedOperationException(); }
        @Override public java.util.Locale getLocale() { throw new UnsupportedOperationException(); }
        @Override public Enumeration<java.util.Locale> getLocales() { throw new UnsupportedOperationException(); }
        @Override public boolean isSecure() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.RequestDispatcher getRequestDispatcher(String path) { throw new UnsupportedOperationException(); }
        @Override public int getRemotePort() { throw new UnsupportedOperationException(); }
        @Override public String getLocalName() { throw new UnsupportedOperationException(); }
        @Override public String getLocalAddr() { throw new UnsupportedOperationException(); }
        @Override public int getLocalPort() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.ServletContext getServletContext() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.AsyncContext startAsync() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.AsyncContext startAsync(jakarta.servlet.ServletRequest servletRequest, jakarta.servlet.ServletResponse servletResponse) { throw new UnsupportedOperationException(); }
        @Override public boolean isAsyncStarted() { throw new UnsupportedOperationException(); }
        @Override public boolean isAsyncSupported() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.AsyncContext getAsyncContext() { throw new UnsupportedOperationException(); }
        @Override public jakarta.servlet.DispatcherType getDispatcherType() { throw new UnsupportedOperationException(); }
    }
} 
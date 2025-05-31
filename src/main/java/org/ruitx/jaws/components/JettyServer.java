package org.ruitx.jaws.components;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.strings.RequestType;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * JettyServer replaces Yggdrasill as the main HTTP server component.
 * It integrates Jetty with JAWS' existing route system and adds middleware support.
 */
public class JettyServer {

    /**
     * The current number of active connections.
     */
    public static final AtomicInteger currentConnections = new AtomicInteger(0);

    /**
     * The port on which the server is running.
     */
    public static int currentPort;

    /**
     * The base path for static resources.
     */
    public static String currentResourcesPath;

    private final int port;
    private final String resourcesPath;
    private final List<Middleware> middlewares = new ArrayList<>();
    private Server server;

    /**
     * Constructs a new JettyServer instance.
     *
     * @param port          the port to run the server on.
     * @param resourcesPath the path to the server's static resources.
     */
    public JettyServer(int port, String resourcesPath) {
        this.port = port;
        this.resourcesPath = resourcesPath;
        currentPort = port;
        currentResourcesPath = resourcesPath;
    }

    /**
     * Adds a middleware to the server.
     * Middleware will be executed in order of their priority (getOrder() method).
     *
     * @param middleware the middleware to add
     */
    public void addMiddleware(Middleware middleware) {
        middlewares.add(middleware);
        // Sort middleware by order
        middlewares.sort(Comparator.comparingInt(Middleware::getOrder));
    }

    /**
     * Starts the server, setting up Jetty with custom servlet for route handling.
     */
    public void start() {
        try {
            server = new Server();
            
            // Create connector
            ServerConnector connector = new ServerConnector(server);
            connector.setPort(port);
            connector.setIdleTimeout(ApplicationConfig.TIMEOUT);
            server.addConnector(connector);

            // Create servlet context
            ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
            context.setContextPath("/");

            // Set up static file serving
            setupStaticFileServing(context);

            // Set up dynamic route handling
            setupDynamicRouteHandling(context);

            server.setHandler(context);
            server.start();

            Logger.info("JettyServer started on port {} with resources path: {}", port, resourcesPath);
            
        } catch (Exception e) {
            Logger.error("JettyServer encountered an error: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to start JettyServer", e);
        }
    }

    /**
     * Sets up static file serving using Jetty's DefaultServlet.
     */
    private void setupStaticFileServing(ServletContextHandler context) {
        try {
            // Create static resource handler
            Path resourcePath = Paths.get(resourcesPath).toAbsolutePath();
            if (!Files.exists(resourcePath)) {
                Logger.warn("Static resources path does not exist: {}", resourcePath);
                Files.createDirectories(resourcePath);
            }

            // Use DefaultServlet for static files
            ServletHolder staticServlet = new ServletHolder("static", DefaultServlet.class);
            staticServlet.setInitParameter("resourceBase", resourcePath.toString());
            staticServlet.setInitParameter("dirAllowed", "true");
            staticServlet.setInitParameter("pathInfoOnly", "true");
            staticServlet.setInitParameter("welcomeServlets", "false");
            
            // Add static servlet with lower priority (mapped to /static/*)
            context.addServlet(staticServlet, "/static/*");
            
            Logger.info("Static file serving configured for path: {}", resourcePath);
        } catch (Exception e) {
            Logger.error("Failed to setup static file serving: {}", e.getMessage());
        }
    }

    /**
     * Sets up dynamic route handling using a custom servlet.
     */
    private void setupDynamicRouteHandling(ServletContextHandler context) {
        // Create and add the main request handling servlet
        ServletHolder jawsServlet = new ServletHolder("jaws", new JawsServlet());
        context.addServlet(jawsServlet, "/*");
    }

    /**
     * Shuts down the server.
     */
    public void shutdown() {
        Logger.info("JettyServer shutdown initiated...");
        if (server != null) {
            try {
                Logger.info("Shutting down JettyServer...");
                server.stop();
                server.join();
            } catch (Exception e) {
                Logger.error("Error shutting down JettyServer: {}", e.getMessage());
            }
        }
    }

    /**
     * Custom servlet that handles all dynamic requests and integrates with the existing JAWS route system.
     */
    private class JawsServlet extends HttpServlet {

        @Override
        protected void service(HttpServletRequest request, HttpServletResponse response) 
                throws ServletException, IOException {
            
            currentConnections.incrementAndGet();
            
            try {
                // Create JettyRequestHandler for this request
                JettyRequestHandler handler = new JettyRequestHandler(request, response, resourcesPath);

                // Execute middleware chain
                MiddlewareChainImpl middlewareChain = new MiddlewareChainImpl(middlewares, handler);
                boolean continueProcessing = middlewareChain.execute();

                if (!continueProcessing) {
                    // Middleware stopped the request
                    return;
                }

                // Process the request using existing JAWS logic
                processRequest(handler);

            } catch (Exception e) {
                Logger.error("Error processing request: {}", e.getMessage(), e);
                try {
                    if (!response.isCommitted()) {
                        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        response.setContentType("text/html; charset=UTF-8");
                        response.getWriter().write("Internal Server Error");
                        response.getWriter().flush();
                    }
                } catch (IOException ioException) {
                    Logger.error("Error sending error response: {}", ioException.getMessage());
                } finally {
                    currentConnections.decrementAndGet();
                }
            }
        }

        /**
         * Processes the request using JAWS' existing route system.
         */
        private void processRequest(JettyRequestHandler handler) throws IOException {
            String method = handler.getRequest().getMethod().toUpperCase();
            String endPoint = handler.getRequest().getRequestURI();

            // Remove query parameters from endpoint for route matching
            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex != -1) {
                endPoint = endPoint.substring(0, questionMarkIndex);
            }

            // Convert HTTP method to RequestType
            RequestType requestType = RequestType.fromString(method);
            if (requestType == null) {
                Logger.error("Invalid method: {}", method);
                handler.getResponse().setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                handler.getResponse().getWriter().write("405 - Method Not Allowed");
                return;
            }

            // Try to find and execute dynamic route
            boolean routeFound = handler.findDynamicRouteFor(endPoint, requestType);

            if (!routeFound) {
                // Try to serve static file if no dynamic route found
                if (requestType == RequestType.GET) {
                    if (tryServeStaticFile(handler, endPoint)) {
                        return;
                    }
                }
                
                // Send 404 if no route and no static file found
                handler.sendNotFoundResponse();
            }
        }

        /**
         * Attempts to serve a static file for the given endpoint.
         */
        private boolean tryServeStaticFile(JettyRequestHandler handler, String endPoint) throws IOException {
            Path filePath = getResourcePath(endPoint);
            Path fileToServe = getFileToServe(filePath);
            
            if (fileToServe != null) {
                serveStaticFile(handler, fileToServe);
                return true;
            }
            
            return false;
        }

        /**
         * Serves a static file with proper content type and template processing.
         */
        private void serveStaticFile(JettyRequestHandler handler, Path filePath) throws IOException {
            byte[] content = Files.readAllBytes(filePath);
            String contentType = Files.probeContentType(filePath);
            
            if (contentType == null) {
                contentType = "application/octet-stream";
            }

            if ("text/html".equals(contentType)) {
                // Process HTML templates
                String processedHTML = Hermod.processTemplate(
                    new String(content), 
                    handler.getQueryParams(), 
                    handler.getBodyParams()
                );
                processedHTML += "\n\n"; // Prevent truncation
                
                handler.getResponse().setStatus(HttpServletResponse.SC_OK);
                handler.getResponse().setContentType(contentType);
                handler.getResponse().getWriter().write(processedHTML);
            } else {
                // Serve binary content
                handler.getResponse().setStatus(HttpServletResponse.SC_OK);
                handler.getResponse().setContentType(contentType);
                handler.getResponse().getOutputStream().write(content);
            }
        }

        /**
         * Gets the file to serve based on the path, checking for index.html in directories.
         */
        private Path getFileToServe(Path path) {
            if (Files.exists(path) && !Files.isDirectory(path)) {
                return path;
            }

            if (Files.isDirectory(path)) {
                Path indexPath = path.resolve("index.html");
                if (Files.exists(indexPath) && !Files.isDirectory(indexPath)) {
                    return indexPath;
                }
            }

            return null;
        }

        /**
         * Returns the path for the static resource corresponding to the endpoint.
         */
        private Path getResourcePath(String endPoint) {
            return endPoint.equals("/") 
                ? Paths.get(resourcesPath + "/index.html") 
                : Paths.get(resourcesPath + endPoint);
        }
    }

    // Getter methods for compatibility
    public static int getCurrentConnections() {
        return currentConnections.get();
    }
} 
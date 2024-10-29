package org.ruitx.server.components;

import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.exceptions.ConnectionException;
import org.ruitx.server.strings.RequestType;
import org.ruitx.server.strings.ResponseCode;
import org.tinylog.Logger;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Yggdrasill {

    public static Integer currentConnections = 0;
    public static int currentPort;
    public static String currentResourcesPath;

    private final int port;
    private final String resourcesPath;

    public Yggdrasill(int port, String resourcesPath) {
        this.port = port;
        this.resourcesPath = resourcesPath;
        currentPort = port;
        currentResourcesPath = resourcesPath;
    }

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ExecutorService threadPool = Executors.newCachedThreadPool();
            acceptConnections(serverSocket, threadPool);
        } catch (IOException | ConnectionException e) {
            Logger.error("Yggdrasill encountered an error: %s", e.getMessage());
        }
    }

    private void acceptConnections(ServerSocket serverSocket, ExecutorService threadPool) throws ConnectionException {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();
                clientSocket.setSoTimeout((int) ApplicationConfig.TIMEOUT);
                threadPool.submit(new RequestHandler(clientSocket, resourcesPath));
                ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool;
                currentConnections = executor.getActiveCount();
            } catch (SocketTimeoutException e) {
                throw new ConnectionException("Socket timeout while waiting for connection", e);
            } catch (IllegalBlockingModeException e) {
                throw new ConnectionException("Illegal blocking mode: " + e.getMessage(), e);
            } catch (SecurityException e) {
                throw new ConnectionException("Security exception: " + e.getMessage(), e);
            } catch (IOException e) {
                throw new ConnectionException("Error accepting connection: " + e.getMessage(), e);
            }
        }
    }

    public static class RequestHandler implements Runnable {

        private final Socket socket;
        private final String resourcesPath;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final StringBuilder body = new StringBuilder();
        private Map<String, String> queryParams = new LinkedHashMap<>();
        private Map<String, String> bodyParams = new LinkedHashMap<>();
        private Map<String, String> formData = new LinkedHashMap<>();
        private BufferedReader in;
        private DataOutputStream out;

        public RequestHandler(Socket socket, String resourcesPath) {
            this.socket = socket;
            this.resourcesPath = resourcesPath;
        }

        @Override
        public void run() {
            try {
                processRequest();
            } catch (IOException e) {
                try {
                    closeSocket();
                } catch (IOException ex) {
                    Logger.error("Request failed.");
                }
                currentConnections--;
            }
        }

        public void processRequest() throws IOException {
            initializeStreams();
            readHeaders();
            readBody();
            checkRequestAndSendResponse();
            closeSocket();
        }

        private void initializeStreams() throws IOException {
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());
        }

        private void readHeaders() throws IOException {
            String headerLine;
            while (!(headerLine = in.readLine()).isEmpty()) {
                String[] parts = headerLine.split(" ", 2);
                headers.put(parts[0].replace(":", ""), parts[1]);
            }
        }

        private void readBody() throws IOException {
            while (in.ready()) {
                body.append((char) in.read());
            }
        }

        private void checkRequestAndSendResponse() throws IOException {
            String requestType = getRequestType();
            if (requestType.equals("INVALID")) {
                Logger.error("Invalid method: " + requestType);
                byte[] content = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <title>405 - Method Not Allowed</title>
                        </head>
                        <body>
                            <h1>405 - Method not allowed</h1>
                        </body>
                        </html>
                        """.getBytes();
                sendResponseHeaders(ResponseCode.METHOD_NOT_ALLOWED, "text/html", content.length);
                out.write(content);
                out.flush();
                return;
            }
            sendResponse(requestType, body.toString());
        }

        private String getRequestType() {
            return headers.keySet().stream()
                    .filter(this::isValidRequestType)
                    .findFirst()
                    .orElse("INVALID");
        }

        private boolean isValidRequestType(String key) {
            return key.equals("GET") || key.equals("POST") || key.equals("PUT") || key.equals("PATCH") || key.equals("DELETE");
        }

        private void sendResponse(String requestType, String body) throws IOException {
            String endPoint = headers.get(requestType).split(" ")[0];
            queryParams = extractQueryParameters(endPoint);

            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex != -1) {
                endPoint = endPoint.substring(0, questionMarkIndex);
            }

            switch (RequestType.fromString(requestType)) {
                case GET -> processGET(endPoint);
                case POST -> processPOST(endPoint, body);
                case PUT -> processPUT(endPoint, body);
                case PATCH -> Logger.warn("PATCH not implemented, ignored");
                case DELETE -> Logger.warn("DELETE not implemented, ignored");
                default -> Logger.error("Request type not recognized [" + requestType + "], ignored");
            }
        }

        private void processGET(String endPoint) throws IOException {
            Path path = getResourcePath(endPoint);
            if (Files.exists(path)) {
                if (queryParams == null) {
                    sendFileResponse(path);
                    return;
                }
                //TODO: change this to sendFileResponse(path, queryParams);
                sendFileResponse(path, queryParams, queryParams);
                return;
            }

            //Logger.warn("File not found: " + path + ". Trying to find a dynamic route...");
            if (!findDynamicRouteFor(endPoint, "GET")) {
                //Logger.info("No dynamic route found for GET request.");
                sendNotFoundResponse();
            }
        }

        public void sendHTMLResponse(ResponseCode responseCode, String body) throws IOException {
            byte[] content = body.getBytes();
            String contentType = "text/html";

            String parsedHTML = Hermes.parseHTML(new String(content));
            sendResponseHeaders(responseCode, contentType, parsedHTML.length());
            out.write(parsedHTML.getBytes());
            out.flush();
        }

        private Path getResourcePath(String endPoint) {
            return endPoint.equals("/") ? Paths.get(resourcesPath + "/index.html") : Paths.get(resourcesPath + endPoint);
        }

        private void sendFileResponse(Path path) throws IOException {
            byte[] content = Files.readAllBytes(path);
            String contentType = Files.probeContentType(path);

            if (contentType.equals("text/html")) {
                String parsedHTML = Hermes.parseHTML(new String(content));
                sendResponseHeaders(ResponseCode.OK, contentType, parsedHTML.length());
                out.write(parsedHTML.getBytes());
            } else {
                sendResponseHeaders(ResponseCode.OK, contentType, content.length);
                out.write(content);
            }
            out.flush();
        }

        private void sendFileResponse(Path path, Map<String, String> queryParams, Map<String, String> bodyParams) throws IOException {
            byte[] content = Files.readAllBytes(path);
            String contentType = Files.probeContentType(path);

            if (contentType.equals("text/html")) {
                String parsedHTML = Hermes.parseHTML(new String(content), queryParams, bodyParams);
                sendResponseHeaders(ResponseCode.OK, contentType, parsedHTML.length());
                out.write(parsedHTML.getBytes());
            } else {
                sendResponseHeaders(ResponseCode.OK, contentType, content.length);
                out.write(content);
            }
            out.flush();
        }

        private void sendNotFoundResponse() throws IOException {
            byte[] content;
            Path path = Paths.get(ApplicationConfig.CUSTOM_PAGE_PATH_404);
            if (Files.exists(path)) {
                content = Files.readAllBytes(path);
            } else {
                Logger.warn("Could not find 404 page, using hard coded default");
                String notFoundHtml = """
                        <!DOCTYPE html>
                        <html lang="en">
                        <head>
                            <meta charset="UTF-8">
                            <title>404 - Not Found</title>
                        </head>
                        <body>
                            <h1>404 - Not Found</h1>
                        </body>
                        </html>
                        """;
                content = notFoundHtml.getBytes();
            }
            sendResponseHeaders(ResponseCode.NOT_FOUND, "text/html", content.length);
            out.write(content);
            out.flush();
        }

        private void sendResponseHeaders(ResponseCode responseCode, String contentType, int contentLength) throws IOException {
            Hephaestus responseHeader = new Hephaestus.Builder()
                    .responseType(responseCode.toString())
                    .contentType(contentType)
                    .contentLength(String.valueOf(contentLength))
                    .endResponse()
                    .build();
            out.write(responseHeader.headerToBytes());
            out.flush();
        }

        private void processPOST(String endPoint, String body) throws IOException {
            byte[] content = parseFormData(body).toString().getBytes();
            sendResponseHeaders(ResponseCode.OK, "text/plain", content.length);
            out.write(content);
            out.flush();
        }

        private void processPUT(String endPoint, String body) throws IOException {
            formData = parseFormData(body);
            // Endpoint.getEndpoint(endPoint).getHandler().execute(this);
        }

        private Map<String, String> parseFormData(String data) {
            Map<String, String> parsedData = new LinkedHashMap<>();
            String[] pairs = data.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                parsedData.put(key, value);
            }
            return parsedData;
        }

        private Map<String, String> extractQueryParameters(String endPoint) {
            int questionMarkIndex = endPoint.indexOf('?');
            if (questionMarkIndex == -1) {
                return null;
            }

            Map<String, String> queryParams = new LinkedHashMap<>();
            String queryString = endPoint.substring(questionMarkIndex + 1);
            String[] pairs = queryString.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                queryParams.put(key, value);
            }
            return queryParams;
        }

        private boolean findDynamicRouteFor(String endPoint, String method) throws IOException {
            Method routeMethod = Njord.getInstance().getRoute(endPoint, method);
            if (routeMethod != null) {
                String controllerName = routeMethod.getDeclaringClass().getSimpleName();
                Object controllerInstance = Njord.getInstance().getControllerInstance(controllerName);
                try {
                    //Logger.info("Found. Invoking dynamic route: " + routeMethod.getName());
                    synchronized (this) {
                        routeMethod.invoke(controllerInstance, this); // Invoke the method on the instance
                    }
                    
                    return true;
                } catch (Exception e) {
                    Logger.error("Failed to invoke method: ", e);
                    closeSocket();
                }
            }

            return false;
        }

        private void closeSocket() throws IOException {
            synchronized (Yggdrasill.class) {
                currentConnections--;
            }
            socket.close();
        }

        public Map<String, String> getQueryParams() {
            return queryParams;
        }
    }
}

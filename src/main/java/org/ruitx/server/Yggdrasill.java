package org.ruitx.server;

import org.ruitx.server.components.Hephaestus;
import org.ruitx.server.components.Hermes;
import org.ruitx.server.configs.Constants;
import org.ruitx.server.strings.Messages;
import org.ruitx.server.strings.RequestType;
import org.ruitx.server.strings.ResponseCode;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

public class Yggdrasill {

    public static Integer currentConnections = 0;

    private final int port;
    private final String resourcesPath;

    public Yggdrasill(int port, String resourcesPath) {
        this.port = port;
        this.resourcesPath = resourcesPath;
    }

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ExecutorService threadPool = Executors.newCachedThreadPool();
            acceptConnections(serverSocket, threadPool);
        }
    }

    private void acceptConnections(ServerSocket serverSocket, ExecutorService threadPool) {
        while (true) {
            try {
                Socket clientSocket = serverSocket.accept();

                threadPool.submit(new RequestHandler(clientSocket, resourcesPath));
                ThreadPoolExecutor executor = (ThreadPoolExecutor) threadPool;
                currentConnections = executor.getActiveCount();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RequestHandler implements Runnable {

        private final Socket socket;
        private final String resourcesPath;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private final StringBuilder body = new StringBuilder();
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
                currentConnections--;
                logError(Messages.INTERNAL_SERVER_ERROR);
            }
        }

        public void processRequest() throws IOException {
            initializeStreams();
            readHeaders();
            readBody();

            long startRequestTime = System.currentTimeMillis();
            startTimeoutThread(startRequestTime);

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
                logError(Messages.INVALID_REQUEST);
                return; // Handle invalid request
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

            switch (RequestType.fromString(requestType)) {
                case GET -> processGET(endPoint);
                case POST -> processPOST(endPoint, body);
                case PUT -> processPUT(endPoint, body);
                case PATCH -> System.out.println("PATCH");
                case DELETE -> System.out.println("DELETE");
                default -> System.out.println("INVALID");
            }
        }

        private void processGET(String endPoint) throws IOException {
            Path path = getResourcePath(endPoint);
            if (Files.exists(path)) {
                sendFileResponse(path);
            } else {
                sendNotFoundResponse();
            }
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
        }

        private void sendNotFoundResponse() throws IOException {
            byte[] content = Files.readAllBytes(Paths.get(resourcesPath + "/404.html"));
            sendResponseHeaders(ResponseCode.NOT_FOUND, "text/html", content.length);
            out.write(content);
        }

        private void sendResponseHeaders(ResponseCode responseCode, String contentType, int contentLength) throws IOException {
            Hephaestus responseHeader = new Hephaestus.Builder()
                    .responseType(responseCode.toString())
                    .contentType(contentType)
                    .contentLength(String.valueOf(contentLength))
                    .endResponse()
                    .build();
            out.write(responseHeader.headerToBytes());
        }

        private void processPOST(String endPoint, String body) throws IOException {
            formData = parseFormData(body);
            // Endpoint.getEndpoint(endPoint).getHandler().execute(this);
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

        private void closeSocket() throws IOException {
            currentConnections--;
            socket.close();
        }

        private void startTimeoutThread(long startRequestTime) {
            new Thread(() -> {
                while (!socket.isClosed()) {
                    if (System.currentTimeMillis() - startRequestTime > Constants.TIMEOUT) {
                        logTimeout(startRequestTime);
                        try {
                            closeSocket();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                        return;
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }).start();
        }

        private void logTimeout(long startRequestTime) {
            long totalRequestTime = System.currentTimeMillis() - startRequestTime;
            System.out.printf(Messages.CLIENT_LOG,
                    Instant.now().getEpochSecond(),
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort(),
                    Messages.CLIENT_TIMEOUT + " [" + totalRequestTime + " ms]");
        }

        private void logError(String message) {
            System.out.printf(message, Instant.now().getEpochSecond(), message);
        }
    }
}

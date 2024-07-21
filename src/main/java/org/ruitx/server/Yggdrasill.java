package org.ruitx.server;

import org.ruitx.server.components.Hephaestus;
import org.ruitx.server.components.Hermes;
import org.ruitx.server.configs.Constants;
import org.ruitx.server.strings.Messages;
import org.ruitx.server.strings.RequestType;
import org.ruitx.server.strings.ResponseCode;
import org.ruitx.server.util.TerminalColors;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Yggdrasill {

    static int port;
    static String resourcesPath;

    public Yggdrasill(int port, String resourcesPath) {
        Yggdrasill.port = port;
        Yggdrasill.resourcesPath = resourcesPath;
    }

    public static int getPort() {
        return port;
    }

    public static String getResourcesPath() {
        return resourcesPath;
    }

    public void start() throws IOException {

        try (ServerSocket serverSocket = new ServerSocket(port)) {
            ExecutorService threadPool = Executors.newCachedThreadPool();

            /*
            System.out.printf(Messages.SERVER_LOG.getMessage(),
                    Instant.now().getEpochSecond(),
                    Messages.SERVER_STARTED.getMessage());

            System.out.printf(Messages.SERVER_LOG.getMessage(),
                    Instant.now().getEpochSecond(),
                    "Port: " + port);

            System.out.printf(Messages.SERVER_LOG.getMessage(),
                    Instant.now().getEpochSecond(),
                    "WWW Path: " + resourcesPath);

             */
            acceptConnections(serverSocket, threadPool);

        }
    }

    private void acceptConnections(ServerSocket serverSocket, ExecutorService threadPool) {
        while (true) {
            try {
                RequestHandler requestHandler = new RequestHandler(serverSocket.accept());
                threadPool.submit(requestHandler);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static class RequestHandler implements Runnable {

        private final Socket socket;
        private final Map<String, String> headers;
        private final StringBuilder body;
        private Map<String, String> formData;
        private BufferedReader in;
        private DataOutputStream out;

        public RequestHandler(Socket socket) {

            this.socket = socket;
            headers = new LinkedHashMap<>();
            body = new StringBuilder();
            formData = new LinkedHashMap<>();
            in = null;
            out = null;
        }

        @Override
        public void run() {
            try {
                dealWithRequest(socket);
            } catch (IOException e) {
                System.out.printf(Messages.INTERNAL_SERVER_ERROR,
                        Instant.now().getEpochSecond(),
                        Messages.INTERNAL_SERVER_ERROR);
            }
        }

        /**
         * Deal with the client request
         *
         * @param socket
         */
        public void dealWithRequest(Socket socket) throws IOException {

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new DataOutputStream(socket.getOutputStream());

            // Get request headers into an ordered map
            String requesHeaderLine;
            //while (!Objects.equals(requesHeaderLine = in.readLine(), "")) {
            while (!(requesHeaderLine = in.readLine()).isEmpty()) {
                String[] requestLine = requesHeaderLine.split(" ", 2);
                headers.put(requestLine[0].replace(":", ""), requestLine[1]);
            }

            // Read the body of the request
            while (in.ready()) {
                body.append((char) in.read());
            }

            /*
            System.out.printf(Messages.CLIENT_LOG.getMessage(),
                    Instant.now().getEpochSecond(),
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort(),
                    Messages.CLIENT_CONNECTED.getMessage()
                            + " [" + getRequestType(headers) + " " + getEndpointFromHeaders(headers) + "]");
             */

            long startRequestTime = System.currentTimeMillis();

            // Start timeout thread
            initTimeoutFrom(startRequestTime);

            // Check the request and send the response
            checkRequestAndSendResponse(headers, body.toString());
            this.closeSocket();

            /*
            long totalRequestTime = System.currentTimeMillis() - startRequestTime;
            System.out.printf(Messages.CLIENT_LOG.getMessage(),
                    Instant.now().getEpochSecond(),
                    socket.getInetAddress().getHostAddress(),
                    socket.getPort(),
                    Messages.CLIENT_DISCONNECTED.getMessage() + " [" + totalRequestTime + " ms]");
             */
        }

        /**
         * Check the request type and send the appropriate response
         *
         * @param headers
         */
        private void checkRequestAndSendResponse(Map<String, String> headers, String body) throws IOException {
            String requestType = getRequestType(headers);
            sendResponse(requestType, body);
        }

        /**
         * Get the request type from the request headers
         *
         * @param headers
         * @return
         */
        private String getRequestType(Map<String, String> headers) {
            Optional<String> requestType = headers.keySet()
                    .stream()
                    .filter(key -> key.equals("GET") || key.equals("POST") || key.equals("PUT") || key.equals("PATCH") || key.equals("DELETE"))
                    .findFirst();

            return requestType.orElse("INVALID");
        }

        /**
         * Send the appropriate response based on the request type.
         * Type of request is determined by the request headers.
         *
         * @param requestType
         */
        private void sendResponse(String requestType, String body) throws IOException {
            RequestType request = RequestType.fromString(requestType);

            switch (request) {
                case GET:
                    for (String key : headers.keySet()) {
                        if (key.equals("GET")) {
                            String endPoint = headers.get(key).split(" ")[0];
                            processGET(endPoint);
                            return;
                        }
                    }
                    break;
                case POST:
                    for (String key : headers.keySet()) {
                        if (key.equals("POST")) {
                            String endPoint = headers.get(key).split(" ")[0];
                            processPOST(endPoint, body);
                            return;
                        }
                    }
                    break;
                case PUT:
                    for (String key : headers.keySet()) {
                        if (key.equals("PUT")) {
                            String endPoint = headers.get(key).split(" ")[0];
                            processPUT(endPoint, body);
                            return;
                        }
                    }
                    break;
                case PATCH:
                    System.out.println("PATCH");
                    break;
                case DELETE:
                    System.out.println("DELETE");
                    break;
                default:
                    System.out.println("INVALID");
                    break;
            }
        }

        /**
         * Get the endpoint from the request headers
         *
         * @param headers
         * @return
         */
        private String getEndpointFromHeaders(Map<String, String> headers) {
            for (String key : headers.keySet()) {
                if (key.equals("GET") || key.equals("POST") || key.equals("PUT") || key.equals("PATCH") || key.equals("DELETE")) {
                    return headers.get(key).split(" ")[0];
                }
            }
            return "INVALID";
        }

        /**
         * Process the PUT request
         *
         * @param endPoint
         * @throws IOException
         */
        private void processPUT(String endPoint, String body) throws IOException {
            // Parse the form data
            formData = parseFormData(body);
            //Endpoint.getEndpoint(endPoint).getHandler().execute(this);
        }

        /**
         * Process the POST request
         *
         * @param endPoint
         * @throws IOException
         */
        private void processPOST(String endPoint, String body) throws IOException {
            // Parse the form data
            formData = parseFormData(body);
            //Endpoint.getEndpoint(endPoint).getHandler().execute(this);
        }

        /**
         * Process the GET request.
         * Always check if a file/page with the name of the end point exists
         * If not, then checks if the end point is on the list of endpoints
         *
         * @param endPoint the end point to send
         * @throws IOException
         */
        private void processGET(String endPoint) throws IOException {
            String htmlFile = endPoint.equals("/")
                    ? resourcesPath + "/index.html"
                    : resourcesPath + endPoint;

            Path path = Paths.get(htmlFile);
            byte[] htmlPage;
            String contentType;

            // Tries to load from filesystem
            if (Files.exists(path)) {
                htmlPage = Files.readAllBytes(path);
                contentType = Files.probeContentType(path);
            } else {
                // Fall back to loading from resources within the JAR or from an endpoint
                InputStream resourceStream = getClass().getResourceAsStream(htmlFile);
                if (resourceStream == null) {
                    // If the file does not exist, then load the 404 page
                    // TODO
                    //  Add custom 404 page
                    htmlPage = Files.readAllBytes(Paths.get(resourcesPath + "/404.html"));
                    Hephaestus responseHeader = new Hephaestus.Builder()
                            .responseType(ResponseCode.NOT_FOUND.toString())
                            .contentType("text/html")
                            .contentLength(String.valueOf(htmlPage.length))
                            .endResponse()
                            .build();

                    sendHeaderAndPage(responseHeader.headerToBytes(), htmlPage);

                    //TODO
                    // Handle case where the file does not exist
                    // e.g., resource not found response or Endpoint check
                    return;
                }
                htmlPage = resourceStream.readAllBytes();
                resourceStream.close();
                contentType = URLConnection.guessContentTypeFromStream(new ByteArrayInputStream(htmlPage));
            }

            // Parse only HTML files
            if (contentType.equals("text/html")) {
                String parsedHTML = Hermes.parseHTML(new String(htmlPage));
                Hephaestus responseHeader = new Hephaestus.Builder()
                        .responseType(ResponseCode.OK.toString())
                        .contentType(contentType)
                        .contentLength(String.valueOf(parsedHTML.length()))
                        .endResponse()
                        .build();

                sendHeaderAndPage(responseHeader.headerToBytes(), parsedHTML.getBytes());
                return;
            }

            Hephaestus responseHeader = new Hephaestus.Builder()
                    .responseType(ResponseCode.OK.toString())
                    .contentType(contentType)
                    .contentLength(String.valueOf(htmlPage.length))
                    .endResponse()
                    .build();

            sendHeaderAndPage(responseHeader.headerToBytes(), htmlPage);
        }

        /**
         * Parse the form data
         *
         * @param data
         * @return
         */
        private Map<String, String> parseFormData(String data) {
            Map<String, String> temp = new LinkedHashMap<>();
            String[] pairs = data.split("&");
            for (String pair : pairs) {
                String[] keyValue = pair.split("=");
                String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                String value = keyValue.length > 1 ? URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8) : "";
                temp.put(key, value);
            }
            return temp;
        }

        /**
         * Send the header and the page
         *
         * @param htmlPage the page to send
         * @param header   the header to send
         * @throws IOException
         */
        private void sendHeaderAndPage(byte[] header, byte[] htmlPage) throws IOException {
            out.write(header, 0, header.length);
            out.write(htmlPage, 0, htmlPage.length);
        }

        /**
         * Close the socket
         *
         * @throws IOException
         */
        private void closeSocket() throws IOException {
            this.socket.close();
        }

        /**
         * Initializes the timeout thread.
         * The timeout thread will check if the request has timed out.
         * If it has, then the socket will be closed.
         * Timeout is set to be CONSTANT.TIMEOUT ms.
         *
         * @param startRequestTime the start request time
         */
        private void initTimeoutFrom(long startRequestTime) {
            Thread timeoutThread = new Thread(() -> {
                while (!this.socket.isClosed()) {
                    if (System.currentTimeMillis() - startRequestTime > Constants.TIMEOUT) {
                        try {
                            long totalRequestTime = System.currentTimeMillis() - startRequestTime;
                            System.out.printf(Messages.CLIENT_LOG,
                                    Instant.now().getEpochSecond(),
                                    socket.getInetAddress().getHostAddress(),
                                    socket.getPort(),
                                    Messages.CLIENT_TIMEOUT
                                            + TerminalColors.ANSI_WHITE + " [" + getRequestType(headers) + " " + getEndpointFromHeaders(headers) + "]" + TerminalColors.ANSI_RESET
                                            + " [" + totalRequestTime + " ms]");
                            this.closeSocket();
                            return;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
            });
            timeoutThread.start();
        }

        public Socket getSocket() {
            return socket;
        }

        public DataOutputStream getOutStream() {
            return out;
        }

        public Map<String, String> getFormData() {
            return formData;
        }
    }

}

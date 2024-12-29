package org.ruitx.jaws.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.ruitx.jaws.configs.ApplicationConfig.APPLICATION_NAME;

/**
 * Hephaestus is a class that represents an HTTP response header.
 */
public final class Hephaestus {

    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String DEFAULT_SERVER = APPLICATION_NAME;
    private static final String DEFAULT_CONNECTION = "keep-alive";
    private static final String DEFAULT_CACHE_CONTROL = "no-cache";
    private static final String DEFAULT_CONTENT_TYPE = "text/html";
    private static final String DEFAULT_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private final String responseType;
    private final Map<String, String> headers;

    private Hephaestus(Builder builder) {
        this.responseType = builder.responseType;
        this.headers = builder.headers;
    }

    /**
     * Convert the header map to a string.
     *
     * @return the header as a string
     */
    public String headerToString() {
        return responseType + "\r\n" +
                headers.entrySet().stream()
                        .map(entry -> entry.getKey() + ": " + entry.getValue() + "\r\n")
                        .collect(Collectors.joining()) +
                "\r\n";
    }

    public byte[] headerToBytes() {
        return headerToString().getBytes();
    }

    public static class Builder {
        private final Map<String, String> headers = new TreeMap<>();
        private String responseType;

        public Builder() {
        }

        public Builder responseType(String responseType) {
            if (responseType == null || responseType.trim().isEmpty()) {
                throw new IllegalArgumentException("Response type cannot be null or empty");
            }
            this.responseType = HTTP_VERSION + " " + responseType;
            return this;
        }

        public Builder contentType(String contentType) {
            if (contentType != null && !contentType.trim().isEmpty()) {
                headers.put("Content-Type", contentType);
            }
            return this;
        }

        public Builder contentLength(String contentLength) {
            if (contentLength != null && !contentLength.trim().isEmpty()) {
                headers.put("Content-Length", contentLength);
            }
            return this;
        }

        public Builder server(String server) {
            if (server != null && !server.trim().isEmpty()) {
                headers.put("Server", server);
            }
            return this;
        }

        public Builder date(String date) {
            if (date != null && !date.trim().isEmpty()) {
                headers.put("Date", date);
            }
            return this;
        }

        public Builder connection(String connection) {
            if (connection != null && !connection.trim().isEmpty()) {
                headers.put("Connection", connection);
            }
            return this;
        }

        public Builder cacheControl(String cacheControl) {
            if (cacheControl != null && !cacheControl.trim().isEmpty()) {
                headers.put("Cache-Control", cacheControl);
            }
            return this;
        }

        public Builder addCookie(String cookieName, String cookieValue, int maxAgeInSeconds) {
            String cookieHeader = String.format(
                    "%s=%s; Max-Age=%d; Path=/; HttpOnly; Secure",
                    cookieName, cookieValue, maxAgeInSeconds
            );
            return addCustomHeader("Set-Cookie", cookieHeader);
        }

        public Builder addCustomHeader(String name, String value) {
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Header name cannot be null or empty");
            }
            if (value == null || value.trim().isEmpty()) {
                throw new IllegalArgumentException("Header value cannot be null or empty");
            }
            headers.put(name, value);
            return this;
        }

        public Hephaestus build() {
            // Apply default headers only if they haven't been set already
            if (!headers.containsKey("Cache-Control")) {
                headers.put("Cache-Control", DEFAULT_CACHE_CONTROL);
            }
            if (!headers.containsKey("Connection")) {
                headers.put("Connection", DEFAULT_CONNECTION);
            }
            if (!headers.containsKey("Content-Type")) {
                headers.put("Content-Type", DEFAULT_CONTENT_TYPE);
            }
            if (!headers.containsKey("Date")) {
                SimpleDateFormat formatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
                headers.put("Date", formatter.format(new Date()));
            }
            if (!headers.containsKey("Server")) {
                headers.put("Server", DEFAULT_SERVER);
            }

            return new Hephaestus(this);
        }
    }
}

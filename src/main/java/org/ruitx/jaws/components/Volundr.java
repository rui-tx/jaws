package org.ruitx.jaws.components;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.ruitx.jaws.configs.ApplicationConfig.APPLICATION_NAME;
import static org.ruitx.jaws.strings.HttpHeaders.*;

/**
 * Völundr is a class that represents an HTTP response header.
 */
public final class Volundr {

    private static final String HTTP_VERSION = "HTTP/1.1";
    private static final String DEFAULT_SERVER = APPLICATION_NAME;
    private static final String DEFAULT_CONNECTION = "keep-alive";
    private static final String DEFAULT_CACHE_CONTROL = "no-cache";
    private static final String DEFAULT_CONTENT_TYPE = "text/html";
    private static final String DEFAULT_DATE_FORMAT = "EEE, dd MMM yyyy HH:mm:ss zzz";

    private final String responseType;
    private final Map<String, String> headers;

    private Volundr(Builder builder) {
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
                headers.put(CONTENT_TYPE.getHeaderName(), contentType);
            }
            return this;
        }

        public Builder contentLength(String contentLength) {
            if (contentLength != null && !contentLength.trim().isEmpty()) {
                headers.put(CONTENT_LENGTH.getHeaderName(), contentLength);
            }
            return this;
        }

        public Builder server(String server) {
            if (server != null && !server.trim().isEmpty()) {
                headers.put(SERVER.getHeaderName(), server);
            }
            return this;
        }

        public Builder date(String date) {
            if (date != null && !date.trim().isEmpty()) {
                headers.put(DATE.getHeaderName(), date);
            }
            return this;
        }

        public Builder connection(String connection) {
            if (connection != null && !connection.trim().isEmpty()) {
                headers.put(CONNECTION.getHeaderName(), connection);
            }
            return this;
        }

        public Builder cacheControl(String cacheControl) {
            if (cacheControl != null && !cacheControl.trim().isEmpty()) {
                headers.put(CACHE_CONTROL.getHeaderName(), cacheControl);
            }
            return this;
        }

        public Builder addCookie(String cookieName, String cookieValue, int maxAgeInSeconds) {
            String cookieHeader = String.format(
                    "%s=%s; Max-Age=%d; Path=/; HttpOnly; Secure",
                    cookieName, cookieValue, maxAgeInSeconds
            );
            return addCustomHeader(SET_COOKIE.getHeaderName(), cookieHeader);
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

        public Volundr build() {
            // Apply default headers only if they haven't been set already
            if (!headers.containsKey(CACHE_CONTROL.getHeaderName())) {
                headers.put(CACHE_CONTROL.getHeaderName(), DEFAULT_CACHE_CONTROL);
            }
            if (!headers.containsKey(CONNECTION.getHeaderName())) {
                headers.put(CONNECTION.getHeaderName(), DEFAULT_CONNECTION);
            }
            if (!headers.containsKey(CONTENT_TYPE.getHeaderName())) {
                headers.put(CONTENT_TYPE.getHeaderName(), DEFAULT_CONTENT_TYPE);
            }
            if (!headers.containsKey(DATE.getHeaderName())) {
                SimpleDateFormat formatter = new SimpleDateFormat(DEFAULT_DATE_FORMAT);
                headers.put(DATE.getHeaderName(), formatter.format(new Date()));
            }
            if (!headers.containsKey(SERVER.getHeaderName())) {
                headers.put(SERVER.getHeaderName(), DEFAULT_SERVER);
            }

            return new Volundr(this);
        }
    }
}

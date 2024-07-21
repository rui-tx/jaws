package org.ruitx.server.components;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Hephaestus is a class that represents an HTTP response header.
 */
public final class Hephaestus {
    private final String requestType;
    private final String contentType;
    private final String contentLength;
    private final String server;
    private final String date;
    private final String connection;
    private final String cacheControl;
    private final String endResponse;

    private Hephaestus(Builder builder) {
        this.requestType = builder.responseType;
        this.contentType = builder.contentType;
        this.contentLength = builder.contentLength;
        this.server = builder.server;
        this.date = builder.date;
        this.connection = builder.connection;
        this.cacheControl = builder.cacheControl;
        this.endResponse = builder.endResponse;
    }

    /**
     * Convert the header to a string
     * Checks if the field is not null before converting it to a string
     *
     * @return the header as a string
     */
    public String headerToString() {
        Field[] fields = this.getClass().getDeclaredFields();
        List<Field> nonNullFields = Arrays.stream(fields)
                .filter(f -> {
                    try {
                        return f.get(this) != null;
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .toList();

        return nonNullFields.stream()
                .map(f -> {
                    try {
                        return "" + f.get(this);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(Collectors.joining());
    }

    public byte[] headerToBytes() {
        return headerToString().getBytes();
    }

    public static class Builder {
        private String responseType;
        private String endResponse;
        private String contentType;
        private String contentLength;
        private String server;
        private String date;
        private String connection;
        private String cacheControl;

        public Builder() {
        }

        public Builder responseType(String responseType) {
            if (responseType == null) {
                throw new IllegalArgumentException("Request type cannot be null");
            }
            this.responseType = "HTTP/1.1 " + responseType + "\r\n";
            return this;
        }

        public Builder contentType(String contentType) {
            this.contentType = "Content-Type: " + contentType + "\r\n";
            return this;
        }

        public Builder contentLength(String contentLength) {
            this.contentLength = "Content-Length: " + contentLength + "\r\n";
            return this;
        }

        public Builder server(String server) {
            this.server = "Server: " + server + "\r\n";
            return this;
        }

        public Builder date(String date) {
            this.date = "Date: " + date + "\r\n";
            return this;
        }

        public Builder connection(String connection) {
            this.connection = "Connection: " + connection + "\r\n";
            return this;
        }

        public Builder cacheControl(String cacheControl) {
            this.cacheControl = "Cache-Control: " + cacheControl + "\r\n";
            return this;
        }

        public Builder endResponse() {
            this.endResponse = "\r\n";
            return this;
        }

        public Hephaestus build() {
            return new Hephaestus(this);
        }
    }
}
package org.ruitx.jaws.strings;

public enum RequestType {
    GET, POST, PUT, PATCH, DELETE, INVALID;

    public static RequestType fromString(String requestType) {
        return switch (requestType) {
            case "GET" -> GET;
            case "POST" -> POST;
            case "PUT" -> PUT;
            case "PATCH" -> PATCH;
            case "DELETE" -> DELETE;
            default -> INVALID;
        };
    }

    public static String toString(RequestType requestType) {
        return switch (requestType) {
            case GET -> "GET";
            case POST -> "POST";
            case PUT -> "PUT";
            case PATCH -> "PATCH";
            case DELETE -> "DELETE";
            default -> "INVALID";
        };
    }
}

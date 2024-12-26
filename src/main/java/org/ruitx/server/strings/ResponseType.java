package org.ruitx.server.strings;

public enum ResponseType {
    INVALID, HTML, JSON;

    public static ResponseType fromString(String responseType) {
        return switch (responseType) {
            case "HTML" -> HTML;
            case "JSON" -> JSON;
            default -> INVALID;
        };
    }

    public static String toString(ResponseType responseType) {
        return switch (responseType) {
            case HTML -> "HTML";
            case JSON -> "JSON";
            default -> "INVALID";
        };
    }
}

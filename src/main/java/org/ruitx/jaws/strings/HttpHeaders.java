package org.ruitx.jaws.strings;

public enum HttpHeaders {

    A_IM("A-IM"),
    ACCEPT("Accept"),
    ACCEPT_CHARSET("Accept-Charset"),
    ACCEPT_DATETIME("Accept-Datetime"),
    ACCEPT_ENCODING("Accept-Encoding"),
    ACCEPT_LANGUAGE("Accept-Language"),
    ACCESS_CONTROL_REQUEST_METHOD("Access-Control-Request-Method"),
    ACCESS_CONTROL_REQUEST_HEADERS("Access-Control-Request-Headers"),
    AUTHORIZATION("Authorization"),
    CACHE_CONTROL("Cache-Control"),
    CONNECTION("Connection"),
    CONTENT_ENCODING("Content-Encoding"),
    CONTENT_LENGTH("Content-Length"),
    CONTENT_TYPE("Content-Type"),
    COOKIE("Cookie"),
    DATE("Date"),
    EXPECT("Expect"),
    FORWARDED("Forwarded"),
    FROM("From"),
    HOST("Host"),
    IF_MATCH("If-Match"),
    IF_MODIFIED_SINCE("If-Modified-Since"),
    IF_NONE_MATCH("If-None-Match"),
    IF_RANGE("If-Range"),
    IF_UNMODIFIED_SINCE("If-Unmodified-Since"),
    TE("TE"),
    USER_AGENT("User-Agent"),
    UPGRADE("Upgrade"),
    VIA("Via"),
    WARNING("Warning"),
    LOCATION("Location"),
    SERVER("Server"),
    SET_COOKIE("Set-Cookie");

    private final String headerName;

    HttpHeaders(String headerName) {
        this.headerName = headerName;
    }

    public String getHeaderName() {
        return headerName.toLowerCase();
    }

    @Override
    public String toString() {
        return headerName;
    }
}
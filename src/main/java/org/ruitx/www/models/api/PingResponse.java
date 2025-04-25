package org.ruitx.www.models.api;

/**
 * Represents a ping response with server status information.
 */
public record PingResponse(
    String status
) {
    public static PingResponse ok() {
        return new PingResponse("pong");
    }
} 
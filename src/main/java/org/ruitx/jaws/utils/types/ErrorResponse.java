package org.ruitx.jaws.utils.types;

/**
 * Standard error response model.
 * Used when returning error details to the client.
 */
public record ErrorResponse(
    String message,
    String details
) {
    /**
     * Creates a simple error response with just code and message.
     */
    public static ErrorResponse of(String message) {
        return new ErrorResponse(message, null);
    }
} 
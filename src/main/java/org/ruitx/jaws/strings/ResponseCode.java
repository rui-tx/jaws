package org.ruitx.jaws.strings;

public enum ResponseCode {
    OK(200, "OK"),
    CREATED(201, "CREATED"),
    ACCEPTED(202, "ACCEPTED"),
    NO_CONTENT(204, "NO CONTENT"),
    MOVED_PERMANENTLY(301, "MOVED PERMANENTLY"),
    FOUND(302, "FOUND"),
    SEE_OTHER(303, "SEE OTHER"),
    NOT_MODIFIED(304, "NOT MODIFIED"),
    TEMPORARY_REDIRECT(307, "TEMPORARY REDIRECT"),
    PERMANENT_REDIRECT(308, "PERMANENT REDIRECT"),
    BAD_REQUEST(400, "BAD REQUEST"),
    UNAUTHORIZED(401, "UNAUTHORIZED"),
    FORBIDDEN(403, "FORBIDDEN"),
    NOT_FOUND(404, "NOT FOUND"),
    METHOD_NOT_ALLOWED(405, "METHOD NOT ALLOWED"),
    CONFLICT(409, "CONFLICT"),
    GONE(410, "GONE"),
    LENGTH_REQUIRED(411, "LENGTH REQUIRED"),
    PRECONDITION_FAILED(412, "PRECONDITION FAILED"),
    PAYLOAD_TOO_LARGE(413, "PAYLOAD TOO LARGE"),
    UNSUPPORTED_MEDIA_TYPE(415, "UNSUPPORTED MEDIA TYPE"),
    INTERNAL_SERVER_ERROR(500, "INTERNAL SERVER ERROR"),
    NOT_IMPLEMENTED(501, "NOT IMPLEMENTED"),
    BAD_GATEWAY(502, "BAD GATEWAY"),
    SERVICE_UNAVAILABLE(503, "SERVICE UNAVAILABLE"),
    GATEWAY_TIMEOUT(504, "GATEWAY TIMEOUT"),
    HTTP_VERSION_NOT_SUPPORTED(505, "HTTP VERSION NOT SUPPORTED");

    private final int code;
    private final String message;

    ResponseCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResponseCode fromCode(int code) {
        for (ResponseCode responseCode : values()) {
            if (responseCode.getCode() == code) {
                return responseCode;
            }
        }
        return null;
    }

    public static ResponseCode fromString(String message) {
        for (ResponseCode responseCode : values()) {
            if (responseCode.getMessage().equalsIgnoreCase(message)) {
                return responseCode;
            }
        }
        return null;
    }

    /**
     * Extracts a ResponseCode from a code and message string (e.g., "400 BAD REQUEST")
     * @param codeWithMessage The string containing the code and message
     * @return The corresponding ResponseCode, or INTERNAL_SERVER_ERROR if parsing fails
     */
    public static ResponseCode fromCodeAndMessage(String codeWithMessage) {
        try {
            int code = Integer.parseInt(codeWithMessage.split(" ")[0]);
            ResponseCode responseCode = fromCode(code);
            return responseCode != null ? responseCode : INTERNAL_SERVER_ERROR;
        } catch (Exception e) {
            return INTERNAL_SERVER_ERROR;
        }
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getCodeAndMessage() {
        return code + " " + message;
    }

    @Override
    public String toString() {
        return code + " " + message;
    }
}

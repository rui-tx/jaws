package org.ruitx.jaws.exceptions;

import org.ruitx.jaws.strings.ResponseCode;

/**
 * Exception for business logic errors that should be returned to the client.
 */
public class BusinessException extends RuntimeException {
    private final ResponseCode responseCode;

        public BusinessException(ResponseCode responseCode, String message) {
        super(message);
        this.responseCode = responseCode;
    }

    public BusinessException(ResponseCode responseCode, String message, Throwable cause) {
        super(message, cause);
        this.responseCode = responseCode;
    }

    public ResponseCode getResponseCode() {
        return responseCode;
    }
}

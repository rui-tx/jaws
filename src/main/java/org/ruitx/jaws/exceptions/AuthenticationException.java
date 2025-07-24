package org.ruitx.jaws.exceptions;

import org.ruitx.jaws.strings.ResponseCode;

/**
 * Exception for authentication and authorization errors.
 */
public class AuthenticationException extends BusinessException {
    public AuthenticationException(String message) {
        super(ResponseCode.UNAUTHORIZED, message);
    }

    public AuthenticationException(ResponseCode responseCode, String message) {
        super(responseCode, message);
    }
}

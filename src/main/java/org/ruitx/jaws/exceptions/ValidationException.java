package org.ruitx.jaws.exceptions;

import org.ruitx.jaws.strings.ResponseCode;

/**
 * Exception for input validation errors.
 */
public class ValidationException extends BusinessException {
    private final String field;
    private final Object rejectedValue;

    public ValidationException(String field, Object rejectedValue, String message) {
        super(ResponseCode.BAD_REQUEST, message);
        this.field = field;
        this.rejectedValue = rejectedValue;
    }

    public String getField() {
        return field;
    }

    public Object getRejectedValue() {
        return rejectedValue;
    }
}

package org.ruitx.jaws.exceptions;

public class ProcessRequestException extends RuntimeException {
    public ProcessRequestException(String message) {
        super(message);
    }

    public ProcessRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}

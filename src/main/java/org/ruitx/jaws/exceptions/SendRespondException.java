package org.ruitx.jaws.exceptions;

public class SendRespondException extends RuntimeException {
    public SendRespondException(String message) {
        super(message);
    }

    public SendRespondException(String message, Throwable cause) {
        super(message, cause);
    }
}

package org.ruitx.jaws.exceptions;

/**
 * Exception for input validation errors.
 */
public class ValidationException extends RuntimeException {

  private final ValidationError error;
  private final String field;
  private final Object rejectedValue;

  public ValidationException(ValidationError error, String field, Object rejectedValue) {
    super(error.getMessage());
    this.error = error;
    this.field = field;
    this.rejectedValue = rejectedValue;
  }

  public ValidationError getError() {
    return error;
  }

  public String getField() {
    return field;
  }

  public Object getRejectedValue() {
    return rejectedValue;
  }
}

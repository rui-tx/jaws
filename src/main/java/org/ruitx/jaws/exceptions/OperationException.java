package org.ruitx.jaws.exceptions;

public class OperationException extends RuntimeException {

  private final OperationError error;

  public OperationException(OperationError error) {
    super(error.getMessage());
    this.error = error;
  }

  public OperationError getError() {
    return error;
  }
}
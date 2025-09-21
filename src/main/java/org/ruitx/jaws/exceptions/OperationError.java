package org.ruitx.jaws.exceptions;

import org.ruitx.jaws.enums.ResponseCode;

public enum OperationError {
  DUPLICATE_USER("User already exists", ResponseCode.CONFLICT),
  USER_NOT_FOUND("User not found", ResponseCode.NOT_FOUND),
  DUPLICATE_ENTITY("Entity already exists", ResponseCode.CONFLICT),
  ENTITY_NOT_FOUND("Entity not found", ResponseCode.NOT_FOUND),
  INVALID_STATE("Invalid state for operation", ResponseCode.BAD_REQUEST),
  OPERATION_NOT_ALLOWED("Operation not allowed", ResponseCode.FORBIDDEN),
  CONFLICTING_OPERATION("Conflicting operation in progress", ResponseCode.CONFLICT),
  OPERATION_FAILED("Operation failed", ResponseCode.INTERNAL_SERVER_ERROR);

  private final String message;
  private final ResponseCode responseCode;

  OperationError(String message, ResponseCode responseCode) {
    this.message = message;
    this.responseCode = responseCode;
  }

  public String getMessage() {
    return message;
  }

  public ResponseCode getResponseCode() {
    return responseCode;
  }
}
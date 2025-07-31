package org.ruitx.jaws.exceptions;

import org.ruitx.jaws.strings.ResponseCode;

public enum AuthenticationError {
  INVALID_CREDENTIALS("Invalid username or password", ResponseCode.UNAUTHORIZED),
  TOKEN_EXPIRED("Session token has expired", ResponseCode.UNAUTHORIZED),
  TOKEN_INVALID("Invalid token provided", ResponseCode.UNAUTHORIZED),
  ACCOUNT_LOCKED("Account is locked", ResponseCode.FORBIDDEN),
  ACCOUNT_DISABLED("Account is disabled", ResponseCode.FORBIDDEN),
  INSUFFICIENT_PERMISSIONS("Insufficient permissions", ResponseCode.FORBIDDEN),
  AUTHENTICATION_REQUIRED("Authentication required", ResponseCode.UNAUTHORIZED);

  private final String message;
  private final ResponseCode responseCode;

  AuthenticationError(String message, ResponseCode responseCode) {
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
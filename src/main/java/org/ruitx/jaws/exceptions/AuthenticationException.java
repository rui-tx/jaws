package org.ruitx.jaws.exceptions;

/**
 * Exception for authentication and authorization errors.
 */
public class AuthenticationException extends RuntimeException {

  private final AuthenticationError error;

  public AuthenticationException(AuthenticationError error) {
    super(error.getMessage());
    this.error = error;
  }

  public AuthenticationError getError() {
    return error;
  }
}

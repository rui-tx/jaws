package org.ruitx.jaws.exceptions;

import org.ruitx.jaws.enums.ResponseCode;

public enum ValidationError {
  INVALID_EMAIL_FORMAT("Invalid email format", ResponseCode.BAD_REQUEST),
  PASSWORD_TOO_SHORT("Password must be at least 8 characters", ResponseCode.BAD_REQUEST),
  MISSING_REQUIRED_FIELD("Missing required field", ResponseCode.BAD_REQUEST),
  INVALID_FIELD_FORMAT("Invalid field format", ResponseCode.BAD_REQUEST),
  FIELD_TOO_LONG("Field value too long", ResponseCode.BAD_REQUEST),
  FIELD_TOO_SHORT("Field value too short", ResponseCode.BAD_REQUEST),
  INVALID_ENUM_VALUE("Invalid enum value provided", ResponseCode.BAD_REQUEST),
  INVALID_DATE_FORMAT("Invalid date format", ResponseCode.BAD_REQUEST);

  private final String message;
  private final ResponseCode responseCode;

  ValidationError(String message, ResponseCode responseCode) {
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

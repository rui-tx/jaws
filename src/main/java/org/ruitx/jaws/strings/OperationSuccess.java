package org.ruitx.jaws.strings;

public enum OperationSuccess {
  USER_CREATED("User created successfully!"),
  USER_UPDATED("User updated successfully!"),
  USER_DELETED("User deleted successfully!"),
  LOGIN_SUCCESSFUL("Login successful!"),
  LOGOUT_SUCCESSFUL("Logout successful!"),
  TOKEN_REFRESHED("Token refreshed successfully!"),
  TOKEN_REVOKED("Token revoked successfully!"),
  ROLE_ASSIGNED("Role assigned successfully!"),
  ROLE_REMOVED("Role removed successfully!"),
  PASSWORD_CHANGED("Password changed successfully!"),
  ACCOUNT_ACTIVATED("Account activated successfully!"),
  ACCOUNT_DEACTIVATED("Account deactivated successfully!");

  private final String message;

  OperationSuccess(String message) {
    this.message = message;
  }

  public String getMessage() {
    return message;
  }
}

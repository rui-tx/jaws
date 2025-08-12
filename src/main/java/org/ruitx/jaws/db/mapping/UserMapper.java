package org.ruitx.jaws.db.mapping;

import java.util.Optional;
import org.ruitx.jaws.types.Row;
import org.ruitx.www.model.auth.User;

/**
 * Authoritative Row -> User mapper. Keeps models pure from persistence concerns.
 */
public final class UserMapper implements RowMapper<User> {
  public static final UserMapper INSTANCE = new UserMapper();
  private UserMapper() {}

  @Override
  public User map(Row row) {
    if (row == null) return null;

    Optional<Integer> id = row.getInt("id");
    Optional<String> usernameOpt = row.getString("user");
    Optional<String> passwordHash = row.getString("password_hash");
    Optional<String> emailOpt = row.getString("email");
    Optional<String> firstNameOpt = row.getString("first_name");
    Optional<String> lastNameOpt = row.getString("last_name");
    Optional<Long> birthdateOpt = row.getUnixTimestamp("birthdate");
    Optional<String> genderOpt = row.getString("gender");
    Optional<String> phoneNumberOpt = row.getString("phone_number");
    Optional<String> profilePictureOpt = row.getString("profile_picture");
    Optional<String> bioOpt = row.getString("bio");
    Optional<String> locationOpt = row.getString("location");
    Optional<String> websiteOpt = row.getString("website");
    Optional<Long> lastLoginOpt = row.getUnixTimestamp("last_login");
    Optional<Integer> isActiveOpt = row.getInt("is_active");

    Optional<Integer> failedLoginAttemptsOpt = row.getInt("failed_login_attempts");
    Optional<Long> lockoutUntilOpt = row.getUnixTimestamp("lockout_until");
    Optional<Long> createdAt = row.getUnixTimestamp("created_at");
    Optional<Long> updatedAt = row.getUnixTimestamp("updated_at");

    if (id.isEmpty() || usernameOpt.isEmpty() || passwordHash.isEmpty() || createdAt.isEmpty()) {
      return null; // Essential fields must be present
    }

    return User.builder()
        .id(id.get())
        .user(usernameOpt.get())
        .passwordHash(passwordHash.get())
        .email(emailOpt.orElse(null))
        .firstName(firstNameOpt.orElse(null))
        .lastName(lastNameOpt.orElse(null))
        .birthdate(birthdateOpt.orElse(null))
        .gender(genderOpt.orElse(null))
        .phoneNumber(phoneNumberOpt.orElse(null))
        .profilePicture(profilePictureOpt.orElse(null))
        .bio(bioOpt.orElse(null))
        .location(locationOpt.orElse(null))
        .website(websiteOpt.orElse(null))
        .lastLogin(lastLoginOpt.orElse(null))
        .isActive(isActiveOpt.orElse(1))
        .failedLoginAttempts(failedLoginAttemptsOpt.orElse(0))
        .lockoutUntil(lockoutUntilOpt.orElse(null))
        .createdAt(createdAt.get())
        .updatedAt(updatedAt.orElse(null))
        .build();
  }
}

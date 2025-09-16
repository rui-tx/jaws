import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.utils.JawsUtils;

class JawsUtilsTest {

  // ------------------------------
  // Password hashing and validation
  // ------------------------------

  @Test
  @DisplayName("hashPassword returns null when input is null")
  void hashPassword_null_returnsNull() {
    assertNull(JawsUtils.hashPassword(null));
  }

  @Test
  @DisplayName("hashPassword produces a BCrypt hash that validates with isValidPassword")
  void hashAndValidatePassword_success() {
    String password = "S3cret!";
    String hash = JawsUtils.hashPassword(password);

    assertNotNull(hash);
    assertTrue(hash.startsWith("$2"), "Expected a BCrypt hash format");
    assertTrue(JawsUtils.isValidPassword(password, hash));
  }

  @Test
  @DisplayName("isValidPassword returns false for wrong password and true for correct one")
  void isValidPassword_wrongPassword_returnsFalse() {
    String hash = JawsUtils.hashPassword("correct");
    assertFalse(JawsUtils.isValidPassword("wrong", hash));
    assertTrue(JawsUtils.isValidPassword("correct", hash));
  }

  @Test
  @DisplayName("isValidPassword returns false when password is null")
  void isValidPassword_nullPassword_returnsFalse() {
    String hash = JawsUtils.hashPassword("anything");
    assertFalse(JawsUtils.isValidPassword(null, hash));
  }

  @Test
  @DisplayName("isValidPassword throws when hash is null")
  void isValidPassword_nullHash_throws() {
    assertThrows(Exception.class, () -> JawsUtils.isValidPassword("pw", null));
  }

  // ------------------------------
  // formatUnixTimestamp
  // ------------------------------

  @Test
  @DisplayName("formatUnixTimestamp respects provided pattern and seconds input")
  void formatUnixTimestamp_seconds_input() {
    long seconds = 1_700_000_000L; // arbitrary positive epoch seconds
    String pattern = "yyyy-MM-dd";

    // Build expected using Java time with system default zone (same as implementation)
    String expected = ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds),
            ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern));

    assertEquals(expected, JawsUtils.formatUnixTimestamp(seconds, pattern));
  }

  @Test
  @DisplayName("formatUnixTimestamp converts millis to seconds when needed")
  void formatUnixTimestamp_millis_input() {
    long seconds = 1_700_000_000L;
    long millis = seconds * 1000L;

    String expected = ZonedDateTime.ofInstant(Instant.ofEpochSecond(seconds),
            ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

    assertEquals(expected, JawsUtils.formatUnixTimestamp(millis));
  }

  // ------------------------------
  // getOrDefault
  // ------------------------------

  @Test
  @DisplayName("getOrDefault returns newValue when non-null, otherwise default")
  void getOrDefault_behaviour() {
    assertEquals("new", JawsUtils.getOrDefault("new", "default"));
    assertEquals("default", JawsUtils.getOrDefault(null, "default"));

    Integer def = 42;
    assertEquals(def, JawsUtils.getOrDefault(null, def));
  }

  // ------------------------------
  // newPassword helpers
  // ------------------------------

  @Test
  @DisplayName("newPassword(length) generates a password of requested length")
  void newPassword_withLength() {
    Optional<String> pwd = JawsUtils.newPassword(24);
    assertTrue(pwd.isPresent());
    assertEquals(24, pwd.get().length());
  }

  @Test
  @DisplayName("newPassword() generates a password with default length 16")
  void newPassword_defaultLength() {
    Optional<String> pwd = JawsUtils.newPassword();
    assertTrue(pwd.isPresent());
    assertEquals(16, pwd.get().length());
  }

  @Test
  @DisplayName("newPassword with streamSize 0 returns empty Optional")
  void newPassword_zeroLength_empty() {
    Optional<String> pwd = JawsUtils.newPassword(0, 33, 122);
    assertTrue(pwd.isEmpty());
  }
}

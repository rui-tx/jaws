package org.ruitx.jaws.components.mimir;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a single row of data with column name as key and its corresponding value. Provides
 * methods to retrieve values by column name with type safety.
 *
 * @param data A map containing column name as key and its corresponding value.
 */
public record Row(Map<String, Object> data) {

  /**
   * Gets the value associated with the specified column name.
   *
   * @param columnName The name of the column to retrieve.
   * @return The value associated with the column name, or null if not found.
   */
  public Object get(String columnName) {
    return data.get(columnName);
  }

  /**
   * Gets the string value associated with the specified column name as an Optional.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as a string if present, or empty if not found.
   */
  public Optional<String> getString(String columnName) {
    Object value = data.get(columnName);
    return value instanceof String ? Optional.of((String) value) : Optional.empty();
  }

  /**
   * Gets the integer value associated with the specified column name as an Optional.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as an Integer if present, or empty if not found.
   */
  public Optional<Integer> getInt(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Integer ? Optional.of((Integer) value) : Optional.empty();
  }

  /**
   * Gets the long value associated with the specified column name as an Optional.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as a Long if present, or empty if not found.
   */
  public Optional<Long> getLong(String columnName) {
    Object value = data.get(columnName);
    if (value instanceof Integer) {
      return Optional.of(((Integer) value).longValue());
    }
    return value instanceof Long ? Optional.of((Long) value) : Optional.empty();
  }

  /**
   * Gets the double value associated with the specified column name as an Optional.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as a Double if present, or empty if not found.
   */
  public Optional<Double> getDouble(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Double ? Optional.of((Double) value) : Optional.empty();
  }

  /**
   * Gets the float value associated with the specified column name as an Optional.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as a Float if present, or empty if not found.
   */
  public Optional<Float> getFloat(String columnName) {
    Object value = data.get(columnName);
    if (value instanceof Double) {
      return Optional.of(((Double) value).floatValue());
    }
    return value instanceof Float ? Optional.of((Float) value) : Optional.empty();
  }

  /**
   * Gets the byte array (blob) value associated with the specified column name as an Optional.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as a byte array if present, or empty if not found.
   */
  public Optional<byte[]> getBlob(String columnName) {
    Object value = data.get(columnName);
    return value instanceof byte[] ? Optional.of((byte[]) value) : Optional.empty();
  }

  /**
   * Gets the boolean value associated with the specified column name as an Optional. It also
   * supports Integer values where 0 is false and any non-zero value is true.
   *
   * @param columnName The name of the column to retrieve.
   * @return An Optional containing the value as a Boolean if present, or empty if not found.
   */
  public Optional<Boolean> getBoolean(String columnName) {
    Object value = data.get(columnName);
    if (value instanceof Integer) {
      return Optional.of(((Integer) value) != 0);
    }
    return value instanceof Boolean ? Optional.of((Boolean) value) : Optional.empty();
  }

  /**
   * Checks if the value associated with the specified column name is null.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is null, false otherwise.
   */
  public boolean isNull(String columnName) {
    return data.get(columnName) == null;
  }

  /**
   * Checks if the row contains a column with the specified name.
   *
   * @param columnName The name of the column to check.
   * @return true if the column exists, false otherwise.
   */
  public boolean containsColumn(String columnName) {
    return data.containsKey(columnName);
  }

  // Type checking methods

  /**
   * Checks if the value associated with the specified column name is of type String.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a String, false otherwise.
   */
  public boolean isString(String columnName) {
    Object value = data.get(columnName);
    return value instanceof String;
  }

  /**
   * Checks if the value associated with the specified column name is of type Integer.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is an Integer, false otherwise.
   */
  public boolean isInteger(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Integer;
  }

  /**
   * Checks if the value associated with the specified column name is of type Long.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a Long or Integer, false otherwise.
   */
  public boolean isLong(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Long || value instanceof Integer;
  }

  /**
   * Checks if the value associated with the specified column name is of type Double.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a Double, false otherwise.
   */
  public boolean isDouble(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Double;
  }

  /**
   * Checks if the value associated with the specified column name is of type Float.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a Float or Double, false otherwise.
   */
  public boolean isFloat(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Float || value instanceof Double;
  }

  /**
   * Checks if the value associated with the specified column name is of type byte array (blob).
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a byte array, false otherwise.
   */
  public boolean isBlob(String columnName) {
    Object value = data.get(columnName);
    return value instanceof byte[];
  }

  /**
   * Checks if the value associated with the specified column name is of type Boolean. It also
   * supports Integer values where 0 is false and any non-zero value is true.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a Boolean or Integer (0 or 1), false otherwise.
   */
  public boolean isBoolean(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Boolean
        || (value instanceof Integer && ((Integer) value == 0 || (Integer) value == 1));
  }

  /**
   * Checks if the value associated with the specified column name is numeric. This includes
   * Integer, Long, Double, and Float types.
   *
   * @param columnName The name of the column to check.
   * @return true if the value is a numeric type, false otherwise.
   */
  public boolean isNumeric(String columnName) {
    Object value = data.get(columnName);
    return value instanceof Number;
  }

  /**
   * Gets a timestamp as Unix timestamp (seconds since epoch)
   *
   * @param columnName The name of the column
   * @return Optional containing the Unix timestamp in seconds
   */
  public Optional<Long> getUnixTimestamp(String columnName) {
    Object value = data.get(columnName);
    if (value instanceof Long) {
      return Optional.of((Long) value);
    }
    if (value instanceof Integer) {
      return Optional.of(((Integer) value).longValue());
    }
    if (value instanceof String) {
      try {
        // Try parsing as ISO 8601 string first
        LocalDateTime dateTime = LocalDateTime.parse((String) value);
        return Optional.of(dateTime.atZone(ZoneId.systemDefault()).toEpochSecond());
      } catch (Exception e) {
        // If that fails, try parsing as Unix timestamp string
        try {
          return Optional.of(Long.parseLong((String) value));
        } catch (NumberFormatException nfe) {
          return Optional.empty();
        }
      }
    }
    return Optional.empty();
  }

  /**
   * Gets a timestamp as Instant (UTC)
   *
   * @param columnName The name of the column
   * @return Optional containing the Instant
   */
  public Optional<Instant> getInstant(String columnName) {
    return getUnixTimestamp(columnName).map(Instant::ofEpochSecond);
  }

  /**
   * Gets a timestamp as LocalDateTime in the system's default timezone
   *
   * @param columnName The name of the column
   * @return Optional containing the LocalDateTime
   */
  public Optional<LocalDateTime> getTimestamp(String columnName) {
    return getInstant(columnName)
        .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
  }

  /**
   * Gets a timestamp as LocalDateTime in the specified timezone
   *
   * @param columnName The name of the column
   * @param zoneId     The timezone to use
   * @return Optional containing the LocalDateTime
   */
  public Optional<LocalDateTime> getTimestamp(String columnName, ZoneId zoneId) {
    return getInstant(columnName)
        .map(instant -> instant.atZone(zoneId).toLocalDateTime());
  }
}

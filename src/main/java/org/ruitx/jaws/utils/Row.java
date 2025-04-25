package org.ruitx.jaws.utils;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

public record Row(Map<String, Object> data) {

    public Object get(String columnName) {
        return data.get(columnName);
    }

    public Optional<String> getString(String columnName) {
        Object value = data.get(columnName);
        return value instanceof String ? Optional.of((String) value) : Optional.empty();
    }

    public Optional<Integer> getInt(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Integer ? Optional.of((Integer) value) : Optional.empty();
    }

    public Optional<Long> getLong(String columnName) {
        Object value = data.get(columnName);
        if (value instanceof Integer) {
            return Optional.of(((Integer) value).longValue());
        }
        return value instanceof Long ? Optional.of((Long) value) : Optional.empty();
    }

    public Optional<Double> getDouble(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Double ? Optional.of((Double) value) : Optional.empty();
    }

    public Optional<Float> getFloat(String columnName) {
        Object value = data.get(columnName);
        if (value instanceof Double) {
            return Optional.of(((Double) value).floatValue());
        }
        return value instanceof Float ? Optional.of((Float) value) : Optional.empty();
    }

    public Optional<byte[]> getBlob(String columnName) {
        Object value = data.get(columnName);
        return value instanceof byte[] ? Optional.of((byte[]) value) : Optional.empty();
    }

    public Optional<Boolean> getBoolean(String columnName) {
        Object value = data.get(columnName);
        if (value instanceof Integer) {
            return Optional.of(((Integer) value) != 0);
        }
        return value instanceof Boolean ? Optional.of((Boolean) value) : Optional.empty();
    }

    public boolean isNull(String columnName) {
        return data.get(columnName) == null;
    }

    public boolean containsColumn(String columnName) {
        return data.containsKey(columnName);
    }

    // Type checking methods
    public boolean isString(String columnName) {
        Object value = data.get(columnName);
        return value instanceof String;
    }

    public boolean isInteger(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Integer;
    }

    public boolean isLong(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Long || value instanceof Integer;
    }

    public boolean isDouble(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Double;
    }

    public boolean isFloat(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Float || value instanceof Double;
    }

    public boolean isBlob(String columnName) {
        Object value = data.get(columnName);
        return value instanceof byte[];
    }

    public boolean isBoolean(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Boolean || (value instanceof Integer && ((Integer) value == 0 || (Integer) value == 1));
    }

    public boolean isNumeric(String columnName) {
        Object value = data.get(columnName);
        return value instanceof Number;
    }

    /**
     * Gets a timestamp as Unix timestamp (seconds since epoch)
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
     * @param columnName The name of the column
     * @return Optional containing the Instant
     */
    public Optional<Instant> getInstant(String columnName) {
        return getUnixTimestamp(columnName).map(Instant::ofEpochSecond);
    }

    /**
     * Gets a timestamp as LocalDateTime in the system's default timezone
     * @param columnName The name of the column
     * @return Optional containing the LocalDateTime
     */
    public Optional<LocalDateTime> getTimestamp(String columnName) {
        return getInstant(columnName)
            .map(instant -> instant.atZone(ZoneId.systemDefault()).toLocalDateTime());
    }

    /**
     * Gets a timestamp as LocalDateTime in the specified timezone
     * @param columnName The name of the column
     * @param zoneId The timezone to use
     * @return Optional containing the LocalDateTime
     */
    public Optional<LocalDateTime> getTimestamp(String columnName, ZoneId zoneId) {
        return getInstant(columnName)
            .map(instant -> instant.atZone(zoneId).toLocalDateTime());
    }
}


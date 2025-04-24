package org.ruitx.jaws.utils;

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
}


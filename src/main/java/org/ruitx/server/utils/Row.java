package org.ruitx.server.utils;

import java.util.Map;

public record Row(Map<String, Object> data) {

    public Object get(String columnName) {
        return data.get(columnName);
    }

    public String getString(String columnName) {
        return (String) data.get(columnName);
    }

    public Integer getInt(String columnName) {
        return (Integer) data.get(columnName);
    }

    public boolean containsColumn(String columnName) {
        return data.containsKey(columnName);
    }
}


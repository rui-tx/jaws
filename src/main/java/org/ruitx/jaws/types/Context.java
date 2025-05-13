package org.ruitx.jaws.types;

import java.util.Map;

public record Context(
        Map<String, String> context
) {

    public Context() {
        this(Map.of());
    }
}

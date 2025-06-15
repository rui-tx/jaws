package org.ruitx.jaws.types;

import java.util.LinkedHashMap;
import java.util.Map;

public record Context(
        Map<String, Object> context
) {

    public Context() {
        this(Map.of());
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private final Map<String, Object> context = new LinkedHashMap<>();

        public Builder with(String key, Object value) {
            context.put(key, value);
            return this;
        }

        public Builder withAll(Map<String, Object> additionalVars) {
            context.putAll(additionalVars);
            return this;
        }

        public Context build() {
            return new Context(new LinkedHashMap<>(context));
        }
    }
}

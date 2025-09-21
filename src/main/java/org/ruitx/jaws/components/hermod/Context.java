package org.ruitx.jaws.components.hermod;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Context is a class for representing a context with key-value pairs. It provides a builder pattern
 * for constructing instances.
 */
public record Context(
    Map<String, Object> context
) {


  public Context() {
    this(Map.of());
  }


  /**
   * Creates a new Builder instance for constructing a Context object with a fluent API.
   *
   * @return a new Builder instance for building a Context
   */
  public static Builder builder() {
    return new Builder();
  }


  public static class Builder {

    private final Map<String, Object> context = new LinkedHashMap<>();

    /**
     * Adds a key-value pair to the context.
     *
     * @param key   the key for the context variable
     * @param value the value for the context variable
     * @return this builder instance for method chaining
     */
    public Builder with(String key, Object value) {
      context.put(key, value);
      return this;
    }

    /**
     * Adds multiple key-value pairs to the context.
     *
     * @param additionalVars a map containing additional context variables
     * @return this builder instance for method chaining
     */
    public Builder withAll(Map<String, Object> additionalVars) {
      context.putAll(additionalVars);
      return this;
    }

    /**
     * Builds a new Context instance with the current state of the builder.
     *
     * @return a new Context instance containing the key-value pairs added to the builder
     */
    public Context build() {
      return new Context(new LinkedHashMap<>(context));
    }
  }
}

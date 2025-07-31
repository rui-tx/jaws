package org.ruitx.jaws.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as eligible for Mimir query caching. Tables specified inform selective
 * invalidation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Cacheable {

  /**
   * List of tables the method's query reads. Used for fine-grained invalidation; empty means
   * "unspecified / detect automatically".
   */
  String[] tables() default {};

  /**
   * Optional TTL (milliseconds). Negative means use global default.
   */
  long ttl() default -1L;
}

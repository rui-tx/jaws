package org.ruitx.jaws.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for methods that require access control checks.
 * <p>
 * This annotation can be used to specify whether a method requires user login and what role is
 * required to access it.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AccessControl {

  /**
   * Indicates whether the method requires the user to be logged in. Defaults to false, meaning no
   * login is required.
   *
   * @return true if login is required, false otherwise
   */
  boolean login() default false;

  /**
   * Specifies the role required to access the method. If not specified, no specific role is
   * required.
   *
   * @return the required role as a string
   */
  String role() default "";
}

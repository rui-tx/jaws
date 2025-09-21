package org.ruitx.jaws.enums;

/**
 * Enum for specifying parameter types when retrieving request parameters.
 */
public enum ParamType {
  /**
   * Path parameters (e.g., /users/{id})
   */
  PATH,

  /**
   * Query parameters (e.g., ?name=value)
   */
  QUERY,

  /**
   * Body parameters (form data or JSON body)
   */
  BODY
} 
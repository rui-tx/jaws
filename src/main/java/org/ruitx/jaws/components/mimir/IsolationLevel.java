package org.ruitx.jaws.components.mimir;

/**
 * Enumeration representing the isolation levels for database transactions. This is used to define
 * how transaction integrity is visible to other transactions.
 */
public enum IsolationLevel {
  READ_UNCOMMITTED,
  SERIALIZABLE
}

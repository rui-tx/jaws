package org.ruitx.jaws.components.freyr;

/**
 * Job status enum
 */
public enum JobStatus {
  PENDING, PROCESSING, COMPLETED, FAILED, TIMEOUT, RETRY_SCHEDULED, DEAD_LETTER
}

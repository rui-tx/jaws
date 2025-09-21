package org.ruitx.jaws.models;

import org.ruitx.jaws.components.freyr.ExecutionMode;
import org.ruitx.jaws.components.freyr.JobStatus;

public record Jobs(
    String id,
    String type,
    Integer priority,
    Integer maxRetries,
    Integer currentRetries,
    Integer timeoutMs,
    ExecutionMode executionMode,
    JobStatus status,
    Integer createdAt,
    Integer completedAt,
    String errorMessage,
    String clientId,
    String userId, // Fk to USER table
    Integer nextRetryAt,
    Integer retryBackoffMs,
    Integer lastRetryAt
) {

  public static class Columns {

    // TODO: do the rest
    public static final String ID = "id";
    public static final String STATUS = "status";

  }
}

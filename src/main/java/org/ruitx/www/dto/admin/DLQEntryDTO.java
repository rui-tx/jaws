package org.ruitx.www.dto.admin;

import java.util.List;
import java.util.Map;

/**
 * DTO for Dead Letter Queue entries in admin interface
 */
public record DLQEntryDTO(
    String id,
    String originalJobId,
    String jobType,
    String executionMode,
    Map<String, Object> payload,
    int priority,
    int maxRetries,
    String failureReason,
    long failedAt,
    int retryAttempts,
    List<String> retryHistory,
    boolean canBeRetried,
    long createdAt
) {} 
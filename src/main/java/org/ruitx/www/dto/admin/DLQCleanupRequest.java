package org.ruitx.www.dto.admin;

/**
 * Request DTO for DLQ cleanup operations
 */
public record DLQCleanupRequest(
    Integer retentionDays
) {} 
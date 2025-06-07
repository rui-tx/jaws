package org.ruitx.www.dto.admin;

/**
 * Request DTO for testing error classification
 */
public record ErrorClassificationTestRequest(
    String exceptionType,
    String exceptionMessage,
    String jobType
) {} 
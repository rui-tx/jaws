package org.ruitx.www.dto.admin;

/**
 * Request DTO for manual retry operations
 */
public record ManualRetryRequest(
    Boolean resetRetryCount
) {} 
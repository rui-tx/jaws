package org.ruitx.www.dto.admin;

import java.util.List;

/**
 * Request DTO for batch retry operations
 */
public record BatchRetryRequest(
    List<String> dlqEntryIds,
    Boolean resetRetryCount
) {} 
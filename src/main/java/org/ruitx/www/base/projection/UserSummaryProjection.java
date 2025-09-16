package org.ruitx.www.base.projection;

import java.util.Optional;

/**
 * Lightweight projection for listing users without loading the full User entity.
 * Columns must be aliased to match component names (case-insensitive).
 */
public record UserSummaryProjection(
    Optional<Long> id,
    Optional<String> user,
    Optional<String> firstName,
    Optional<String> lastName
) {}

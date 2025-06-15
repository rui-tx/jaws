package org.ruitx.jaws.types;

import java.util.Optional;

/**
 * PageRequest represents pagination parameters for database queries.
 * Uses 0-based page indexing (first page is page 0).
 */
public record PageRequest(
    int page,
    int size,
    Optional<String> sortBy,
    Optional<SortDirection> direction
) {
    
    /**
     * Validation constructor that ensures valid pagination parameters.
     */
    public PageRequest {
        if (page < 0) {
            throw new IllegalArgumentException("Page index must be >= 0 (0-based indexing)");
        }
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be > 0");
        }
        if (size > 1000) {
            throw new IllegalArgumentException("Page size must be <= 1000 to prevent performance issues");
        }
    }
    
    /**
     * Simple constructor for basic pagination without sorting.
     * 
     * @param page 0-based page index
     * @param size number of items per page
     */
    public PageRequest(int page, int size) {
        this(page, size, Optional.empty(), Optional.empty());
    }
    
    /**
     * Constructor with sorting parameters.
     * 
     * @param page 0-based page index
     * @param size number of items per page
     * @param sortBy column name to sort by
     * @param direction sort direction
     */
    public PageRequest(int page, int size, String sortBy, SortDirection direction) {
        this(page, size, Optional.of(sortBy), Optional.of(direction));
    }
    
    /**
     * Constructor with only sort column (defaults to ASC).
     * 
     * @param page 0-based page index
     * @param size number of items per page
     * @param sortBy column name to sort by
     */
    public PageRequest(int page, int size, String sortBy) {
        this(page, size, Optional.of(sortBy), Optional.of(SortDirection.ASC));
    }
    
    /**
     * Calculate the SQL OFFSET value for this page request.
     * 
     * @return offset for SQL LIMIT clause
     */
    public int getOffset() {
        return page * size;
    }
    
    /**
     * Check if this page request has sorting specified.
     * 
     * @return true if sortBy is present
     */
    public boolean hasSorting() {
        return sortBy.isPresent();
    }
    
    /**
     * Get the effective sort direction (defaults to ASC if not specified).
     * 
     * @return the sort direction, or ASC if not specified
     */
    public SortDirection getEffectiveDirection() {
        return direction.orElse(SortDirection.ASC);
    }
    
    /**
     * Create a new PageRequest for the next page.
     * 
     * @return new PageRequest for the next page
     */
    public PageRequest nextPage() {
        return new PageRequest(page + 1, size, sortBy, direction);
    }
    
    /**
     * Create a new PageRequest for the previous page.
     * 
     * @return new PageRequest for the previous page
     * @throws IllegalArgumentException if already on the first page
     */
    public PageRequest previousPage() {
        if (page == 0) {
            throw new IllegalArgumentException("Already on the first page (page 0)");
        }
        return new PageRequest(page - 1, size, sortBy, direction);
    }
    
    /**
     * Create a new PageRequest with different sorting.
     * 
     * @param newSortBy new sort column
     * @param newDirection new sort direction
     * @return new PageRequest with updated sorting
     */
    public PageRequest withSort(String newSortBy, SortDirection newDirection) {
        return new PageRequest(page, size, Optional.of(newSortBy), Optional.of(newDirection));
    }
    
    /**
     * Create a new PageRequest without sorting.
     * 
     * @return new PageRequest without sorting
     */
    public PageRequest withoutSort() {
        return new PageRequest(page, size, Optional.empty(), Optional.empty());
    }
} 
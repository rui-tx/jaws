package org.ruitx.jaws.types;

/**
 * Enumeration for sort directions in pagination queries.
 */
public enum SortDirection {
    ASC("ASC"),
    DESC("DESC");
    
    private final String sqlKeyword;
    
    SortDirection(String sqlKeyword) {
        this.sqlKeyword = sqlKeyword;
    }
    
    /**
     * Get the SQL keyword for this sort direction.
     * 
     * @return SQL keyword (ASC or DESC)
     */
    public String getSqlKeyword() {
        return sqlKeyword;
    }
    
    @Override
    public String toString() {
        return sqlKeyword;
    }
} 
package org.ruitx.jaws.examples;

import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.types.SortDirection;

import java.util.Optional;

/**
 * Example demonstrating how to use the pagination features in Mimir.
 * This class shows various pagination scenarios and patterns.
 */
public class PaginationExample {
    
    private final Mimir db;
    
    public PaginationExample() {
        this.db = new Mimir();
    }
    
    /**
     * Demonstrates basic pagination without sorting.
     */
    public void basicPaginationExample() {
        System.out.println("=== Basic Pagination Example ===");
        
        // Create a page request for the first page with 10 items
        PageRequest pageRequest = new PageRequest(0, 10);
        
        // Get the first page of users
        Page<Row> userPage = db.getPage("SELECT * FROM USER", pageRequest);
        
        // Display pagination metadata
        System.out.println("Current page: " + userPage.getCurrentPage());
        System.out.println("Page size: " + userPage.getPageSize());
        System.out.println("Total elements: " + userPage.getTotalElements());
        System.out.println("Total pages: " + userPage.getTotalPages());
        System.out.println("Is first page: " + userPage.isFirst());
        System.out.println("Is last page: " + userPage.isLast());
        System.out.println("Has next page: " + userPage.hasNext());
        System.out.println("Has previous page: " + userPage.hasPrevious());
        System.out.println("Number of elements in this page: " + userPage.getNumberOfElements());
        
        // Print users from this page
        System.out.println("\nUsers on this page:");
        userPage.getContent().forEach(user -> {
            Optional<String> username = user.getString("user");
            Optional<String> email = user.getString("email");
            System.out.println("- " + username.orElse("N/A") + " (" + email.orElse("N/A") + ")");
        });
    }
    
    /**
     * Demonstrates pagination with sorting.
     */
    public void paginationWithSortingExample() {
        System.out.println("\n=== Pagination with Sorting Example ===");
        
        // Create a page request with sorting by creation date (newest first)
        PageRequest pageRequest = new PageRequest(0, 5, "created_at", SortDirection.DESC);
        
        Page<Row> userPage = db.getPage("SELECT * FROM USER", pageRequest);
        
        System.out.println("Users sorted by creation date (newest first):");
        userPage.getContent().forEach(user -> {
            Optional<String> username = user.getString("user");
            Optional<Long> createdAt = user.getLong("created_at");
            System.out.println("- " + username.orElse("N/A") + " (created: " + createdAt.orElse(0L) + ")");
        });
    }
    
    /**
     * Demonstrates pagination with filtering.
     */
    public void paginationWithFilteringExample() {
        System.out.println("\n=== Pagination with Filtering Example ===");
        
        // Create a page request for active users
        PageRequest pageRequest = new PageRequest(0, 10, "user", SortDirection.ASC);
        
        // Note: This assumes you have an 'active' column in your USER table
        // Modify the SQL according to your actual schema
        Page<Row> activePage = db.getPage(
            "SELECT * FROM USER WHERE email LIKE ?", 
            pageRequest, 
            "%@%"  // Filter for users with email addresses
        );
        
        System.out.println("Active users (page " + activePage.getCurrentPage() + " of " + activePage.getTotalPages() + "):");
        activePage.getContent().forEach(user -> {
            System.out.println("- " + user.getString("user").orElse("N/A"));
        });
    }
    
    /**
     * Demonstrates navigating through pages.
     */
    public void pageNavigationExample() {
        System.out.println("\n=== Page Navigation Example ===");
        
        PageRequest firstPage = new PageRequest(0, 3);
        Page<Row> currentPage = db.getPage("SELECT * FROM USER", firstPage);
        
        System.out.println("Starting navigation from page " + currentPage.getCurrentPage());
        
        // Navigate through pages
        int pageNum = 0;
        while (pageNum < Math.min(currentPage.getTotalPages(), 3)) { // Limit to first 3 pages
            System.out.println("\n--- Page " + pageNum + " ---");
            System.out.println("Elements on this page: " + currentPage.getNumberOfElements());
            
            currentPage.getContent().forEach(user -> {
                System.out.println("- " + user.getString("user").orElse("N/A"));
            });
            
            if (currentPage.hasNext()) {
                PageRequest nextPageRequest = currentPage.getNextPageRequest();
                currentPage = db.getPage("SELECT * FROM USER", nextPageRequest);
                pageNum++;
            } else {
                break;
            }
        }
    }
    
    /**
     * Demonstrates mapping Page<Row> to a custom type.
     */
    public void pageTransformationExample() {
        System.out.println("\n=== Page Transformation Example ===");
        
        PageRequest pageRequest = new PageRequest(0, 5);
        
        // Get a page of users and transform to UserSummary objects
        Page<UserSummary> userSummaries = db.getPage(
            "SELECT * FROM USER", 
            pageRequest,
            this::mapToUserSummary  // Method reference to mapper function
        );
        
        System.out.println("User summaries (page " + userSummaries.getCurrentPage() + "):");
        userSummaries.getContent().forEach(summary -> {
            System.out.println("- " + summary);
        });
        
        // Alternative: Use the map method to transform an existing page
        Page<Row> rawPage = db.getPage("SELECT * FROM USER", pageRequest);
        Page<String> usernamesPage = rawPage.map(row -> row.getString("user").orElse("Unknown"));
        
        System.out.println("\nJust usernames:");
        usernamesPage.getContent().forEach(username -> {
            System.out.println("- " + username);
        });
    }
    
    /**
     * Example mapper function to convert Row to UserSummary.
     */
    private UserSummary mapToUserSummary(Row row) {
        return new UserSummary(
            row.getString("user").orElse("Unknown"),
            row.getString("email").orElse("No email"),
            row.getLong("created_at").orElse(0L)
        );
    }
    
    /**
     * Simple record for demonstrating page transformation.
     */
    public record UserSummary(String username, String email, long createdAt) {
        @Override
        public String toString() {
            return String.format("%s <%s> (created: %d)", username, email, createdAt);
        }
    }
    
    /**
     * Demonstrates handling empty results.
     */
    public void emptyResultsExample() {
        System.out.println("\n=== Empty Results Example ===");
        
        PageRequest pageRequest = new PageRequest(0, 10);
        
        // Query that likely returns no results
        Page<Row> emptyPage = db.getPage(
            "SELECT * FROM USER WHERE user = ?", 
            pageRequest, 
            "nonexistent_user"
        );
        
        System.out.println("Total elements: " + emptyPage.getTotalElements());
        System.out.println("Is empty: " + emptyPage.isEmpty());
        System.out.println("Total pages: " + emptyPage.getTotalPages());
        
        if (emptyPage.isEmpty()) {
            System.out.println("No users found matching the criteria.");
        }
    }
    
    /**
     * Run all examples.
     */
    public void runAllExamples() {
        try {
            // Make sure database is initialized
            db.initializeDatabase(null);
            
            basicPaginationExample();
            paginationWithSortingExample();
            paginationWithFilteringExample();
            pageNavigationExample();
            pageTransformationExample();
            emptyResultsExample();
            
        } catch (Exception e) {
            System.err.println("Error running pagination examples: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Main method to run the examples.
     */
    public static void main(String[] args) {
        PaginationExample example = new PaginationExample();
        example.runAllExamples();
    }
} 
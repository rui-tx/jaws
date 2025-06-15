package org.ruitx.jaws.types;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Page represents a paginated result set with metadata for frontend consumption.
 * Contains the actual data along with pagination information like total pages, 
 * first/last page indicators, etc.
 * 
 * @param <T> the type of elements in this page
 */
public class Page<T> {
    
    @JsonProperty("content")
    private final List<T> content;
    
    @JsonProperty("pageRequest")
    private final PageRequest pageRequest;
    
    @JsonProperty("totalElements")
    private final long totalElements;
    
    @JsonProperty("totalPages")
    private final int totalPages;
    
    @JsonProperty("currentPage")
    private final int currentPage;
    
    @JsonProperty("pageSize")
    private final int pageSize;
    
    @JsonProperty("numberOfElements")
    private final int numberOfElements;
    
    @JsonProperty("isFirst")
    private final boolean isFirst;
    
    @JsonProperty("isLast")
    private final boolean isLast;
    
    @JsonProperty("hasNext")
    private final boolean hasNext;
    
    @JsonProperty("hasPrevious")
    private final boolean hasPrevious;
    
    @JsonProperty("isEmpty")
    private final boolean isEmpty;
    
    /**
     * Constructor for creating a Page with all metadata.
     * 
     * @param content the list of elements in this page
     * @param pageRequest the original page request
     * @param totalElements total number of elements across all pages
     */
    public Page(List<T> content, PageRequest pageRequest, long totalElements) {
        this.content = content != null ? List.copyOf(content) : List.of();
        this.pageRequest = pageRequest;
        this.totalElements = totalElements;
        this.numberOfElements = this.content.size();
        this.currentPage = pageRequest.page();
        this.pageSize = pageRequest.size();
        this.isEmpty = this.content.isEmpty();
        
        // Calculate total pages (avoid division by zero)
        this.totalPages = pageRequest.size() > 0 ? 
            (int) Math.ceil((double) totalElements / pageRequest.size()) : 0;
        
        // Calculate navigation flags
        this.isFirst = pageRequest.page() == 0;
        this.isLast = pageRequest.page() >= (totalPages - 1) || totalPages == 0;
        this.hasNext = !isLast && totalElements > 0;
        this.hasPrevious = !isFirst;
    }
    
    /**
     * Get the content of this page.
     * 
     * @return immutable list of elements
     */
    public List<T> getContent() {
        return content;
    }
    
    /**
     * Get the original page request that generated this page.
     * 
     * @return the page request
     */
    public PageRequest getPageRequest() {
        return pageRequest;
    }
    
    /**
     * Get the total number of elements across all pages.
     * 
     * @return total elements count
     */
    public long getTotalElements() {
        return totalElements;
    }
    
    /**
     * Get the total number of pages.
     * 
     * @return total pages count
     */
    public int getTotalPages() {
        return totalPages;
    }
    
    /**
     * Get the current page number (0-based).
     * 
     * @return current page number
     */
    public int getCurrentPage() {
        return currentPage;
    }
    
    /**
     * Get the size of this page (max elements per page).
     * 
     * @return page size
     */
    public int getPageSize() {
        return pageSize;
    }
    
    /**
     * Get the actual number of elements in this page.
     * 
     * @return number of elements in this page
     */
    public int getNumberOfElements() {
        return numberOfElements;
    }
    
    /**
     * Check if this is the first page.
     * 
     * @return true if this is the first page (page 0)
     */
    public boolean isFirst() {
        return isFirst;
    }
    
    /**
     * Check if this is the last page.
     * 
     * @return true if this is the last page
     */
    public boolean isLast() {
        return isLast;
    }
    
    /**
     * Check if there is a next page.
     * 
     * @return true if there is a next page
     */
    public boolean hasNext() {
        return hasNext;
    }
    
    /**
     * Check if there is a previous page.
     * 
     * @return true if there is a previous page
     */
    public boolean hasPrevious() {
        return hasPrevious;
    }
    
    /**
     * Check if this page is empty.
     * 
     * @return true if this page contains no elements
     */
    public boolean isEmpty() {
        return isEmpty;
    }
    
    /**
     * Transform this page's content to a different type.
     * Useful for converting Page<Row> to Page<SomeModel>.
     * 
     * @param <U> the target type
     * @param mapper function to transform each element
     * @return new Page with transformed content
     */
    public <U> Page<U> map(Function<T, U> mapper) {
        List<U> transformedContent = content.stream()
            .map(mapper)
            .collect(Collectors.toList());
        return new Page<>(transformedContent, pageRequest, totalElements);
    }
    
    /**
     * Get a PageRequest for the next page.
     * 
     * @return PageRequest for the next page, or empty if this is the last page
     */
    public PageRequest getNextPageRequest() {
        if (!hasNext) {
            throw new IllegalStateException("No next page available");
        }
        return pageRequest.nextPage();
    }
    
    /**
     * Get a PageRequest for the previous page.
     * 
     * @return PageRequest for the previous page, or empty if this is the first page
     */
    public PageRequest getPreviousPageRequest() {
        if (!hasPrevious) {
            throw new IllegalStateException("No previous page available");
        }
        return pageRequest.previousPage();
    }
    
    /**
     * Create an empty page.
     * 
     * @param <T> the type of elements
     * @param pageRequest the page request
     * @return empty page
     */
    public static <T> Page<T> empty(PageRequest pageRequest) {
        return new Page<>(List.of(), pageRequest, 0);
    }
    
    @Override
    public String toString() {
        return String.format("Page{page=%d, size=%d, totalElements=%d, totalPages=%d, content.size=%d}", 
            currentPage, pageSize, totalElements, totalPages, numberOfElements);
    }
} 
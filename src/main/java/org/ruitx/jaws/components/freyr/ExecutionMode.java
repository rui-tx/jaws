package org.ruitx.jaws.components.freyr;

/**
 * ExecutionMode - Defines how jobs should be processed
 * 
 * PARALLEL: Jobs are processed concurrently by multiple worker threads (default behavior)
 * SEQUENTIAL: Jobs are processed one at a time in FIFO order by a single worker thread
 */
public enum ExecutionMode {
    
    /**
     * Process jobs in parallel using multiple worker threads.
     */
    PARALLEL,
    
    /**
     * Process jobs sequentially using a single worker thread.
     * Jobs are processed one at a time in the order they were submitted.
     */
    SEQUENTIAL;
    
    /**
     * Default execution mode
     */
    public static final ExecutionMode DEFAULT = PARALLEL;
} 
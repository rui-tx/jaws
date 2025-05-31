package org.ruitx.jaws.interfaces;

/**
 * Represents a chain of middleware that can be executed sequentially.
 * Each middleware in the chain can decide whether to continue processing or stop.
 */
public interface MiddlewareChain {
    
    /**
     * Continue execution to the next middleware in the chain.
     * 
     * @return true if the chain should continue, false to stop processing
     */
    boolean next();
    
    /**
     * Check if there are more middleware to execute in the chain.
     * 
     * @return true if there are more middleware, false otherwise
     */
    boolean hasNext();
} 
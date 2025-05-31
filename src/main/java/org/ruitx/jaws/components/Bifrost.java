package org.ruitx.jaws.components;

import org.ruitx.jaws.interfaces.Middleware;
import org.ruitx.jaws.interfaces.MiddlewareChain;

import java.util.List;

/**
 * Implementation of MiddlewareChain that manages the sequential execution of middleware.
 */
public class Bifrost implements MiddlewareChain {
    
    private final List<Middleware> middlewares;
    private final Yggdrasill.RequestContext context;
    private int currentIndex = 0;
    
    public Bifrost(List<Middleware> middlewares, Yggdrasill.RequestContext context) {
        this.middlewares = middlewares;
        this.context = context;
    }
    
    @Override
    public boolean next() {
        if (currentIndex < middlewares.size()) {
            Middleware middleware = middlewares.get(currentIndex++);
            return middleware.handle(context, this);
        }
        return true; // No more middleware, continue to route handling
    }
    
    @Override
    public boolean hasNext() {
        return currentIndex < middlewares.size();
    }
    
    /**
     * Start the middleware chain execution.
     * 
     * @return true if all middleware passed and request should continue to route handling
     */
    public boolean execute() {
        currentIndex = 0;
        return next();
    }
} 
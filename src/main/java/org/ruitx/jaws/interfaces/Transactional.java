package org.ruitx.jaws.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Transactional {
    /**
     * The isolation level for the transaction.
     * Defaults to READ_UNCOMMITTED.
     */
    IsolationLevel isolation() default IsolationLevel.SERIALIZABLE;

    /**
     * Whether to read-only transaction.
     * Defaults to false.
     */
    boolean readOnly() default false;

    /**
     * The timeout in seconds for the transaction.
     * Defaults to -1 (no timeout).
     */
    int timeout() default -1;
} 
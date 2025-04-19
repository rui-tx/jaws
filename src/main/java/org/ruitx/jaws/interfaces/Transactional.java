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
     * Defaults to SERIALIZABLE.
     */
    IsolationLevel isolation() default IsolationLevel.SERIALIZABLE;

    /**
     * Whether to read-only transaction.
     * Defaults to false.
     */
    boolean readOnly() default false;
} 
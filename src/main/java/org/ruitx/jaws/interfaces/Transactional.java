package org.ruitx.jaws.interfaces;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to indicate that a method should be executed within a transaction.
 * The transaction can be configured with different isolation levels and read-only status.
 */
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
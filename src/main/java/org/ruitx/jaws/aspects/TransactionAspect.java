package org.ruitx.jaws.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.IsolationLevel;
import org.ruitx.jaws.interfaces.Transactional;
import org.ruitx.jaws.utils.JawsLogger;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;

@Aspect
public class TransactionAspect {
    @Pointcut("execution(* org.ruitx.www..*(..))")
    public void apiMethods() {
    }

    /**
     * Manage a transaction.
     * 
     * @param joinPoint The join point
     * @return The result of the method
     * @throws Throwable If the method throws an exception
     */
    @Around("apiMethods() && @annotation(org.ruitx.jaws.interfaces.Transactional)")
    public Object manageTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        Method method = ((MethodSignature) joinPoint.getSignature()).getMethod();
        Transactional transactional = method.getAnnotation(Transactional.class);

        if (transactional == null) {
            return joinPoint.proceed();
        }

        // Get the Mimir instance from the target object or its fields
        Mimir db = findMimirInstance(joinPoint);
        if (db == null) {
            throw new IllegalStateException("No Mimir instance found in method arguments or class fields");
        }

        try {
            db.beginTransaction();
            Connection conn = db.getConnection();
            setTransactionIsolation(conn, transactional.isolation());

            Object result = joinPoint.proceed();

            if (!transactional.readOnly()) {
                db.commitTransaction();
            } else {
                db.rollbackTransaction();
            }

            return result;
        } catch (Throwable e) {
            try {
                db.rollbackTransaction();
            } catch (SQLException ex) {
                JawsLogger.error("Error during rollback: {}", ex.getMessage());
            }
            throw e;
        }
    }

    /**
     * Find the Mimir instance in the method arguments or fields of the target object.
     * 
     * @param joinPoint The join point
     * @return The Mimir instance or null if not found
     */
    private Mimir findMimirInstance(ProceedingJoinPoint joinPoint) {
        // First check method arguments
        for (Object arg : joinPoint.getArgs()) {
            if (arg instanceof Mimir) {
                return (Mimir) arg;
            }
        }

        // Then check target object fields
        Object target = joinPoint.getTarget();
        for (java.lang.reflect.Field field : target.getClass().getDeclaredFields()) {
            if (Mimir.class.isAssignableFrom(field.getType())) {
                try {
                    field.setAccessible(true);
                    return (Mimir) field.get(target);
                } catch (IllegalAccessException e) {
                    JawsLogger.error("Failed to access Mimir field: {}", e.getMessage());
                }
            }
        }

        return null;
    }

    /**
     * Set the transaction isolation level.
     * 
     * @param conn The connection
     * @param level The isolation level
     * @throws SQLException If the transaction isolation level fails
     */
    private void setTransactionIsolation(Connection conn, IsolationLevel level) throws SQLException {
        switch (level) {
            case READ_UNCOMMITTED:
                conn.setTransactionIsolation(Connection.TRANSACTION_READ_UNCOMMITTED);
                break;
            case SERIALIZABLE:
                conn.setTransactionIsolation(Connection.TRANSACTION_SERIALIZABLE);
                break;
        }
    }
} 
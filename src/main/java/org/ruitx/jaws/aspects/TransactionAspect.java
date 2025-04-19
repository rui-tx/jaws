package org.ruitx.jaws.aspects;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.ruitx.jaws.components.Mimir;
import org.ruitx.jaws.interfaces.IsolationLevel;
import org.ruitx.jaws.interfaces.Transactional;
import org.tinylog.Logger;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

@Aspect
public class TransactionAspect {

    @Around("@annotation(org.ruitx.jaws.interfaces.Transactional)")
    public Object manageTransaction(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Transactional transactional = method.getAnnotation(Transactional.class);

        Logger.info("Starting transaction for method: {}", method.getName());

        // Get the Mimir instance from the target object or its fields
        Mimir db = findMimirInstance(joinPoint);
        if (db == null) {
            throw new IllegalStateException("No Mimir instance found in method arguments or class fields");
        }

        Connection conn = null;
        try {
            // Begin transaction
            db.beginTransaction();
            conn = db.getConnection();
            Logger.info("Transaction begun with isolation level: {}", transactional.isolation());
            
            // Set isolation level
            setTransactionIsolation(conn, transactional.isolation());

            // Set timeout if specified
            if (transactional.timeout() > 0) {
                conn.setNetworkTimeout(null, (int) TimeUnit.SECONDS.toMillis(transactional.timeout()));
            }

            // Execute the method
            Object result = joinPoint.proceed();

            // Commit if successful and not read-only
            if (!transactional.readOnly()) {
                db.commitTransaction();
                Logger.info("Transaction committed successfully");
            } else {
                db.rollbackTransaction();
                Logger.info("Read-only transaction completed");
            }

            return result;
        } catch (Throwable e) {
            Logger.error("Transaction failed: {}", e.getMessage());
            // Rollback on error
            try {
                if (!transactional.readOnly() && conn != null) {
                    db.rollbackTransaction();
                    Logger.info("Transaction rolled back due to error");
                }
            } catch (SQLException rollbackEx) {
                Logger.error("Failed to rollback transaction: {}", rollbackEx.getMessage());
            }
            throw e;
        }
    }

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
                    Logger.error("Failed to access Mimir field: {}", e.getMessage());
                }
            }
        }

        return null;
    }

    private void setTransactionIsolation(Connection conn, IsolationLevel level) throws SQLException {
        // SQLite only supports SERIALIZABLE and READ_UNCOMMITTED
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
package org.ruitx.jaws.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * Utility class for Jakarta Bean Validation.
 * Provides methods to validate DTOs and convert validation errors to user-friendly messages.
 */
public class ValidationUtils {
    
    private static final ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    private static final Validator validator = factory.getValidator();
    
    /**
     * Validates an object using Jakarta Bean Validation annotations.
     * 
     * @param object the object to validate
     * @param <T> the type of the object
     * @return APIResponse with validation errors if any, or null if validation passes
     */
    public static <T> APIResponse<String> validate(T object) {
        if (object == null) {
            return APIResponse.error(
                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                "Request body cannot be null"
            );
        }
        
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        if (violations.isEmpty()) {
            return null; // Validation passed
        }
        
        // Convert violations to user-friendly error message
        String errorMessage = violations.stream()
            .map(violation -> {
                String fieldName = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                return fieldName + ": " + message;
            })
            .collect(Collectors.joining("; "));
        
        return APIResponse.error(
            ResponseCode.BAD_REQUEST.getCodeAndMessage(),
            "Validation failed: " + errorMessage
        );
    }
    
    /**
     * Validates an object and returns a detailed validation report.
     * 
     * @param object the object to validate
     * @param <T> the type of the object
     * @return ValidationResult with detailed information
     */
    public static <T> ValidationResult<T> validateDetailed(T object) {
        if (object == null) {
            return new ValidationResult<>(false, "Request body cannot be null", Set.of());
        }
        
        Set<ConstraintViolation<T>> violations = validator.validate(object);
        
        if (violations.isEmpty()) {
            return new ValidationResult<>(true, null, Set.of());
        }
        
        String errorMessage = violations.stream()
            .map(violation -> {
                String fieldName = violation.getPropertyPath().toString();
                String message = violation.getMessage();
                Object invalidValue = violation.getInvalidValue();
                return String.format("%s: %s (received: %s)", fieldName, message, invalidValue);
            })
            .collect(Collectors.joining("; "));
        
        return new ValidationResult<>(false, errorMessage, violations);
    }
    
    /**
     * Result of validation operation.
     */
    public static class ValidationResult<T> {
        private final boolean valid;
        private final String errorMessage;
        private final Set<ConstraintViolation<T>> violations;
        
        public ValidationResult(boolean valid, String errorMessage, Set<ConstraintViolation<T>> violations) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.violations = violations;
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getErrorMessage() {
            return errorMessage;
        }
        
        public Set<ConstraintViolation<T>> getViolations() {
            return violations;
        }
        
        public APIResponse<String> toAPIResponse() {
            if (valid) {
                return null;
            }
            return APIResponse.error(
                ResponseCode.BAD_REQUEST.getCodeAndMessage(),
                "Validation failed: " + errorMessage
            );
        }
    }
} 
package org.ruitx.jaws.interfaces;

import java.util.Optional;

/**
 * Interface for DTOs that need custom validation logic beyond Jakarta Bean Validation.
 * Implementing classes should provide their own validation rules and return validation errors.
 */
public interface Validatable {
    
    /**
     * Performs custom validation on the implementing object.
     * 
     * @return Optional.empty() if validation passes, 
     *         Optional containing error message if validation fails
     */
    Optional<String> isValid();
} 
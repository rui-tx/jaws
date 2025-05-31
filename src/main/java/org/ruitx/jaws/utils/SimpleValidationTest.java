package org.ruitx.jaws.utils;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.ruitx.www.dto.PasteCreateRequest;

import java.util.Set;

/**
 * Simple standalone validation performance test
 */
public class SimpleValidationTest {
    
    public static void main(String[] args) {
        System.out.println("🚀 Jakarta Bean Validation Performance Test\n");
        
        // Initialize validator (one-time cost)
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        Validator validator = factory.getValidator();
        
        // Create test objects
        PasteCreateRequest validRequest = new PasteCreateRequest(
            "This is a test paste content", "My Title", "javascript", 48, false, "password123"
        );
        
        PasteCreateRequest invalidRequest = new PasteCreateRequest(
            "", "This title is way too long and exceeds the 200 character limit that we have set in our validation rules", 
            "this-language-name-is-definitely-too-long-to-be-valid", -5, null, null
        );
        
        // Warm up
        System.out.println("🔥 Warming up...");
        for (int i = 0; i < 1000; i++) {
            validator.validate(validRequest);
            validator.validate(invalidRequest);
        }
        System.out.println("✅ Warm-up complete\n");
        
        // Test performance
        int iterations = 100_000;
        
        // Test valid objects
        long startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Set<ConstraintViolation<PasteCreateRequest>> violations = validator.validate(validRequest);
        }
        long endTime = System.nanoTime();
        
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgTimeMicros = (endTime - startTime) / 1_000.0 / iterations;
        
        System.out.printf("✅ Valid Objects: Validated %,d objects in %,d ms\n", iterations, totalTimeMs);
        System.out.printf("✅ Average time: %.2f microseconds per validation\n", avgTimeMicros);
        System.out.printf("✅ Throughput: %,.0f validations/second\n\n", iterations * 1000.0 / totalTimeMs);
        
        // Test invalid objects (more work - finding violations)
        startTime = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            Set<ConstraintViolation<PasteCreateRequest>> violations = validator.validate(invalidRequest);
        }
        endTime = System.nanoTime();
        
        totalTimeMs = (endTime - startTime) / 1_000_000;
        avgTimeMicros = (endTime - startTime) / 1_000.0 / iterations;
        
        System.out.printf("❌ Invalid Objects: Validated %,d objects in %,d ms\n", iterations, totalTimeMs);
        System.out.printf("❌ Average time: %.2f microseconds per validation\n", avgTimeMicros);
        System.out.printf("❌ Throughput: %,.0f validations/second\n\n", iterations * 1000.0 / totalTimeMs);
        
        // Show actual violations for the invalid object
        Set<ConstraintViolation<PasteCreateRequest>> violations = validator.validate(invalidRequest);
        System.out.printf("🔍 Found %d violations in invalid object:\n", violations.size());
        for (ConstraintViolation<PasteCreateRequest> violation : violations) {
            System.out.printf("   • %s: %s\n", violation.getPropertyPath(), violation.getMessage());
        }
        
        System.out.println("\n🎯 Conclusion: Jakarta Bean Validation is FAST! 🚀");
    }
} 
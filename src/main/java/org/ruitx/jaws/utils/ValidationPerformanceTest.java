package org.ruitx.jaws.utils;

import org.ruitx.www.dto.PasteCreateRequest;

/**
 * Simple performance test for Jakarta Bean Validation
 * Run this to see validation performance metrics
 */
public class ValidationPerformanceTest {
    
    public static void main(String[] args) {
        // Warm up the validator (one-time cost)
        warmUp();
        
        // Test validation performance
        testValidationPerformance();
        
        // Test manual validation performance
        testManualValidationPerformance();
    }
    
    private static void warmUp() {
        System.out.println("🔥 Warming up Jakarta Bean Validation...");
        PasteCreateRequest warmupRequest = new PasteCreateRequest(
            "test content", "title", "java", 24, false, null
        );
        ValidationUtils.validate(warmupRequest);
        System.out.println("✅ Warm-up complete\n");
    }
    
    private static void testValidationPerformance() {
        System.out.println("⏱️  Testing Jakarta Bean Validation Performance");
        
        int iterations = 100_000;
        PasteCreateRequest validRequest = new PasteCreateRequest(
            "This is a test paste content", "My Title", "javascript", 48, false, "password123"
        );
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            ValidationUtils.validate(validRequest);
        }
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgTimeUs = (endTime - startTime) / 1_000.0 / iterations;
        
        System.out.printf("✅ Validated %,d objects in %,d ms\n", iterations, totalTimeMs);
        System.out.printf("✅ Average validation time: %.2f microseconds per object\n", avgTimeUs);
        System.out.printf("✅ Throughput: %,.0f validations/second\n\n", iterations * 1000.0 / totalTimeMs);
    }
    
    private static void testManualValidationPerformance() {
        System.out.println("⏱️  Testing Manual Validation Performance (for comparison)");
        
        int iterations = 100_000;
        PasteCreateRequest request = new PasteCreateRequest(
            "This is a test paste content", "My Title", "javascript", 48, false, "password123"
        );
        
        long startTime = System.nanoTime();
        
        for (int i = 0; i < iterations; i++) {
            manualValidate(request);
        }
        
        long endTime = System.nanoTime();
        long totalTimeMs = (endTime - startTime) / 1_000_000;
        double avgTimeUs = (endTime - startTime) / 1_000.0 / iterations;
        
        System.out.printf("✅ Manually validated %,d objects in %,d ms\n", iterations, totalTimeMs);
        System.out.printf("✅ Average validation time: %.2f microseconds per object\n", avgTimeUs);
        System.out.printf("✅ Throughput: %,.0f validations/second\n", iterations * 1000.0 / totalTimeMs);
    }
    
    private static boolean manualValidate(PasteCreateRequest request) {
        if (request == null) return false;
        if (request.content() == null || request.content().trim().isEmpty()) return false;
        if (request.content().length() > 1_000_000) return false;
        if (request.title() != null && request.title().length() > 200) return false;
        if (request.language() != null && request.language().length() > 50) return false;
        if (request.expiresInHours() != null && (request.expiresInHours() < 1 || request.expiresInHours() > 8760)) return false;
        if (request.password() != null && request.password().length() > 100) return false;
        return true;
    }
} 
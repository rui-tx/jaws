package org.ruitx.www.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;

/**
 * Request DTO for creating a new paste.
 * 
 * Required fields:
 * - content: The actual paste content (cannot be blank)
 * 
 * Optional fields:
 * - title: Optional title for the paste
 * - language: Programming language for syntax highlighting
 * - expiresInHours: Hours until expiration (1-8760 hours = 1 year max)
 * - isPrivate: Whether the paste is private (defaults to false)
 * - password: Optional password protection
 */
public record PasteCreateRequest(
        @JsonProperty("content")
        @NotNull(message = "Content is required")
        @NotBlank(message = "Content cannot be empty")
        @Size(max = 1_000_000, message = "Content cannot exceed 1MB (1,000,000 characters)")
        String content,
        
        @JsonProperty("title")
        @Size(max = 200, message = "Title cannot exceed 200 characters")
        String title,
        
        @JsonProperty("language")
        @Size(max = 50, message = "Language cannot exceed 50 characters")
        String language,
        
        @JsonProperty("expiresInHours")
        @Min(value = 1, message = "Expiration must be at least 1 hour")
        @Max(value = 8760, message = "Expiration cannot exceed 1 year (8760 hours)")
        Integer expiresInHours,
        
        @JsonProperty("isPrivate")
        Boolean isPrivate,
        
        @JsonProperty("password")
        @Size(max = 100, message = "Password cannot exceed 100 characters")
        String password
) {
} 
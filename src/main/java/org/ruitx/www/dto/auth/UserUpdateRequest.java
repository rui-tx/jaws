package org.ruitx.www.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.ruitx.www.validation.Validatable;

import java.time.Instant;
import java.util.Optional;

public record UserUpdateRequest(
    @JsonProperty("password") 
    @Size(min = 8, message = "Password must be at least 8 characters long")
    @Size(max = 16, message = "Password cannot exceed 16 characters")
    String password,

    @JsonProperty("email") 
    @Email(message = "Invalid email address")
    String email,

    @JsonProperty("firstName")
    @Size(max = 100, message = "First name cannot exceed 100 characters")
    String firstName,

    @JsonProperty("lastName") 
    @Size(max = 100, message = "Last name cannot exceed 100 characters")
    String lastName,

    @JsonProperty("birthdate") 
    @Min(value = 0, message = "Birthdate must be a valid Unix timestamp (after 1970)")
    @Max(value = 4102444800L, message = "Birthdate cannot be in the future (after 2100)")
    Long birthdate,

    @JsonProperty("gender")
    @Size(max = 100, message = "Gender cannot exceed 100 characters")
    String gender,

    @JsonProperty("phoneNumber") 
    @Size(max = 16, message = "Phone number cannot exceed 16 characters")
    String phoneNumber,

    @JsonProperty("profilePicture")
    @Size(max = 1024, message = "Profile picture cannot exceed 1024 characters")
    String profilePicture,

    @JsonProperty("bio") 
    @Size(max = 2096, message = "Bio cannot exceed 2096 characters")
    String bio,

    @JsonProperty("location") 
    @Size(max = 1024, message = "Location cannot exceed 1024 characters")
    String location,

    @JsonProperty("website") 
    @Size(max = 1024, message = "Website cannot exceed 1024 characters")
    String website,

    @JsonProperty("isActive") 
    @Min(value = 0, message = "isActive must be 0 or 1")
    @Max(value = 1, message = "isActive must be 0 or 1")
    Integer isActive,

    @JsonProperty("isSuperuser") 
    @Min(value = 0, message = "isSuperuser must be 0 or 1")
    @Max(value = 1, message = "isSuperuser must be 0 or 1")
    Integer isSuperuser,

    @JsonProperty("lockoutUntil") 
    Long lockoutUntil

) implements Validatable {

    @Override
    public Optional<String> isValid() {
        
        // birthdate - ensure it's not in the future
        if (birthdate != null) {
            long currentTimestamp = Instant.now().getEpochSecond();
            if (birthdate > currentTimestamp) {
                return Optional.of("Birthdate cannot be in the future");
            }
        }
        
        // lockoutUntil - ensure it's not in the past if set
        if (lockoutUntil != null) {
            long currentTimestamp = Instant.now().getEpochSecond();
            if (lockoutUntil < currentTimestamp) {
                return Optional.of("Lockout time must be in the future");
            }
        }
        
        return Optional.empty();
    }
}
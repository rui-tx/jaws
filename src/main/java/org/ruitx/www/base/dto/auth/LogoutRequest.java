package org.ruitx.www.base.dto.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Optional;
import org.ruitx.jaws.components.yggdrasill.Validatable;

public record LogoutRequest(
    @JsonProperty("refreshToken")
    @NotNull(message = "Refresh token is required")
    @NotBlank(message = "Refresh token cannot be empty")
    String refreshToken

) implements Validatable {

  @Override
  public Optional<String> isValid() {
    return Optional.empty();
  }
}

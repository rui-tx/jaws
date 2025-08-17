package components.tyr;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ruitx.jaws.configs.ApplicationConfig.JWT_SECRET;

import io.jsonwebtoken.Clock;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Urd;

class TyrTokenExpirationDeterministicTest {

  private Urd urd;
  private Clock clock;

  @BeforeEach
  void setUp() {
    urd = Urd.getInstance();
    urd.resetTime();
    urd.clearSimulationState();
    clock = urd.jwtClock();
  }

  private boolean isValid(String token) {
    try {
      Jwts.parser()
          .verifyWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
          .clock(clock)
          .build()
          .parse(token);
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  @Test
  @DisplayName("Token valid at T0, valid at +5s, expired at +11s (logical time)")
  void tokenExpiresDeterministically() {
    // Arrange: build a token that expires at T0 + 10s using logical time
    String token = Jwts.builder()
        .issuer("JAWS")
        .subject("testUser")
        .issuedAt(clock.now())
        .expiration(new Date(urd.getSimulationTimeMillis() + 10_000))
        .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes()))
        .compact();

    // T0: should be valid
    assertTrue(isValid(token), "Token should be valid at issuance");

    // +5s: still valid
    urd.advanceTime(5_000);
    assertTrue(isValid(token), "Token should still be valid at +5s");

    // +11s: expired
    urd.advanceTime(6_000);
    assertFalse(isValid(token), "Token should be expired at +11s");
  }
}

package org.ruitx.jaws.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;
import java.time.Instant;

/**
 * TimestampDeserializer - Custom deserializer for handling both epoch seconds and ISO 8601 date
 * enums.
 * <p>
 * This deserializer attempts to parse a timestamp from either a numeric string (epoch seconds) or
 * an ISO 8601 formatted date string. If both parsing attempts fail, it throws an IOException.
 */
public class TimestampDeserializer extends JsonDeserializer<Long> {

  @Override
  public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
    String dateStr = p.getValueAsString();
    try {
      // Try parsing as epoch seconds first
      return Long.parseLong(dateStr);
    } catch (NumberFormatException e) {
      try {
        // If that fails, try parsing as ISO 8601 string
        return Instant.parse(dateStr).getEpochSecond();
      } catch (Exception ex) {
        throw new IOException("Unable to parse timestamp: " + dateStr, ex);
      }
    }
  }
}
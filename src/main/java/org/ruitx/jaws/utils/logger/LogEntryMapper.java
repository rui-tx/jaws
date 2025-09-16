package org.ruitx.jaws.utils.logger;

import java.util.Optional;
import org.ruitx.jaws.components.mimir.RowMapper;
import org.ruitx.jaws.types.Row;

/**
 * Authoritative Row -> LogEntry mapper.
 */
public final class LogEntryMapper implements RowMapper<LogEntry> {

  public static final LogEntryMapper INSTANCE = new LogEntryMapper();

  private LogEntryMapper() {
  }

  @Override
  public LogEntry map(Row row) {
    if (row == null) {
      return null;
    }

    Optional<Integer> id = row.getInt("id");
    Optional<Long> timestamp = row.getUnixTimestamp("timestamp");
    Optional<String> level = row.getString("level");
    Optional<String> logger = row.getString("logger");
    Optional<String> thread = row.getString("thread");
    Optional<String> message = row.getString("message");
    Optional<String> exception = row.getString("exception");
    Optional<String> method = row.getString("method");
    Optional<Integer> lineNumber = row.getInt("line_number");
    Optional<String> traceId = row.getString("trace_id");

    if (id.isEmpty()) {
      return null; // Essential fields must be present
    }

    return new LogEntry(
        id.orElse(null),
        timestamp.orElse(0L),
        level.orElse(null),
        logger.orElse(null),
        thread.orElse(null),
        message.orElse(null),
        exception.orElse(null),
        method.orElse(null),
        lineNumber.orElse(0),
        traceId.orElse(null));
  }
}

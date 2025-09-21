package org.ruitx.jaws.models;

import java.util.Optional;
import org.ruitx.jaws.components.mimir.Row;
import org.ruitx.jaws.components.mimir.RowMapper;
import org.ruitx.jaws.models.JobResults.Columns;

public final class JobResultsMapper implements RowMapper<JobResults> {

  public static final JobResultsMapper INSTANCE = new JobResultsMapper();

  @Override
  public JobResults map(Row r) {
    if (r == null) {
      return null;
    }

    Optional<String> id = r.getString(Columns.ID);
    Optional<String> jobId = r.getString(Columns.JOBID);
    Optional<Integer> statusCode = r.getInt(Columns.STATUSCODE);
    Optional<String> headers = r.getString(Columns.HEADERS);
    Optional<String> body = r.getString(Columns.BODY);
    Optional<String> contentType = r.getString(Columns.CONTENTTYPE);
    Optional<Integer> createdAt = r.getInt(Columns.CREATEDAT);
    Optional<Integer> expiresAt = r.getInt(Columns.EXPIRESAT);

    if (id.isEmpty() || jobId.isEmpty()) {
      return null; // Essential fields must be present
    }

    return new JobResults(
        id.get(),
        jobId.get(),
        statusCode.orElse(null),
        headers.orElse(null),
        body.orElse(null),
        contentType.orElse(null),
        createdAt.orElse(null),
        expiresAt.orElse(null)
    );
  }
}

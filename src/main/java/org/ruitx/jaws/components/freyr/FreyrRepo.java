package org.ruitx.jaws.components.freyr;

import java.time.Instant;
import java.util.Optional;
import org.ruitx.jaws.components.mimir.Mimir;
import org.ruitx.jaws.components.mimir.Repository;
import org.ruitx.jaws.components.mimir.Row;
import org.ruitx.jaws.models.JobResults;
import org.ruitx.jaws.models.JobResultsMapper;

public class FreyrRepo extends Repository {

  protected FreyrRepo(Mimir db) {
    super(db);
  }

  protected Optional<Row> getJobStatus(String jobId) {
    Optional<Row> row = db.getRow("SELECT status FROM JOBS WHERE id = ?", jobId);
    return row;
  }

  protected Optional<JobResults> getJobResult(String jobId) {
    Optional<JobResults> row = getOne(
        "SELECT * FROM JOB_RESULTS WHERE job_id = ? AND expires_at > ?",
        JobResultsMapper.INSTANCE,
        jobId,
        Instant.now().toEpochMilli());

    return row;
  }
}

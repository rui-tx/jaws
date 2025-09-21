package org.ruitx.jaws.models;

public record JobResults(
    String id,
    String jobId, // Fk to JOBS table
    Integer statusCode,
    String headers,
    String body,
    String contentType,
    Integer createdAt,
    Integer expiresAt
) {

  public static class Columns {

    public static final String ID = "id";
    public static final String JOBID = "job_id";
    public static final String STATUSCODE = "status_code";
    public static final String HEADERS = "headers";
    public static final String BODY = "body";
    public static final String CONTENTTYPE = "content_type";
    public static final String CREATEDAT = "created_at";
    public static final String EXPIRESAT = "expires_at";
  }
}

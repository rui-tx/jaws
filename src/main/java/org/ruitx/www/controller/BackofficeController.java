package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.JawsUtils;
import org.ruitx.www.dto.auth.UserCreateRequest;
import org.ruitx.www.dto.auth.UserUpdateRequest;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.repository.AuthRepo;
import org.ruitx.www.service.AuthService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.RequestType.PATCH;
import static org.ruitx.jaws.strings.ResponseCode.*;

public class BackofficeController extends Bragi {

    private static final String BASE_HTML_PATH = "backoffice/index.html";
    private static final String BODY_HTML_PATH = "backoffice/_body.html";
    private static final String DASHBOARD_PAGE = "backoffice/partials/dashboard.html";
    private static final String SETTINGS_PAGE = "backoffice/partials/settings.html";
    private static final String USERS_PAGE = "backoffice/partials/users.html";
    private static final String USER_PROFILE_PAGE = "backoffice/partials/profile.html";
    private static final String JOBS_PAGE = "backoffice/partials/jobs.html";
    private static final String JOB_DETAILS_PAGE = "backoffice/partials/job-details.html";
    private static final Logger log = LoggerFactory.getLogger(BackofficeController.class);

    private final AuthRepo authRepo;
    private final AuthService authService;
    private final Freyr jobQueue;

    public BackofficeController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.authRepo = new AuthRepo();
        this.authService = new AuthService();
        this.jobQueue = Freyr.getInstance();
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice", method = GET)
    public void renderIndex() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, DASHBOARD_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/settings", method = GET)
    public void renderSettings() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, SETTINGS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/users", method = GET)
    public void renderUsers() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, USERS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/users", method = POST)
    public void listUsersHTMX(UserCreateRequest request) {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();
        if (!user.user().equals("admin")) {
            sendHTMLResponse(FORBIDDEN, "You are not authorized to create users");
            return;
        }

        APIResponse<String> response = authService.createUser(request);
        sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/profile/:id", method = GET)
    public void renderUserProfile() {
        String userId = getPathParam("id");
        User currentUser = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();
        User user = authRepo.getUserById(Long.parseLong(userId)).get();

        Map<String, String> context = new HashMap<>();
        context.put("currentUser",
                getCurrentToken().isEmpty() ? "-" : currentUser.firstName() + " " + currentUser.lastName());
        context.put("profilePicture", currentUser.profilePicture() != null && !currentUser.profilePicture().isEmpty()
                ? currentUser.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");

        context.put("userId", userId);
        context.put("username", user.user());
        context.put("userEmail", user.email() == null ? "" : user.email());
        context.put("userFirstName", user.firstName() == null ? "" : user.firstName());
        context.put("userLastName", user.lastName() == null ? "" : user.lastName());
        context.put("phoneNumber", user.phoneNumber() == null ? "" : user.phoneNumber());
        context.put("birthdate",
                user.birthdate() == null ? "" : JawsUtils.formatUnixTimestamp(user.birthdate(), "yyyy-MM-dd"));
        context.put("gender", user.gender() == null ? "" : user.gender());
        context.put("location", user.location() == null ? "" : user.location());
        context.put("website", user.website() == null ? "" : user.website());
        context.put("bio", user.bio() == null ? "" : user.bio());
        context.put("userProfilePicture", user.profilePicture() == null ? "" : user.profilePicture());
        context.put("createdAt", JawsUtils.formatUnixTimestamp(user.createdAt()));
        context.put("updatedAt", user.updatedAt() == null ? "" : JawsUtils.formatUnixTimestamp(user.updatedAt()));
        context.put("lastLogin", user.lastLogin() == null
                ? "Never logged in"
                : JawsUtils.formatUnixTimestamp(user.lastLogin(), "yyyy-MM-dd HH:mm:ss"));
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, USER_PROFILE_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/profile/:id", method = PATCH)
    public void updateUserProfile(UserUpdateRequest request) {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        if (!user.user().equals("admin")) {
            sendHTMLResponse(FORBIDDEN, "You are not authorized to create users");
            return;
        }
        APIResponse<String> response = authService
                .updateUser(Integer.parseInt(getPathParam("id")), request);
        sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/jobs", method = GET)
    public void renderJobs() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, JOBS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/jobs/:id", method = GET)
    public void renderJobDetails() {
        String jobId = getPathParam("id");
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        // Get job details from database
        org.ruitx.jaws.types.Row jobRow = new org.ruitx.jaws.components.Mimir().getRow("SELECT * FROM JOBS WHERE id = ?", jobId);
        if (jobRow == null) {
            sendHTMLResponse(NOT_FOUND, "Job not found");
            return;
        }

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        
        context.put("jobId", jobId);
        context.put("jobType", jobRow.getString("type").orElse("Unknown"));
        context.put("jobStatus", jobRow.getString("status").orElse("UNKNOWN"));
        context.put("jobPriority", jobRow.getInt("priority").orElse(5).toString());
        context.put("jobMaxRetries", jobRow.getInt("max_retries").orElse(3).toString());
        context.put("jobCurrentRetries", jobRow.getInt("current_retries").orElse(0).toString());
        context.put("jobTimeoutMs", jobRow.getLong("timeout_ms").orElse(30000L).toString());
        context.put("jobExecutionMode", jobRow.getString("execution_mode").orElse("PARALLEL"));
        context.put("jobPayload", jobRow.getString("payload").orElse("{}"));
        context.put("jobErrorMessage", jobRow.getString("error_message").orElse(""));
        context.put("jobClientId", jobRow.getString("client_id").orElse(""));
        context.put("jobUserId", jobRow.getString("user_id").orElse(""));
        context.put("jobCreatedAt", jobRow.getLong("created_at").map(ts -> JawsUtils.formatUnixTimestamp(ts)).orElse(""));
        context.put("jobStartedAt", jobRow.getLong("started_at").map(ts -> JawsUtils.formatUnixTimestamp(ts)).orElse(""));
        context.put("jobCompletedAt", jobRow.getLong("completed_at").map(ts -> JawsUtils.formatUnixTimestamp(ts)).orElse(""));
        
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, JOB_DETAILS_PAGE));
    }

    @AccessControl(login = true)
    @Route(endpoint = "/htmx/backoffice/jobs", method = GET)
    public void listJobsHTMX() {
        try {
            String limitParam = getQueryParam("limit");
            String statusFilter = getQueryParam("status");
            String typeFilter = getQueryParam("type");
            
            int limit = 50; // Default limit
            if (limitParam != null) {
                try {
                    limit = Integer.parseInt(limitParam);
                } catch (NumberFormatException e) {
                    limit = 50;
                }
            }

            StringBuilder sql = new StringBuilder("SELECT * FROM JOBS WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (statusFilter != null && !statusFilter.isEmpty()) {
                sql.append(" AND status = ?");
                params.add(statusFilter);
            }

            if (typeFilter != null && !typeFilter.isEmpty()) {
                sql.append(" AND type = ?");
                params.add(typeFilter);
            }

            sql.append(" ORDER BY created_at DESC LIMIT ?");
            params.add(limit);

            List<org.ruitx.jaws.types.Row> jobs = new org.ruitx.jaws.components.Mimir().getRows(sql.toString(), params.toArray());

            StringBuilder html = new StringBuilder();
            for (org.ruitx.jaws.types.Row job : jobs) {
                String status = job.getString("status").orElse("UNKNOWN");
                String statusClass = getStatusClass(status);
                
                html.append("<tr>")
                    .append("<td>")
                    .append("<span class=\"tag ").append(statusClass).append("\">").append(status).append("</span>")
                    .append("</td>")
                    .append("<td>")
                    .append("<strong>").append(job.getString("type").orElse("Unknown")).append("</strong>")
                    .append("<br>")
                    .append("<small class=\"has-text-grey\">").append(job.getString("id").orElse("")).append("</small>")
                    .append("</td>")
                    .append("<td>")
                    .append("<span class=\"tag is-small\">Priority: ").append(job.getInt("priority").orElse(5)).append("</span>")
                    .append("<br>")
                    .append("<small class=\"has-text-grey\">").append(job.getString("execution_mode").orElse("PARALLEL")).append("</small>")
                    .append("</td>")
                    .append("<td>")
                    .append("<small class=\"has-text-grey is-abbr-like\" title=\"")
                    .append(job.getLong("created_at").orElse(0L))
                    .append("\">")
                    .append(job.getLong("created_at").map(ts -> JawsUtils.formatUnixTimestamp(ts)).orElse("N/A"))
                    .append("</small>")
                    .append("</td>")
                    .append("<td>")
                    .append(job.getInt("current_retries").orElse(0)).append("/").append(job.getInt("max_retries").orElse(3))
                    .append("</td>")
                    .append("<td class=\"is-actions-cell\">")
                    .append("<div class=\"buttons is-right\">")
                    .append("<a href=\"/backoffice/jobs/").append(job.getString("id").orElse("")).append("\" ")
                    .append("class=\"button is-small is-primary\" type=\"button\">")
                    .append("<span class=\"icon\"><i class=\"mdi mdi-eye\"></i></span>")
                    .append("</a>");

                // Add reprocess button for failed jobs
                if ("FAILED".equals(status) || "DEAD_LETTER".equals(status)) {
                    html.append("<button class=\"button is-small is-warning\" ")
                        .append("hx-post=\"/htmx/backoffice/jobs/").append(job.getString("id").orElse("")).append("/reprocess\" ")
                        .append("hx-headers='js:{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
                        .append("hx-swap=\"outerHTML\" ")
                        .append("hx-target=\"closest tr\" ")
                        .append("hx-confirm=\"Are you sure you want to reprocess this job?\" ")
                        .append("type=\"button\">")
                        .append("<span class=\"icon\"><i class=\"mdi mdi-refresh\"></i></span>")
                        .append("</button>");
                }

                html.append("</div>")
                    .append("</td>")
                    .append("</tr>");
            }

            sendHTMLResponse(OK, html.toString());
        } catch (Exception e) {
            log.error("Failed to list jobs: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<tr><td colspan=\"6\">Error loading jobs</td></tr>");
        }
    }

    @AccessControl(login = true)
    @Route(endpoint = "/htmx/backoffice/jobs/:id/reprocess", method = POST)
    public void reprocessJobHTMX() {
        try {
            String jobId = getPathParam("id");
            
            // Get job details from database
            org.ruitx.jaws.components.Mimir mimir = new org.ruitx.jaws.components.Mimir();
            org.ruitx.jaws.types.Row jobRow = mimir.getRow("SELECT * FROM JOBS WHERE id = ?", jobId);
            
            if (jobRow == null) {
                sendHTMLResponse(NOT_FOUND, "<tr><td colspan=\"6\">Job not found</td></tr>");
                return;
            }

            String status = jobRow.getString("status").orElse("");
            
            if ("DEAD_LETTER".equals(status)) {
                // Try to find the job in dead letter queue and retry it
                String newJobId = jobQueue.getDeadLetterQueue().manualRetry(jobId, true);
                if (newJobId != null) {
                    sendHTMLResponse(OK, 
                        "<tr class=\"has-background-success-light\">" +
                        "<td colspan=\"6\">" +
                        "<div class=\"notification is-success is-light\">" +
                        "<strong>Success!</strong> Job reprocessed successfully. New Job ID: " + newJobId +
                        "</div>" +
                        "</td>" +
                        "</tr>");
                } else {
                    sendHTMLResponse(BAD_REQUEST, 
                        "<tr class=\"has-background-danger-light\">" +
                        "<td colspan=\"6\">" +
                        "<div class=\"notification is-danger is-light\">" +
                        "<strong>Error!</strong> Failed to reprocess job from Dead Letter Queue." +
                        "</div>" +
                        "</td>" +
                        "</tr>");
                }
            } else if ("FAILED".equals(status)) {
                // Reset job status to PENDING and resubmit
                mimir.executeSql(
                    "UPDATE JOBS SET status = ?, error_message = NULL, current_retries = 0, started_at = NULL, completed_at = NULL WHERE id = ?",
                    "PENDING", jobId
                );
                
                // Try to recreate and resubmit the job
                String jobType = jobRow.getString("type").orElse("");
                String payloadJson = jobRow.getString("payload").orElse("{}");
                
                try {
                    Map<String, Object> payload = org.ruitx.jaws.components.Odin.getMapper().readValue(payloadJson, Map.class);
                    org.ruitx.jaws.interfaces.Job job = org.ruitx.jaws.components.freyr.JobRegistry.getInstance().createJob(jobType, payload);
                    
                    if (job != null) {
                        // Update the existing job ID in the payload
                        payload.put("originalJobId", jobId);
                        jobQueue.submit(job);
                        
                        sendHTMLResponse(OK, 
                            "<tr class=\"has-background-success-light\">" +
                            "<td colspan=\"6\">" +
                            "<div class=\"notification is-success is-light\">" +
                            "<strong>Success!</strong> Job reprocessed successfully!" +
                            "</div>" +
                            "</td>" +
                            "</tr>");
                    } else {
                        sendHTMLResponse(BAD_REQUEST, 
                            "<tr class=\"has-background-danger-light\">" +
                            "<td colspan=\"6\">" +
                            "<div class=\"notification is-danger is-light\">" +
                            "<strong>Error!</strong> Failed to recreate job. Unknown job type: " + jobType +
                            "</div>" +
                            "</td>" +
                            "</tr>");
                    }
                } catch (Exception e) {
                    log.error("Failed to reprocess job {}: {}", jobId, e.getMessage(), e);
                    sendHTMLResponse(INTERNAL_SERVER_ERROR, 
                        "<tr class=\"has-background-danger-light\">" +
                        "<td colspan=\"6\">" +
                        "<div class=\"notification is-danger is-light\">" +
                        "<strong>Error!</strong> Failed to reprocess job: " + e.getMessage() +
                        "</div>" +
                        "</td>" +
                        "</tr>");
                }
            } else {
                sendHTMLResponse(BAD_REQUEST, 
                    "<tr class=\"has-background-warning-light\">" +
                    "<td colspan=\"6\">" +
                    "<div class=\"notification is-warning is-light\">" +
                    "<strong>Warning!</strong> Job cannot be reprocessed. Current status: " + status +
                    "</div>" +
                    "</td>" +
                    "</tr>");
            }
        } catch (Exception e) {
            log.error("Failed to reprocess job: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, 
                "<tr class=\"has-background-danger-light\">" +
                "<td colspan=\"6\">" +
                "<div class=\"notification is-danger is-light\">" +
                "<strong>Error!</strong> Failed to reprocess job: " + e.getMessage() +
                "</div>" +
                "</td>" +
                "</tr>");
        }
    }

    @AccessControl(login = true)
    @Route(endpoint = "/htmx/backoffice/users", method = GET)
    public void listUsersHTMX() {
        if (!isHTMX()) {
            sendHTMLResponse(METHOD_NOT_ALLOWED, "This endpoint is only accessible via HTMX");
            return;
        }

        APIResponse<List<User>> response = authService.listUsers();
        if (!response.success()) {
            sendHTMLResponse(response.code(), "Error loading users");
            return;
        }

        StringBuilder html = new StringBuilder();
        for (User user : response.data()) {

            html.append("<tr>")
                    .append("<td class=\"is-image-cell\">")
                    .append("<div class=\"image\">")
                    .append(user.profilePicture() != null && !user.profilePicture().isEmpty()
                            ? "<img class=\"is-rounded\" src=\"" + user.profilePicture() + "\">"
                            : "<img class=\"is-rounded\" src=\"https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg\">")
                    // .append(user.user().toLowerCase().replace(" ", "-")) // disable for now
                    // .append(".svg\">")
                    .append("</div>")
                    .append("</td>")
                    .append("<td data-label=\"Name\">")
                    .append("<p>").append(user.user()).append("</p>")
                    .append("<p class=\"has-text-grey is-size-7\">")
                    .append(user.email() != null ? user.email() : "No email")
                    .append("</p>")
                    .append("</td>")
                    .append("<td data-label=\"Full Name\">")
                    .append(user.firstName() != null ? user.firstName() : "")
                    .append(" ")
                    .append(user.lastName() != null ? user.lastName() : "")
                    .append("</td>")
                    .append("<td data-label=\"Created\">")
                    .append("<small class=\"has-text-grey is-abbr-like\" title=\"")
                    .append(user.createdAt() != null ? user.createdAt() : "N/A")
                    .append("\">")
                    .append(user.createdAt() != null ? JawsUtils.formatUnixTimestamp(user.createdAt()) : "N/A")
                    .append("</small>")
                    .append("</td>")
                    .append("<td class=\"is-actions-cell\">")
                    .append("<div class=\"buttons is-right\">")
                    .append("<a href=\"/backoffice/profile/").append(user.id()).append("\" ")
                    .append("class=\"button is-small is-primary\" type=\"button\">")
                    .append("<span class=\"icon\"><i class=\"mdi mdi-eye\"></i></span>")
                    .append("</a>")
                    .append("<button class=\"button is-small is-danger jb-modal\" data-target=\"sample-modal\" type=\"button\">")
                    .append("<span class=\"icon\"><i class=\"mdi mdi-trash-can\"></i></span>")
                    .append("</button>")
                    .append("</div>")
                    .append("</td>")
                    .append("</tr>");
        }

        sendHTMLResponse(OK, html.toString());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/htmx/backoffice/job-stats", method = GET)
    public void getJobStatsHTMX() {
        try {
            // Get job statistics from Freyr
            Map<String, Object> stats = jobQueue.getStatistics();
            
            // Get counts by status
            org.ruitx.jaws.components.Mimir mimir = new org.ruitx.jaws.components.Mimir();
            List<org.ruitx.jaws.types.Row> statusCounts = mimir.getRows(
                "SELECT status, COUNT(*) as count FROM JOBS GROUP BY status"
            );
            
            StringBuilder html = new StringBuilder();
            html.append("<div class=\"columns is-multiline\">");
            
            // Total jobs
            html.append("<div class=\"column is-3\">")
                .append("<div class=\"card\">")
                .append("<div class=\"card-content\">")
                .append("<div class=\"level is-mobile\">")
                .append("<div class=\"level-item\">")
                .append("<div class=\"is-widget-label\">")
                .append("<h3 class=\"subtitle is-spaced\">Total Jobs</h3>")
                .append("<h1 class=\"title\">").append(stats.getOrDefault("totalJobs", 0)).append("</h1>")
                .append("</div>")
                .append("</div>")
                .append("<div class=\"level-item has-widget-icon\">")
                .append("<div class=\"is-widget-icon\">")
                .append("<span class=\"icon has-text-primary is-large\">")
                .append("<i class=\"mdi mdi-briefcase-clock-outline mdi-48px\"></i>")
                .append("</span>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            // Completed jobs
            html.append("<div class=\"column is-3\">")
                .append("<div class=\"card\">")
                .append("<div class=\"card-content\">")
                .append("<div class=\"level is-mobile\">")
                .append("<div class=\"level-item\">")
                .append("<div class=\"is-widget-label\">")
                .append("<h3 class=\"subtitle is-spaced\">Completed</h3>")
                .append("<h1 class=\"title\">").append(stats.getOrDefault("completedJobs", 0)).append("</h1>")
                .append("</div>")
                .append("</div>")
                .append("<div class=\"level-item has-widget-icon\">")
                .append("<div class=\"is-widget-icon\">")
                .append("<span class=\"icon has-text-success is-large\">")
                .append("<i class=\"mdi mdi-check-circle mdi-48px\"></i>")
                .append("</span>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            // Failed jobs
            html.append("<div class=\"column is-3\">")
                .append("<div class=\"card\">")
                .append("<div class=\"card-content\">")
                .append("<div class=\"level is-mobile\">")
                .append("<div class=\"level-item\">")
                .append("<div class=\"is-widget-label\">")
                .append("<h3 class=\"subtitle is-spaced\">Failed</h3>")
                .append("<h1 class=\"title\">").append(stats.getOrDefault("failedJobs", 0)).append("</h1>")
                .append("</div>")
                .append("</div>")
                .append("<div class=\"level-item has-widget-icon\">")
                .append("<div class=\"is-widget-icon\">")
                .append("<span class=\"icon has-text-danger is-large\">")
                .append("<i class=\"mdi mdi-alert-circle mdi-48px\"></i>")
                .append("</span>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            // Queue size
            int queueSize = (Integer) stats.getOrDefault("parallelQueueSize", 0) + 
                           (Integer) stats.getOrDefault("sequentialQueueSize", 0);
            html.append("<div class=\"column is-3\">")
                .append("<div class=\"card\">")
                .append("<div class=\"card-content\">")
                .append("<div class=\"level is-mobile\">")
                .append("<div class=\"level-item\">")
                .append("<div class=\"is-widget-label\">")
                .append("<h3 class=\"subtitle is-spaced\">Queue Size</h3>")
                .append("<h1 class=\"title\">").append(queueSize).append("</h1>")
                .append("</div>")
                .append("</div>")
                .append("<div class=\"level-item has-widget-icon\">")
                .append("<div class=\"is-widget-icon\">")
                .append("<span class=\"icon has-text-warning is-large\">")
                .append("<i class=\"mdi mdi-clock-outline mdi-48px\"></i>")
                .append("</span>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            html.append("</div>");
            
            sendHTMLResponse(OK, html.toString());
        } catch (Exception e) {
            log.error("Failed to get job statistics: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<div class=\"notification is-danger\">Error loading job statistics</div>");
        }
    }

    private String getStatusClass(String status) {
        return switch (status) {
            case "PENDING" -> "is-warning";
            case "PROCESSING" -> "is-info";
            case "COMPLETED" -> "is-success";
            case "FAILED" -> "is-danger";
            case "TIMEOUT" -> "is-danger";
            case "RETRY_SCHEDULED" -> "is-warning";
            case "DEAD_LETTER" -> "is-dark";
            default -> "is-light";
        };
    }

}

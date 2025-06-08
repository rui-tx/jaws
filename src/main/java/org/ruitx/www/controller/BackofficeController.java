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
        context.put("currentPage", "dashboard");
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
        context.put("currentPage", "settings");
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
        context.put("currentPage", "users");
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
        context.put("currentPage", "profile");
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
        context.put("currentPage", "jobs");
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
        context.put("currentPage", "jobs");
        
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
            
            // Check if jobs list is empty and return appropriate message
            if (jobs.isEmpty()) {
                html.append("<tr>")
                    .append("<td colspan=\"6\" class=\"px-6 py-4 text-center text-gray-500\">")
                    .append("<div class=\"flex flex-col items-center justify-center py-8\">")
                    .append("<svg class=\"h-12 w-12 text-gray-400 mb-4\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                    .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M20.25 14.15v4.25c0 1.094-.787 2.036-1.872 2.18-2.087.277-4.216.42-6.378.42s-4.291-.143-6.378-.42c-1.085-.144-1.872-1.086-1.872-2.18v-4.25m16.5 0a2.18 2.18 0 00.75-1.661V8.706c0-1.081-.768-2.015-1.837-2.175a48.114 48.114 0 00-3.413-.387m4.5 8.006c-.194.165-.42.295-.673.38A23.978 23.978 0 0112 15.75c-2.648 0-5.195-.429-7.577-1.22a2.016 2.016 0 01-.673-.38m0 0A2.18 2.18 0 013 12.489V8.706c0-1.081.768-2.015 1.837-2.175a48.111 48.111 0 013.413-.387m7.5 0V5.25A2.25 2.25 0 0013.5 3h-3a2.25 2.25 0 00-2.25 2.25v.894m7.5 0a48.667 48.667 0 00-7.5 0M12 12.75h.008v.008H12v-.008z\" />")
                    .append("</svg>")
                    .append("<p class=\"text-sm text-gray-600 mb-1\">No jobs found</p>");
                    
                if (statusFilter != null && !statusFilter.isEmpty()) {
                    html.append("<p class=\"text-xs text-gray-500\">No jobs with status: ").append(statusFilter).append("</p>");
                } else {
                    html.append("<p class=\"text-xs text-gray-500\">The job queue is currently empty</p>");
                }
                
                html.append("</div>")
                    .append("</td>")
                    .append("</tr>");
            } else {
                // Process jobs normally
                for (org.ruitx.jaws.types.Row job : jobs) {
                    String status = job.getString("status").orElse("UNKNOWN");
                    String statusClass = getStatusClass(status);
                    
                    html.append("<tr class=\"hover:bg-gray-50\">")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap\">")
                        .append("<div class=\"flex items-center\">")
                        .append("<div class=\"h-10 w-10 flex-shrink-0\">")
                        .append("<span class=\"px-2 inline-flex text-xs leading-5 font-medium text-gray-900\">")
                        .append(status).append("</span>")
                        .append("</div>")
                        .append("</div>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap\">")
                        .append("<div class=\"text-sm font-medium text-gray-900\">").append(job.getString("type").orElse("Unknown")).append("</div>")
                        .append("<div class=\"text-sm text-gray-500\">")
                        .append("<small class=\"has-text-grey\">").append(job.getString("id").orElse("")).append("</small>")
                        .append("</div>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-500\">")
                        .append("<span class=\"px-2 inline-flex text-xs leading-5 font-medium text-gray-900\">Priority: ").append(job.getInt("priority").orElse(5)).append("</span>")
                        .append("<br>")
                        .append("<small class=\"has-text-grey\">").append(job.getString("execution_mode").orElse("PARALLEL")).append("</small>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-500\">")
                        .append("<time datetime=\"")
                        .append(job.getLong("created_at").orElse(0L))
                        .append("\">")
                        .append(job.getLong("created_at").map(ts -> JawsUtils.formatUnixTimestamp(ts)).orElse("N/A"))
                        .append("</time>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-500\">")
                        .append(job.getInt("current_retries").orElse(0)).append("/").append(job.getInt("max_retries").orElse(3))
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-right text-sm font-medium\">")
                        .append("<div class=\"flex space-x-2 justify-end\">")
                        .append("<a href=\"/backoffice/jobs/").append(job.getString("id").orElse("")).append("\" ")
                        .append("class=\"inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-primary-600 hover:bg-primary-700\" type=\"button\">")
                        .append("<svg class=\"mr-1 h-3 w-3\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                        .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z\" />")
                        .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15 12a3 3 0 11-6 0 3 3 0 016 0z\" />")
                        .append("</svg>")
                        .append("</a>");

                    // Add reprocess button for failed jobs
                    if ("FAILED".equals(status) || "DEAD_LETTER".equals(status)) {
                        html.append("<button class=\"inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-yellow-600 hover:bg-yellow-700\" ")
                            .append("hx-post=\"/htmx/backoffice/jobs/").append(job.getString("id").orElse("")).append("/reprocess\" ")
                            .append("hx-headers='js:{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
                            .append("hx-swap=\"outerHTML\" ")
                            .append("hx-target=\"closest tr\" ")
                            .append("hx-confirm=\"Are you sure you want to reprocess this job?\" ")
                            .append("type=\"button\">")
                            .append("<svg class=\"mr-1 h-3 w-3\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                            .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M16.023 9.348h4.992v-.001M2.985 19.644v-4.992m0 0h4.992m-4.993 0l3.181 3.183a8.25 8.25 0 0013.803-3.7M4.031 9.865a8.25 8.25 0 0113.803-3.7l3.181 3.182m0-4.991v4.99\" />")
                            .append("</svg>")
                            .append("</button>");
                    }

                    html.append("</div>")
                        .append("</td>")
                        .append("</tr>");
                }
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
                        "<tr class=\"bg-green-50\">" +
                        "<td colspan=\"6\" class=\"px-6 py-4\">" +
                        "<div class=\"bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded\">" +
                        "<strong>Success!</strong> Job reprocessed successfully. New Job ID: " + newJobId +
                        "</div>" +
                        "</td>" +
                        "</tr>");
                } else {
                    sendHTMLResponse(BAD_REQUEST, 
                        "<tr class=\"bg-red-50\">" +
                        "<td colspan=\"6\" class=\"px-6 py-4\">" +
                        "<div class=\"bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded\">" +
                        "<strong>Error!</strong> Failed to reprocess job from Dead Letter Queue." +
                        "</div>" +
                        "</td>" +
                        "</tr>");
                }
            } else if ("FAILED".equals(status)) {
                // Update job status to retry
                String updateSql = "UPDATE JOBS SET status = 'PENDING', retry_count = retry_count + 1, started_at = NULL, completed_at = NULL WHERE id = ?";
                new org.ruitx.jaws.components.Mimir().executeSql(updateSql, jobId);
                
                sendHTMLResponse(OK, 
                    "<tr class=\"bg-green-50\">" +
                    "<td colspan=\"6\" class=\"px-6 py-4\">" +
                    "<div class=\"bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded\">" +
                    "<strong>Success!</strong> Job has been reset to PENDING status and will be retried." +
                    "</div>" +
                    "</td>" +
                    "</tr>");
            } else {
                sendHTMLResponse(BAD_REQUEST, 
                    "<tr class=\"bg-red-50\">" +
                    "<td colspan=\"6\" class=\"px-6 py-4\">" +
                    "<div class=\"bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded\">" +
                    "<strong>Error!</strong> Job status is " + status + ". Only FAILED or DEAD_LETTER jobs can be reprocessed." +
                    "</div>" +
                    "</td>" +
                    "</tr>");
            }
        } catch (Exception e) {
            log.error("Failed to reprocess job: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, 
                "<tr class=\"bg-yellow-50\">" +
                "<td colspan=\"6\" class=\"px-6 py-4\">" +
                "<div class=\"bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded\">" +
                "<strong>Warning!</strong> An error occurred while reprocessing the job." +
                "</div>" +
                "</td>" +
                "</tr>");
        }
    }

    @AccessControl(login = true)
    @Route(endpoint = "/htmx/backoffice/jobs/:id/delete", method = POST)
    public void deleteJobHTMX() {
        try {
            String jobId = getPathParam("id");
            
            // Delete job from database
            String deleteSql = "DELETE FROM JOBS WHERE id = ?";
            new org.ruitx.jaws.components.Mimir().executeSql(deleteSql, jobId);
            
            sendHTMLResponse(OK, 
                "<tr class=\"bg-red-50\">" +
                "<td colspan=\"6\" class=\"px-6 py-4\">" +
                "<div class=\"bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded\">" +
                "<strong>Deleted!</strong> Job has been permanently deleted." +
                "</div>" +
                "</td>" +
                "</tr>");
        } catch (Exception e) {
            log.error("Failed to delete job: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error deleting job");
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
            html.append("<tr class=\"hover:bg-gray-50\">")
                    .append("<td class=\"px-6 py-4 whitespace-nowrap\">")
                    .append("<div class=\"flex items-center\">")
                    .append("<div class=\"h-10 w-10 flex-shrink-0\">")
                    .append(user.profilePicture() != null && !user.profilePicture().isEmpty()
                            ? "<img class=\"h-10 w-10 rounded-full\" src=\"" + user.profilePicture() + "\" alt=\"\">"
                            : "<img class=\"h-10 w-10 rounded-full\" src=\"https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg\" alt=\"\">")
                    .append("</div>")
                    .append("</div>")
                    .append("</td>")
                    .append("<td class=\"px-6 py-4 whitespace-nowrap\">")
                    .append("<div class=\"text-sm font-medium text-gray-900\">").append(user.user()).append("</div>")
                    .append("<div class=\"text-sm text-gray-500\">")
                    .append(user.email() != null ? user.email() : "No email")
                    .append("</div>")
                    .append("</td>")
                    .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-900\">")
                    .append(user.firstName() != null ? user.firstName() : "")
                    .append(" ")
                    .append(user.lastName() != null ? user.lastName() : "")
                    .append("</td>")
                    .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-500\">")
                    .append("<time datetime=\"")
                    .append(user.createdAt() != null ? user.createdAt() : "N/A")
                    .append("\">")
                    .append(user.createdAt() != null ? JawsUtils.formatUnixTimestamp(user.createdAt()) : "N/A")
                    .append("</time>")
                    .append("</td>")
                    .append("<td class=\"px-6 py-4 whitespace-nowrap text-right text-sm font-medium\">")
                    .append("<div class=\"flex space-x-2 justify-end\">")
                    .append("<a href=\"/backoffice/profile/").append(user.id()).append("\" ")
                    .append("class=\"inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-primary-600 hover:bg-primary-700\" type=\"button\">")
                    .append("<svg class=\"mr-1 h-3 w-3\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                    .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z\" />")
                    .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15 12a3 3 0 11-6 0 3 3 0 016 0z\" />")
                    .append("</svg>")
                    .append("</a>")
                    .append("<button class=\"inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-red-600 hover:bg-red-700\" onclick=\"openModal('sample-modal')\" type=\"button\">")
                    .append("<svg class=\"mr-1 h-3 w-3\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                    .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M14.74 9l-.346 9m-4.788 0L9.26 9m9.968-3.21c.342.052.682.107 1.022.166m-1.022-.165L18.16 19.673a2.25 2.25 0 01-2.244 2.077H8.084a2.25 2.25 0 01-2.244-2.077L4.772 5.79m14.456 0a48.108 48.108 0 00-3.478-.397m-12 .562c.34-.059.68-.114 1.022-.165m0 0a48.11 48.11 0 013.478-.397m7.5 0v-.916c0-1.18-.91-2.164-2.09-2.201a51.964 51.964 0 00-3.32 0c-1.18.037-2.09 1.022-2.09 2.201v.916m7.5 0a48.667 48.667 0 00-7.5 0M12 12.75h.008v.008H12v-.008z\" />")
                    .append("</svg>")
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
            html.append("<div class=\"grid grid-cols-1 md:grid-cols-2 xl:grid-cols-4 gap-4\">");
            
            // Total jobs
            html.append("<div class=\"bg-white rounded-lg shadow border border-gray-200 p-6\">")
                .append("<div class=\"flex items-center justify-between\">")
                .append("<div>")
                .append("<p class=\"text-sm font-medium text-gray-600\">Total Jobs</p>")
                .append("<p class=\"text-2xl font-bold text-gray-900\">").append(stats.getOrDefault("totalJobs", 0)).append("</p>")
                .append("</div>")
                .append("<div class=\"flex-shrink-0\">")
                .append("<svg class=\"h-8 w-8 text-primary-600\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M20.25 14.15v4.25c0 1.094-.787 2.036-1.872 2.18-2.087.277-4.216.42-6.378.42s-4.291-.143-6.378-.42c-1.085-.144-1.872-1.086-1.872-2.18v-4.25m16.5 0a2.18 2.18 0 00.75-1.661V8.706c0-1.081-.768-2.015-1.837-2.175a48.114 48.114 0 00-3.413-.387m4.5 8.006c-.194.165-.42.295-.673.38A23.978 23.978 0 0112 15.75c-2.648 0-5.195-.429-7.577-1.22a2.016 2.016 0 01-.673-.38m0 0A2.18 2.18 0 013 12.489V8.706c0-1.081.768-2.015 1.837-2.175a48.111 48.111 0 013.413-.387m7.5 0V5.25A2.25 2.25 0 0013.5 3h-3a2.25 2.25 0 00-2.25 2.25v.894m7.5 0a48.667 48.667 0 00-7.5 0M12 12.75h.008v.008H12v-.008z\" />")
                .append("</svg>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            // Completed jobs
            html.append("<div class=\"bg-white rounded-lg shadow border border-gray-200 p-6\">")
                .append("<div class=\"flex items-center justify-between\">")
                .append("<div>")
                .append("<p class=\"text-sm font-medium text-gray-600\">Completed</p>")
                .append("<p class=\"text-2xl font-bold text-green-600\">").append(stats.getOrDefault("completedJobs", 0)).append("</p>")
                .append("</div>")
                .append("<div class=\"flex-shrink-0\">")
                .append("<svg class=\"h-8 w-8 text-green-600\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M9 12.75L11.25 15 15 9.75M21 12a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z\" />")
                .append("</svg>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            // Failed jobs
            html.append("<div class=\"bg-white rounded-lg shadow border border-gray-200 p-6\">")
                .append("<div class=\"flex items-center justify-between\">")
                .append("<div>")
                .append("<p class=\"text-sm font-medium text-gray-600\">Failed</p>")
                .append("<p class=\"text-2xl font-bold text-red-600\">").append(stats.getOrDefault("failedJobs", 0)).append("</p>")
                .append("</div>")
                .append("<div class=\"flex-shrink-0\">")
                .append("<svg class=\"h-8 w-8 text-red-600\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M12 9v3.75m9-.75a9 9 0 11-18 0 9 9 0 0118 0zm-9 3.75h.008v.008H12v-.008z\" />")
                .append("</svg>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            // Queue size
            int queueSize = (Integer) stats.getOrDefault("parallelQueueSize", 0) + 
                           (Integer) stats.getOrDefault("sequentialQueueSize", 0);
            html.append("<div class=\"bg-white rounded-lg shadow border border-gray-200 p-6\">")
                .append("<div class=\"flex items-center justify-between\">")
                .append("<div>")
                .append("<p class=\"text-sm font-medium text-gray-600\">Queue Size</p>")
                .append("<p class=\"text-2xl font-bold text-yellow-600\">").append(queueSize).append("</p>")
                .append("</div>")
                .append("<div class=\"flex-shrink-0\">")
                .append("<svg class=\"h-8 w-8 text-yellow-600\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M12 6v6h4.5m4.5 0a9 9 0 11-18 0 9 9 0 0118 0z\" />")
                .append("</svg>")
                .append("</div>")
                .append("</div>")
                .append("</div>");
            
            html.append("</div>");
            
            sendHTMLResponse(OK, html.toString());
        } catch (Exception e) {
            log.error("Failed to get job statistics: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<div class=\"bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded\">Error loading job statistics</div>");
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

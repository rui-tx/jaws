package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.strings.ResponseCode;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.jaws.utils.JawsLogger;
import org.ruitx.jaws.utils.JawsUtils;
import org.ruitx.www.dto.auth.UserCreateRequest;
import org.ruitx.www.dto.auth.UserUpdateRequest;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.repository.AuthRepo;
import org.ruitx.www.service.AuthService;
import org.ruitx.www.service.AuthorizationService;
import org.ruitx.www.model.auth.Role;
import org.ruitx.www.model.auth.UserRole;
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
import static org.ruitx.jaws.strings.RequestType.DELETE;
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
    private static final String LOGS_PAGE = "backoffice/partials/logs.html";
    private static final String LOG_DETAILS_PAGE = "backoffice/partials/log-details.html";
    private static final String ROLES_PAGE = "backoffice/partials/roles.html";
    private static final Logger log = LoggerFactory.getLogger(BackofficeController.class);

    private final AuthRepo authRepo;
    private final AuthService authService;
    private final AuthorizationService authorizationService;
    private final Freyr jobQueue;
    private final org.ruitx.jaws.components.Mimir logsDb;

    public BackofficeController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.authRepo = new AuthRepo();
        this.authService = new AuthService();
        this.authorizationService = new AuthorizationService();
        this.jobQueue = Freyr.getInstance();
        this.logsDb = new org.ruitx.jaws.components.Mimir("src/main/resources/logs.db");
    }

    /**
     * Creates the base context with user information and role-based navigation visibility.
     *
     * @param user the current user
     * @return context map with user info and navigation permissions
     */
    private Map<String, String> getBaseContext(User user) {
        Map<String, String> context = new HashMap<>();
        
        // Basic user info
        context.put("userId", user.id().toString());
        context.put("currentUser", user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        
        // Role-based navigation visibility
        boolean isAdmin = authorizationService.hasRole(user.id(), "admin");
        context.put("canAccessRoles", String.valueOf(isAdmin));
        context.put("canAccessUserManagement", String.valueOf(isAdmin));
        
        return context;
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice", method = GET)
    public void renderIndex() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "dashboard");
        setContext(context);

        JawsLogger.info("BackofficeController: Rendering dashboard page");

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, DASHBOARD_PAGE));
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/settings", method = GET)
    public void renderSettings() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "settings");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, SETTINGS_PAGE));
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/users", method = GET)
    public void renderUsers() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "users");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, USERS_PAGE));
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/users", method = POST)
    public void createUser(UserCreateRequest request) {
        // No manual role check needed anymore! The middleware handles it
        APIResponse<String> response = authService.createUser(request);
        sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
    }

    @AccessControl(login = true)
    @Route(endpoint = "/backoffice/profile/:id", method = GET)
    public void renderUserProfile() {
        String userId = getPathParam("id");
        String currentUserId = Tyr.getUserIdFromJWT(getCurrentToken());
        User currentUser = authRepo.getUserById(Long.parseLong(currentUserId)).get();
        
        // Security check: Users can only view their own profile unless they're admin
        boolean isAdmin = authorizationService.hasRole(currentUser.id(), "admin");
        boolean isOwnProfile = userId.equals(currentUserId);
        
        if (!isOwnProfile && !isAdmin) {
            JawsLogger.warn("BackofficeController: User {} attempted to access profile {} without permission", 
                currentUserId, userId);
            sendHTMLResponse(FORBIDDEN, "Access denied: You can only view your own profile");
            return;
        }
        
        User user = authRepo.getUserById(Long.parseLong(userId)).get();

        Map<String, String> context = getBaseContext(currentUser);
        
        // Add user-specific profile data
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
        String userId = getPathParam("id");
        String currentUserId = Tyr.getUserIdFromJWT(getCurrentToken());
        User currentUser = authRepo.getUserById(Long.parseLong(currentUserId)).get();
        
        // Security check: Users can only update their own profile unless they're admin
        boolean isAdmin = authorizationService.hasRole(currentUser.id(), "admin");
        boolean isOwnProfile = userId.equals(currentUserId);
        
        if (!isOwnProfile && !isAdmin) {
            JawsLogger.warn("BackofficeController: User {} attempted to update profile {} without permission", 
                currentUserId, userId);
            sendHTMLResponse(FORBIDDEN, "Access denied: You can only update your own profile");
            return;
        }
        
        APIResponse<String> response = authService
                .updateUser(Integer.parseInt(userId), request);
        sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/jobs", method = GET)
    public void renderJobs() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "jobs");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, JOBS_PAGE));
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/logs", method = GET)
    public void renderLogs() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "logs");
        setContext(context);

        JawsLogger.info("BackofficeController: Rendering logs page");

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, LOGS_PAGE));
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/logs/:id", method = GET)
    public void renderLogDetails() {
        String logId = getPathParam("id");
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        // Get log details from database
        org.ruitx.jaws.types.Row logRow = logsDb.getRow("SELECT * FROM LOG_ENTRIES WHERE id = ?", logId);
        if (logRow == null) {
            sendHTMLResponse(NOT_FOUND, "Log entry not found");
            return;
        }

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "logs");
        
        // Add log-specific data
        context.put("logId", logId);
        context.put("logLevel", logRow.getString("level").orElse("UNKNOWN"));
        context.put("logMessage", logRow.getString("message").orElse(""));
        context.put("logLogger", logRow.getString("logger").orElse("Unknown"));
        context.put("logMethod", logRow.getString("method").orElse(""));
        context.put("logLine", logRow.getInt("line").orElse(0).toString());
        context.put("logThread", logRow.getString("thread").orElse(""));
        context.put("logTimestamp", logRow.getLong("timestamp").map(ts -> JawsUtils.formatUnixTimestamp(ts, "yyyy-MM-dd HH:mm:ss")).orElse(""));
        context.put("logException", logRow.getString("exception").orElse(""));
        
        setContext(context);

        JawsLogger.info("BackofficeController: Rendering log details page for log ID: {}", logId);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, LOG_DETAILS_PAGE));
    }

    @AccessControl(login = true, role = "admin")
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

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "jobs");
        
        // Add job-specific data
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

    @AccessControl(login = true, role = "admin")
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

    @AccessControl(login = true, role = "admin")
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

    @AccessControl(login = true, role = "admin")
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

    @AccessControl(login = true, role = "admin")
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

    @AccessControl(login = true, role = "admin")
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

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/logs", method = GET)
    public void listLogsHTMX() {
        try {
            String limitParam = getQueryParam("limit");
            String levelFilter = getQueryParam("level");
            String loggerFilter = getQueryParam("logger");
            
            int limit = 100; // Default limit for logs
            if (limitParam != null) {
                try {
                    limit = Integer.parseInt(limitParam);
                } catch (NumberFormatException e) {
                    limit = 100;
                }
            }

            StringBuilder sql = new StringBuilder("SELECT * FROM LOG_ENTRIES WHERE 1=1");
            List<Object> params = new ArrayList<>();

            if (levelFilter != null && !levelFilter.isEmpty()) {
                sql.append(" AND level = ?");
                params.add(levelFilter);
            }

            if (loggerFilter != null && !loggerFilter.isEmpty()) {
                sql.append(" AND logger LIKE ?");
                params.add("%" + loggerFilter + "%");
            }

            sql.append(" ORDER BY timestamp DESC LIMIT ?");
            params.add(limit);

            List<org.ruitx.jaws.types.Row> logs = logsDb.getRows(sql.toString(), params.toArray());

            StringBuilder html = new StringBuilder();
            
            // Check if logs list is empty and return appropriate message
            if (logs.isEmpty()) {
                html.append("<tr>")
                    .append("<td colspan=\"6\" class=\"px-6 py-4 text-center text-gray-500\">")
                    .append("<div class=\"flex flex-col items-center justify-center py-8\">")
                    .append("<svg class=\"h-12 w-12 text-gray-400 mb-4\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                    .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M19.5 14.25v-2.625a3.375 3.375 0 00-3.375-3.375h-1.5A1.125 1.125 0 0113.5 7.125v-1.5a3.375 3.375 0 00-3.375-3.375H8.25m0 12.75h7.5m-7.5 3H12M10.5 2.25H5.625c-.621 0-1.125.504-1.125 1.125v17.25c0 .621.504 1.125 1.125 1.125h12.75c0 .621-.504 1.125-1.125 1.125H11.25a9 9 0 01-9-9V3.375c0-.621.504-1.125 1.125-1.125z\" />")
                    .append("</svg>")
                    .append("<p class=\"text-sm text-gray-600 mb-1\">No logs found</p>");
                    
                if (levelFilter != null && !levelFilter.isEmpty()) {
                    html.append("<p class=\"text-xs text-gray-500\">No logs with level: ").append(levelFilter).append("</p>");
                } else {
                    html.append("<p class=\"text-xs text-gray-500\">No log entries are available</p>");
                }
                
                html.append("</div>")
                    .append("</td>")
                    .append("</tr>");
            } else {
                // Process logs normally
                for (org.ruitx.jaws.types.Row logEntry : logs) {
                    String level = logEntry.getString("level").orElse("UNKNOWN");
                    String levelClass = getLogLevelClass(level);
                    String message = logEntry.getString("message").orElse("");
                    String logger = logEntry.getString("logger").orElse("Unknown");
                    String method = logEntry.getString("method").orElse("");
                    String thread = logEntry.getString("thread").orElse("");
                    Long timestamp = logEntry.getLong("timestamp").orElse(0L);
                    Integer lineNumber = logEntry.getInt("line").orElse(0);
                    
                    // Truncate long messages for table display
                    String displayMessage = message.length() > 100 ? message.substring(0, 100) + "..." : message;
                    
                    // Extract class name from logger (remove package)
                    String displayLogger = logger.contains(".") ? logger.substring(logger.lastIndexOf(".") + 1) : logger;
                    
                    html.append("<tr class=\"hover:bg-gray-50\">")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap\">")
                        .append("<span class=\"px-2 py-1 text-xs font-medium rounded-full ").append(levelClass).append("\">")
                        .append(level)
                        .append("</span>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4\">")
                        .append("<div class=\"text-sm text-gray-900 max-w-md\">")
                        .append("<span title=\"").append(message.replace("\"", "&quot;")).append("\">")
                        .append(displayMessage)
                        .append("</span>")
                        .append("</div>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap\">")
                        .append("<div class=\"text-sm font-medium text-gray-900\">").append(displayLogger).append("</div>")
                        .append("<div class=\"text-sm text-gray-500\">")
                        .append(method).append(lineNumber > 0 ? ":" + lineNumber : "")
                        .append("</div>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-500\">")
                        .append("<time datetime=\"").append(timestamp).append("\">")
                        .append(JawsUtils.formatUnixTimestamp(timestamp, "yyyy-MM-dd HH:mm:ss"))
                        .append("</time>")
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-sm text-gray-500\">")
                        .append(thread)
                        .append("</td>")
                        .append("<td class=\"px-6 py-4 whitespace-nowrap text-right text-sm font-medium\">")
                        .append("<div class=\"flex space-x-2 justify-end\">");
                    
                    // Add view details button for all logs
                    html.append("<a href=\"/backoffice/logs/").append(logEntry.getInt("id").orElse(0)).append("\" ")
                        .append("class=\"inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-primary-600 hover:bg-primary-700\" ")
                        .append("title=\"View log details\">")
                        .append("<svg class=\"h-3 w-3\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
                        .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z\" />")
                        .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15 12a3 3 0 11-6 0 3 3 0 016 0z\" />")
                        .append("</svg>")
                        .append("</a>");
                    
                    html.append("</div>")
                        .append("</td>")
                        .append("</tr>");
                }
            }

            sendHTMLResponse(OK, html.toString());
        } catch (Exception e) {
            JawsLogger.error(e, "Failed to list logs: {}", e.getMessage());
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "<tr><td colspan=\"6\">Error loading logs</td></tr>");
        }
    }

    private String getLogLevelClass(String level) {
        return switch (level) {
            case "ERROR" -> "bg-red-100 text-red-800";
            case "WARN" -> "bg-yellow-100 text-yellow-800";
            case "INFO" -> "bg-blue-100 text-blue-800";
            case "DEBUG" -> "bg-gray-100 text-gray-800";
            case "TRACE" -> "bg-purple-100 text-purple-800";
            default -> "bg-gray-100 text-gray-800";
        };
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

    // =============================================
    // ROLE MANAGEMENT ENDPOINTS
    // =============================================

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/roles", method = GET)
    public void renderRoles() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = getBaseContext(user);
        context.put("currentPage", "roles");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, ROLES_PAGE));
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/roles", method = POST)
    public void createRole(RoleCreateRequest request) {
        APIResponse<String> response = authorizationService.createRole(request.name(), request.description());
        sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/backoffice/assign-role", method = POST)
    public void assignRole(RoleAssignRequest request) {
        JawsLogger.info("BackofficeController: Assign role request received");
        
        if (request == null) {
            JawsLogger.warn("BackofficeController: Request object is null");
            sendHTMLResponse(BAD_REQUEST, "Invalid request data");
            return;
        }
        
        JawsLogger.info("BackofficeController: Parsed request - userId: {}, roleId: {}", 
            request.userId(), request.roleId());
        
        if (request.userId() == null || request.roleId() == null) {
            JawsLogger.warn("BackofficeController: Missing required fields - userId: {}, roleId: {}", 
                request.userId(), request.roleId());
            sendHTMLResponse(BAD_REQUEST, "Missing required fields: userId and roleId are required");
            return;
        }
        
        // Get current user ID for audit trail
        Integer assignedBy = Integer.parseInt(Tyr.getUserIdFromJWT(getCurrentToken()));
        
        APIResponse<String> response = authorizationService.assignRole(
            request.userId(), 
            request.roleId(), 
            assignedBy
        );
        
        JawsLogger.info("BackofficeController: Assign role response - code: {}, message: {}", 
            response.code(), response.info());
        
        sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), response.info());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/roles", method = GET)
    public void listRolesHTMX() {
        List<Role> roles = authorizationService.getAllRoles();
        
        StringBuilder htmlBuilder = new StringBuilder();
        
        if (roles.isEmpty()) {
            htmlBuilder.append("""
                <tr>
                    <td colspan="5" class="px-6 py-4 text-center text-gray-500">
                        No roles found. Create your first role!
                    </td>
                </tr>
                """);
        } else {
            for (Role role : roles) {
                int userCount = authorizationService.getUserCountForRole(role.id());
                
                htmlBuilder.append(String.format("""
                    <tr>
                        <td class="px-6 py-4 whitespace-nowrap">
                            <div class="flex items-center">
                                <div class="flex-shrink-0 h-10 w-10">
                                    <div class="h-10 w-10 rounded-full bg-primary-100 flex items-center justify-center">
                                        <svg class="h-5 w-5 text-primary-600" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                                            <path stroke-linecap="round" stroke-linejoin="round" d="M9 12.75L11.25 15 15 9.75m-3-7.036A11.959 11.959 0 013.598 6 11.99 11.99 0 003 9.749c0 5.592 3.824 10.29 9 11.623 5.176-1.332 9-6.03 9-11.623 0-1.31-.21-2.571-.598-3.751h-.152c-3.196 0-6.1-1.248-8.25-3.285z" />
                                        </svg>                                        
                                    </div>
                                </div>
                                <div class="ml-4">
                                    <div class="text-sm font-medium text-gray-900">%s</div>
                                    <div class="text-sm text-gray-500">Role ID: %d</div>
                                </div>
                            </div>
                        </td>
                        <td class="px-6 py-4">
                            <div class="text-sm text-gray-900">%s</div>
                        </td>
                        <td class="px-6 py-4 whitespace-nowrap">
                            <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-blue-100 text-blue-800">
                                %d users
                            </span>
                        </td>
                        <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                            %s
                        </td>
                        <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium space-x-2">
                            <button
                                class="text-red-600 hover:text-red-900"
                                hx-delete="/htmx/backoffice/roles/%d"
                                hx-confirm="Are you sure you want to delete this role? This action cannot be undone."
                                hx-headers='{"Content-Type": "application/json"}'
                                hx-swap="innerHTML transition:true"
                                hx-target="#roles-table-body"
                                hx-trigger="click">
                                Delete
                            </button>
                        </td>
                    </tr>
                    """,
                    role.name(),
                    role.id(),
                    role.description() != null ? role.description() : "No description",
                    userCount,
                    role.createdAt() != null ? JawsUtils.formatUnixTimestamp(role.createdAt()) : "Unknown",
                    role.id()
                ));
            }
        }
        
        sendHTMLResponse(OK, htmlBuilder.toString());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/user-roles", method = GET)
    public void listUserRolesHTMX() {
        List<UserRole> userRoles = authorizationService.getAllUserRoles();
        
        StringBuilder htmlBuilder = new StringBuilder();
        
        if (userRoles.isEmpty()) {
            htmlBuilder.append("""
                <tr>
                    <td colspan="5" class="px-6 py-4 text-center text-gray-500">
                        No role assignments found. Start assigning roles to users!
                    </td>
                </tr>
                """);
        } else {
            for (UserRole userRole : userRoles) {
                // Get user and role details
                User user = authRepo.getUserById(userRole.userId().longValue()).orElse(null);
                Role role = authorizationService.getRoleById(userRole.roleId()).orElse(null);
                User assignedByUser = userRole.assignedBy() != null ? 
                    authRepo.getUserById(userRole.assignedBy().longValue()).orElse(null) : null;
                
                if (user != null && role != null) {
                    htmlBuilder.append(String.format("""
                        <tr>
                            <td class="px-6 py-4 whitespace-nowrap">
                                <div class="flex items-center">
                                    <div class="flex-shrink-0 h-10 w-10">
                                        <img class="h-10 w-10 rounded-full" src="%s" alt="">
                                    </div>
                                    <div class="ml-4">
                                        <div class="text-sm font-medium text-gray-900">%s %s</div>
                                        <div class="text-sm text-gray-500">@%s</div>
                                    </div>
                                </div>
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap">
                                <span class="inline-flex items-center px-2.5 py-0.5 rounded-full text-xs font-medium bg-primary-100 text-primary-800">
                                    %s
                                </span>
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                                %s
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                                %s
                            </td>
                            <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                                <button
                                    class="text-red-600 hover:text-red-900"
                                    hx-delete="/htmx/backoffice/user-roles/%d"
                                    hx-confirm="Are you sure you want to remove this role from the user?"
                                    hx-headers='{"Content-Type": "application/json"}'
                                    hx-swap="innerHTML transition:true"
                                    hx-target="#user-roles-table-body"
                                    hx-trigger="click">
                                    Remove
                                </button>
                            </td>
                        </tr>
                        """,
                        user.profilePicture() != null && !user.profilePicture().isEmpty() ? 
                            user.profilePicture() : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg",
                        user.firstName() != null ? user.firstName() : "",
                        user.lastName() != null ? user.lastName() : "",
                        user.user(),
                        role.name(),
                        userRole.assignedAt() != null ? JawsUtils.formatUnixTimestamp(userRole.assignedAt()) : "Unknown",
                        assignedByUser != null ? assignedByUser.user() : "System",
                        userRole.id()
                    ));
                }
            }
        }
        
        sendHTMLResponse(OK, htmlBuilder.toString());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/users-list", method = GET)
    public void listUsersForDropdownHTMX() {
        List<User> users = authRepo.getAllUsers();
        
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<option value=\"\">Select a user...</option>");
        
        for (User user : users) {
            htmlBuilder.append(String.format("""
                <option value="%d">%s %s (@%s)</option>
                """,
                user.id(),
                user.firstName() != null ? user.firstName() : "",
                user.lastName() != null ? user.lastName() : "",
                user.user()
            ));
        }
        
        sendHTMLResponse(OK, htmlBuilder.toString());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/roles-list", method = GET)
    public void listRolesForDropdownHTMX() {
        List<Role> roles = authorizationService.getAllRoles();
        
        StringBuilder htmlBuilder = new StringBuilder();
        htmlBuilder.append("<option value=\"\">Select a role...</option>");
        
        for (Role role : roles) {
            htmlBuilder.append(String.format("""
                <option value="%d">%s - %s</option>
                """,
                role.id(),
                role.name(),
                role.description() != null ? role.description() : "No description"
            ));
        }
        
        sendHTMLResponse(OK, htmlBuilder.toString());
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/roles/:id", method = DELETE)
    public void deleteRoleHTMX() {
        try {
            Integer roleId = Integer.parseInt(getPathParam("id"));
            APIResponse<String> response = authorizationService.deleteRole(roleId);
            
            if (response.code().equals("200 OK")) {
                // Return updated roles table
                listRolesHTMX();
            } else {
                sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), 
                    "<div class=\"text-red-600\">" + response.info() + "</div>");
            }
        } catch (NumberFormatException e) {
            sendHTMLResponse(BAD_REQUEST, "<div class=\"text-red-600\">Invalid role ID</div>");
        }
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/user-roles/:id", method = DELETE)
    public void removeUserRoleHTMX() {
        try {
            Integer userRoleId = Integer.parseInt(getPathParam("id"));
            APIResponse<String> response = authorizationService.removeUserRole(userRoleId);
            
            if (response.code().equals("200 OK")) {
                // Return updated user-roles table
                listUserRolesHTMX();
            } else {
                sendHTMLResponse(ResponseCode.fromCodeAndMessage(response.code()), 
                    "<div class=\"text-red-600\">" + response.info() + "</div>");
            }
        } catch (NumberFormatException e) {
            sendHTMLResponse(BAD_REQUEST, "<div class=\"text-red-600\">Invalid user role ID</div>");
        }
    }

    // =============================================
    // DTOs for Role Management
    // =============================================
    
    public record RoleCreateRequest(String name, String description) {}
    public record RoleAssignRequest(Integer userId, Integer roleId) {}

}

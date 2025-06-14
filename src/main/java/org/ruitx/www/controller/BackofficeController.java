package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.Yggdrasill;
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
import org.ruitx.www.service.BackofficeService;
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
    private final BackofficeService backofficeService;

    public BackofficeController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.authRepo = new AuthRepo();
        this.authService = new AuthService();
        this.authorizationService = new AuthorizationService();
        this.jobQueue = Freyr.getInstance();
        this.logsDb = new org.ruitx.jaws.components.Mimir("src/main/resources/logs.db");
        this.backofficeService = new BackofficeService();
    }

    /**
     * Creates the base context with user information and role-based navigation visibility.
     *
     * @param user the current user
     * @return context map with user info and navigation permissions
     */
    private Map<String, String> getBaseContext(User user) {
        return backofficeService.getBaseContext(user);
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
        String html = backofficeService.generateJobsTableHTML(
            getQueryParam("page"),
            getQueryParam("size"),
            getQueryParam("sort"),
            getQueryParam("direction"),
            getQueryParam("status"),
            getQueryParam("type")
        );
        sendHTMLResponse(OK, html);
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/jobs/:id/reprocess", method = POST)
    public void reprocessJobHTMX() {
        String jobId = getPathParam("id");
        String html = backofficeService.processJobReprocessing(jobId);
        sendHTMLResponse(OK, html);
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

        String html = backofficeService.generateUsersTableHTML(
            getQueryParam("page"),
            getQueryParam("size"),
            getQueryParam("sort"),
            getQueryParam("direction")
        );
        sendHTMLResponse(OK, html);
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/job-stats", method = GET)
    public void getJobStatsHTMX() {
        try {
            Yggdrasill.RequestContext context = getRequestContext();
            String html = backofficeService.generateJobStatsHTML(context.getRequest(), context.getResponse());
            sendHTMLResponse(OK, html);
        } catch (Exception e) {
            log.error("Failed to get job stats: {}", e.getMessage(), e);
            sendHTMLResponse(INTERNAL_SERVER_ERROR, "Error loading job statistics");
        }
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/logs", method = GET)
    public void listLogsHTMX() {
        try {
            // Parse pagination parameters
            int page = 0;
            int size = 25; // Default 25 for logs (they can be verbose)
            String sortBy = "timestamp";
            org.ruitx.jaws.types.SortDirection direction = org.ruitx.jaws.types.SortDirection.DESC;
            String levelFilter = getQueryParam("level");
            String loggerFilter = getQueryParam("logger");

            String pageParam = getQueryParam("page");
            String sizeParam = getQueryParam("size");
            String sortParam = getQueryParam("sort");
            String directionParam = getQueryParam("direction");

            if (pageParam != null) {
                try {
                    page = Integer.parseInt(pageParam);
                } catch (NumberFormatException e) {
                    page = 0;
                }
            }

            if (sizeParam != null) {
                try {
                    size = Integer.parseInt(sizeParam);
                    if (size < 1) size = 25;
                    if (size > 100) size = 100; // Max 100 per page
                } catch (NumberFormatException e) {
                    size = 25;
                }
            }

            if (sortParam != null && !sortParam.trim().isEmpty()) {
                sortBy = sortParam.trim();
            }

            if (directionParam != null && "ASC".equalsIgnoreCase(directionParam)) {
                direction = org.ruitx.jaws.types.SortDirection.ASC;
            }

            // Build base SQL with filters
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

            // Create PageRequest
            org.ruitx.jaws.types.PageRequest pageRequest = new org.ruitx.jaws.types.PageRequest(page, size, sortBy, direction);

            // Get paginated logs from database
            org.ruitx.jaws.types.Page<org.ruitx.jaws.types.Row> logPage = logsDb
                    .getPage(sql.toString(), pageRequest, params.toArray());

            List<org.ruitx.jaws.types.Row> logs = logPage.getContent();

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

            // Add pagination controls
            html.append(generatePaginationHTML(logPage, "logs"));

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
        String html = backofficeService.generateRolesTableHTML(
            getQueryParam("page"),
            getQueryParam("size"),
            getQueryParam("sort"),
            getQueryParam("direction")
        );
        sendHTMLResponse(OK, html);
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/user-roles", method = GET)
    public void listUserRolesHTMX() {
        String html = backofficeService.generateUserRolesTableHTML();
        sendHTMLResponse(OK, html);
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/users-list", method = GET)
    public void listUsersForDropdownHTMX() {
        String html = backofficeService.generateUsersDropdownHTML();
        sendHTMLResponse(OK, html);
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/roles-list", method = GET)
    public void listRolesForDropdownHTMX() {
        String html = backofficeService.generateRolesDropdownHTML();
        sendHTMLResponse(OK, html);
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

    // =============================================
    // PAGINATION UTILITIES
    // =============================================

    /**
     * Generates pagination HTML controls for a given page result.
     * 
     * @param page The Page object containing pagination metadata
     * @param endpoint The endpoint name (users, jobs, logs, etc.)
     * @return HTML string with pagination controls
     */
    private String generatePaginationHTML(org.ruitx.jaws.types.Page<?> page, String endpoint) {
        if (page.getTotalElements() == 0) {
            return ""; // No pagination needed for empty results
        }

        StringBuilder html = new StringBuilder();
        
        // Pagination wrapper
        html.append("<tr>")
            .append("<td colspan=\"5\" class=\"bg-gray-50 px-6 py-3 border-t border-gray-200\">")
            .append("<div class=\"flex items-center justify-between\">")
            
            // Left side - Results info
            .append("<div class=\"flex-1 flex justify-between sm:hidden\">")
            .append("<span class=\"text-sm text-gray-700\">")
            .append("Showing ").append(page.getCurrentPage() * page.getPageSize() + 1)
            .append(" to ").append(Math.min((page.getCurrentPage() + 1) * page.getPageSize(), page.getTotalElements()))
            .append(" of ").append(page.getTotalElements()).append(" results")
            .append("</span>")
            .append("</div>")
            
            // Desktop view
            .append("<div class=\"hidden sm:flex-1 sm:flex sm:items-center sm:justify-between\">")
            .append("<div>")
            .append("<p class=\"text-sm text-gray-700\">")
            .append("Showing <span class=\"font-medium\">").append(page.getCurrentPage() * page.getPageSize() + 1).append("</span>")
            .append(" to <span class=\"font-medium\">").append(Math.min((page.getCurrentPage() + 1) * page.getPageSize(), page.getTotalElements())).append("</span>")
            .append(" of <span class=\"font-medium\">").append(page.getTotalElements()).append("</span> results")
            .append("</p>")
            .append("</div>")
            
            // Navigation controls
            .append("<div>")
            .append("<nav class=\"relative z-0 inline-flex rounded-md shadow-sm -space-x-px\" aria-label=\"Pagination\">")
            
            // Previous button
            .append("<button class=\"relative inline-flex items-center px-2 py-2 rounded-l-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50")
            .append(page.hasPrevious() ? "\" " : " cursor-not-allowed opacity-50\" disabled ")
            .append("hx-get=\"/htmx/backoffice/").append(endpoint).append("?page=").append(Math.max(0, page.getCurrentPage() - 1))
            .append("&size=").append(page.getPageSize()).append("\" ")
            .append("hx-headers='{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
            .append("hx-target=\"#").append(endpoint).append("-table-body\" ")
            .append("hx-swap=\"innerHTML transition:true\"")
            .append(page.hasPrevious() ? ">" : " style=\"pointer-events: none;\">")
            .append("<span class=\"sr-only\">Previous</span>")
            .append("<svg class=\"h-5 w-5\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
            .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M15.75 19.5L8.25 12l7.5-7.5\" />")
            .append("</svg>")
            .append("</button>");

        // Page numbers (show current page +/- 2)
        int startPage = Math.max(0, page.getCurrentPage() - 2);
        int endPage = Math.min(page.getTotalPages() - 1, page.getCurrentPage() + 2);
        
        // Show first page if not in range
        if (startPage > 0) {
            html.append("<button class=\"relative inline-flex items-center px-3 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-700 hover:bg-gray-50\" ")
                .append("hx-get=\"/htmx/backoffice/").append(endpoint).append("?page=0&size=").append(page.getPageSize()).append("\" ")
                .append("hx-headers='{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
                .append("hx-target=\"#").append(endpoint).append("-table-body\" ")
                .append("hx-swap=\"innerHTML transition:true\">")
                .append("1")
                .append("</button>");
                
            if (startPage > 1) {
                html.append("<span class=\"relative inline-flex items-center px-3 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-700\">...")
                    .append("</span>");
            }
        }

        // Page number buttons
        for (int i = startPage; i <= endPage; i++) {
            boolean isCurrent = i == page.getCurrentPage();
            html.append("<button class=\"relative inline-flex items-center px-3 py-2 border ")
                .append(isCurrent ? "border-primary-500 bg-primary-50 text-primary-600 z-10" : "border-gray-300 bg-white text-gray-700 hover:bg-gray-50")
                .append(" text-sm font-medium\" ")
                .append("hx-get=\"/htmx/backoffice/").append(endpoint).append("?page=").append(i).append("&size=").append(page.getPageSize()).append("\" ")
                .append("hx-headers='{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
                .append("hx-target=\"#").append(endpoint).append("-table-body\" ")
                .append("hx-swap=\"innerHTML transition:true\">")
                .append(i + 1)
                .append("</button>");
        }

        // Show last page if not in range
        if (endPage < page.getTotalPages() - 1) {
            if (endPage < page.getTotalPages() - 2) {
                html.append("<span class=\"relative inline-flex items-center px-3 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-700\">...")
                    .append("</span>");
            }
            
            html.append("<button class=\"relative inline-flex items-center px-3 py-2 border border-gray-300 bg-white text-sm font-medium text-gray-700 hover:bg-gray-50\" ")
                .append("hx-get=\"/htmx/backoffice/").append(endpoint).append("?page=").append(page.getTotalPages() - 1).append("&size=").append(page.getPageSize()).append("\" ")
                .append("hx-headers='{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
                .append("hx-target=\"#").append(endpoint).append("-table-body\" ")
                .append("hx-swap=\"innerHTML transition:true\">")
                .append(page.getTotalPages())
                .append("</button>");
        }

        // Next button
        html.append("<button class=\"relative inline-flex items-center px-2 py-2 rounded-r-md border border-gray-300 bg-white text-sm font-medium text-gray-500 hover:bg-gray-50")
            .append(page.hasNext() ? "\" " : " cursor-not-allowed opacity-50\" disabled ")
            .append("hx-get=\"/htmx/backoffice/").append(endpoint).append("?page=").append(Math.min(page.getTotalPages() - 1, page.getCurrentPage() + 1))
            .append("&size=").append(page.getPageSize()).append("\" ")
            .append("hx-headers='{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
            .append("hx-target=\"#").append(endpoint).append("-table-body\" ")
            .append("hx-swap=\"innerHTML transition:true\"")
            .append(page.hasNext() ? ">" : " style=\"pointer-events: none;\">")
            .append("<span class=\"sr-only\">Next</span>")
            .append("<svg class=\"h-5 w-5\" fill=\"none\" viewBox=\"0 0 24 24\" stroke-width=\"1.5\" stroke=\"currentColor\">")
            .append("<path stroke-linecap=\"round\" stroke-linejoin=\"round\" d=\"M8.25 4.5l7.5 7.5-7.5 7.5\" />")
            .append("</svg>")
            .append("</button>");

        html.append("</nav>")
            .append("</div>")
            .append("</div>")
            
            // Page size selector
            .append("<div class=\"flex items-center space-x-2 ml-4\">")
            .append("<label class=\"text-sm text-gray-700\">Show:</label>")
            .append("<select class=\"block w-20 px-2 py-1 border border-gray-300 rounded-md text-sm\" ")
            .append("hx-get=\"/htmx/backoffice/").append(endpoint).append("?page=0\" ")
            .append("hx-include=\"this\" ")
            .append("hx-headers='{\"Authorization\": \"Bearer \" + localStorage.getItem(\"auth_token\")}' ")
            .append("hx-target=\"#").append(endpoint).append("-table-body\" ")
            .append("hx-swap=\"innerHTML transition:true\" ")
            .append("name=\"size\">");

        for (int size : new int[]{10, 25, 50, 100}) {
            html.append("<option value=\"").append(size).append("\"")
                .append(size == page.getPageSize() ? " selected" : "")
                .append(">").append(size).append("</option>");
        }

        html.append("</select>")
            .append("</div>")
            .append("</div>")
            .append("</td>")
            .append("</tr>");

        return html.toString();
    }

}

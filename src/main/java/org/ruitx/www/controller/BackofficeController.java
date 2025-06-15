package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Tyr;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private final org.ruitx.jaws.components.Mimir logsDb;
    private final BackofficeService backofficeService;

    public BackofficeController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.authRepo = new AuthRepo();
        this.authService = new AuthService();
        this.authorizationService = new AuthorizationService();
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
        if (!isHTMX()) {
            sendHTMLResponse(METHOD_NOT_ALLOWED, "This endpoint is only accessible via HTMX");
            return;
        }

        String html = backofficeService.generateJobsTableHTML(getRequestContext());
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
    @Route(endpoint = "/htmx/backoffice/jobs/:id/delete", method = DELETE)
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
            getRequestContext()
        );
        sendHTMLResponse(OK, html);
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/job-stats", method = GET)
    public void getJobStatsHTMX() {
        String html = backofficeService.generateJobStatsHTML(getRequestContext());
        sendHTMLResponse(OK, html);
        return;
    }

    @AccessControl(login = true, role = "admin")
    @Route(endpoint = "/htmx/backoffice/logs", method = GET)
    public void listLogsHTMX() {
        if (!isHTMX()) {
            sendHTMLResponse(METHOD_NOT_ALLOWED, "This endpoint is only accessible via HTMX");
            return;
        }

        String html = backofficeService.generateLogsTableHTML(getRequestContext());
        sendHTMLResponse(OK, html);
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

}

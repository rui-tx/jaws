package org.ruitx.www.service;

import org.ruitx.jaws.components.freyr.Freyr;
import org.ruitx.jaws.types.Context;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.jaws.types.Row;
import org.ruitx.jaws.utils.JawsUtils;
import org.ruitx.www.model.auth.Role;
import org.ruitx.www.model.auth.User;
import org.ruitx.www.model.auth.UserRole;
import org.ruitx.www.repository.AuthRepo;
import org.ruitx.www.repository.BackofficeRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ruitx.jaws.components.Hermod;
import org.ruitx.jaws.components.Yggdrasill;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BackofficeService - Business logic for backoffice operations
 * 
 * Handles all backoffice business logic, HTML generation for HTMX responses,
 * and coordinates between multiple repositories and services.
 */
public class BackofficeService {

    private static final Logger log = LoggerFactory.getLogger(BackofficeService.class);
    
    private final BackofficeRepo backofficeRepo;
    private final AuthRepo authRepo;
    private final AuthorizationService authorizationService;
    private final Freyr jobQueue;

    public BackofficeService() {
        this.backofficeRepo = new BackofficeRepo();
        this.authRepo = new AuthRepo();
        this.authorizationService = new AuthorizationService();
        this.jobQueue = Freyr.getInstance();
    }

    /**
     * Creates the base context with user information and role-based navigation visibility.
     */
    public Map<String, String> getBaseContext(User user) {
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

    // =============================================
    // JOB MANAGEMENT
    // =============================================

    /**
     * Generate jobs table HTML for HTMX response
     */
    public String generateJobsTableHTML(String pageParam, String sizeParam, String sortParam, 
                                      String directionParam, String statusFilter, String typeFilter) {
        try {
            PageRequest pageRequest = backofficeRepo.parsePageRequest(pageParam, sizeParam, sortParam, directionParam);
            Page<Row> jobPage = backofficeRepo.getJobsPage(pageRequest, statusFilter, typeFilter);
            List<Row> jobs = jobPage.getContent();

            StringBuilder html = new StringBuilder();
            
            if (jobs.isEmpty()) {
                html.append(generateEmptyJobsRow(statusFilter));
            } else {
                for (Row job : jobs) {
                    html.append(generateJobTableRow(job));
                }
            }

            // Add pagination controls
            html.append(generatePaginationHTML(jobPage, "jobs"));

            return html.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate jobs table HTML: {}", e.getMessage(), e);
            return "<tr><td colspan=\"6\">Error loading jobs</td></tr>";
        }
    }

    /**
     * Process job reprocessing request
     */
    public String processJobReprocessing(String jobId) {
        try {
            var jobRow = backofficeRepo.getJobById(jobId);
            
            if (jobRow.isEmpty()) {
                return generateErrorRow("Job not found", 6);
            }

            String status = jobRow.get().getString("status").orElse("");
            
            if ("DEAD_LETTER".equals(status)) {
                return handleDeadLetterJobReprocessing(jobId);
            } else if ("FAILED".equals(status)) {
                return handleFailedJobReprocessing(jobId);
            } else {
                return generateErrorRow("Job status is " + status + ". Only FAILED or DEAD_LETTER jobs can be reprocessed.", 6);
            }
            
        } catch (Exception e) {
            log.error("Failed to reprocess job: {}", e.getMessage(), e);
            return generateWarningRow("An error occurred while reprocessing the job.", 6);
        }
    }

    /**
     * Generate job statistics HTML using Thymeleaf template
     */
    public String generateJobStatsHTML(Yggdrasill.RequestContext requestContext) {
        try {
            Map<String, Object> stats = jobQueue.getStatistics();
            
            // Create a list of stat objects for the template
            List<Map<String, Object>> statItems = List.of(
                Map.of(
                    "title", "Total Jobs",
                    "value", stats.getOrDefault("totalJobs", 0),
                    "color", "primary",
                    "icon", "database"
                ),
                Map.of(
                    "title", "Completed",
                    "value", stats.getOrDefault("completedJobs", 0),
                    "color", "green",
                    "icon", "check-circle"
                ),
                Map.of(
                    "title", "Failed",
                    "value", stats.getOrDefault("failedJobs", 0),
                    "color", "red",
                    "icon", "exclamation-circle"
                ),
                Map.of(
                    "title", "Queue Size",
                    "value", (Integer) stats.getOrDefault("parallelQueueSize", 0) + (Integer) stats.getOrDefault("sequentialQueueSize", 0),
                    "color", "yellow",
                    "icon", "clock"
                )
            );
                
            Context templateContext = Context.builder()
                .with("stats", statItems)
                .build();   
            
            // Process template using Hermod with the stats content template
            return Hermod.processTemplate("components/stats-card/stats-content.html", requestContext.getRequest(), requestContext.getResponse(), templateContext);
            
        } catch (Exception e) {
            log.error("Failed to get job statistics: {}", e.getMessage(), e);
            return "<div class=\"bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded\">Error loading job statistics</div>";
        }
    }

    // =============================================
    // USER MANAGEMENT  
    // =============================================

    /**
     * Generate users table HTML for HTMX response
     */
    public String generateUsersTableHTML(String pageParam, String sizeParam, String sortParam, String directionParam) {
        try {
            PageRequest pageRequest = backofficeRepo.parsePageRequest(pageParam, sizeParam, sortParam, directionParam);
            Page<Row> userPage = backofficeRepo.getUsersPage(pageRequest);
            List<User> users = backofficeRepo.transformRowsToUsers(userPage.getContent());

            StringBuilder html = new StringBuilder();

            if (users.isEmpty()) {
                html.append(generateEmptyUsersRow());
            } else {
                for (User user : users) {
                    html.append(generateUserTableRow(user));
                }
            }

            // Add pagination controls
            html.append(generatePaginationHTML(userPage, "users"));

            return html.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate users table HTML: {}", e.getMessage(), e);
            return "<tr><td colspan=\"5\">Error loading users</td></tr>";
        }
    }

    // =============================================
    // ROLE MANAGEMENT
    // =============================================

    /**
     * Generate roles table HTML for HTMX response
     */
    public String generateRolesTableHTML(String pageParam, String sizeParam, String sortParam, String directionParam) {
        try {
            PageRequest pageRequest = backofficeRepo.parsePageRequest(pageParam, sizeParam, sortParam, directionParam);
            Page<Row> rolePage = backofficeRepo.getRolesPage(pageRequest);
            
            // Transform Row objects to Role objects
            List<Role> roles = rolePage.getContent().stream()
                    .map(row -> Role.builder()
                            .id(row.getInt("id").orElse(0))
                            .name(row.getString("name").orElse(""))
                            .description(row.getString("description").orElse(null))
                            .createdAt(row.getLong("created_at").orElse(null))
                            .updatedAt(row.getLong("updated_at").orElse(null))
                            .build())
                    .toList();
        
            StringBuilder html = new StringBuilder();
            
            if (roles.isEmpty()) {
                html.append(generateEmptyRolesRow());
            } else {
                for (Role role : roles) {
                    html.append(generateRoleTableRow(role));
                }
            }
            
            html.append(generatePaginationHTML(rolePage, "roles"));
            
            return html.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate roles table HTML: {}", e.getMessage(), e);
            return "<tr><td colspan=\"5\">Error loading roles</td></tr>";
        }
    }

    /**
     * Generate user-roles assignment table HTML for HTMX response
     */
    public String generateUserRolesTableHTML() {
        try {
            List<UserRole> userRoles = authorizationService.getAllUserRoles();
            
            StringBuilder html = new StringBuilder();
            
            if (userRoles.isEmpty()) {
                html.append(generateEmptyUserRolesRow());
            } else {
                for (UserRole userRole : userRoles) {
                    html.append(generateUserRoleTableRow(userRole));
                }
            }
            
            return html.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate user-roles table HTML: {}", e.getMessage(), e);
            return "<tr><td colspan=\"5\">Error loading user roles</td></tr>";
        }
    }

    /**
     * Generate dropdown HTML for users list
     */
    public String generateUsersDropdownHTML() {
        try {
            List<User> users = authRepo.getAllUsers();
            
            StringBuilder html = new StringBuilder();
            html.append("<option value=\"\">Select a user...</option>");
            
            for (User user : users) {
                html.append(String.format("""
                    <option value="%d">%s %s (@%s)</option>
                    """,
                    user.id(),
                    user.firstName() != null ? user.firstName() : "",
                    user.lastName() != null ? user.lastName() : "",
                    user.user()
                ));
            }
            
            return html.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate users dropdown HTML: {}", e.getMessage(), e);
            return "<option value=\"\">Error loading users</option>";
        }
    }

    /**
     * Generate dropdown HTML for roles list
     */
    public String generateRolesDropdownHTML() {
        try {
            List<Role> roles = backofficeRepo.getAllRoles();
            
            StringBuilder html = new StringBuilder();
            html.append("<option value=\"\">Select a role...</option>");
            
            for (Role role : roles) {
                html.append(String.format("""
                    <option value="%d">%s - %s</option>
                    """,
                    role.id(),
                    role.name(),
                    role.description() != null ? role.description() : "No description"
                ));
            }
            
            return html.toString();
            
        } catch (Exception e) {
            log.error("Failed to generate roles dropdown HTML: {}", e.getMessage(), e);
            return "<option value=\"\">Error loading roles</option>";
        }
    }

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
    private String generatePaginationHTML(Page<?> page, String endpoint) {
        if (page.getTotalElements() == 0) {
            return ""; // No pagination needed for empty results
        }

        StringBuilder html = new StringBuilder();
        
        // Pagination wrapper - dynamic colspan based on endpoint
        int colspan = getTableColspan(endpoint);
        html.append("<tr>")
            .append("<td colspan=\"").append(colspan).append("\" class=\"bg-gray-50 px-6 py-3 border-t border-gray-200\">")
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

    // =============================================
    // PRIVATE HELPER METHODS
    // =============================================

    private String handleDeadLetterJobReprocessing(String jobId) {
        String newJobId = jobQueue.getDeadLetterQueue().manualRetry(jobId, true);
        if (newJobId != null) {
            return generateSuccessRow("Job reprocessed successfully. New Job ID: " + newJobId, 6);
        } else {
            return generateErrorRow("Failed to reprocess job from Dead Letter Queue.", 6);
        }
    }

    private String handleFailedJobReprocessing(String jobId) {
        int updated = backofficeRepo.updateJobForReprocessing(jobId);
        if (updated > 0) {
            return generateSuccessRow("Job has been reset to PENDING status and will be retried.", 6);
        } else {
            return generateErrorRow("Failed to update job status for retry.", 6);
        }
    }

    private String generateJobTableRow(Row job) {
        String status = job.getString("status").orElse("UNKNOWN");
        String statusClass = getJobStatusClass(status);
        Long createdAt = job.getLong("created_at").orElse(0L);
        Long completedAt = job.getLong("completed_at").orElse(null);
        String jobId = job.getString("id").orElse("");
        
        return String.format("""
            <tr class="hover:bg-gray-50">
                <td class="px-6 py-4 whitespace-nowrap">
                    <span class="px-2 py-1 text-xs font-medium rounded-full %s">
                        %s
                    </span>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                    <div class="text-sm font-medium text-gray-900">%s</div>
                    <div class="text-sm text-gray-500">ID: %s</div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    Priority: %d
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    %s
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    %s
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <div class="flex space-x-2 justify-end">
                        <a href="/backoffice/jobs/%s" 
                           class="inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-primary-600 hover:bg-primary-700"
                           title="View job details">
                            <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                                <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                                <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                            </svg>
                        </a>
                        %s
                    </div>
                </td>
            </tr>
            """,
            statusClass,
            status,
            job.getString("type").orElse("Unknown"),
            jobId,
            job.getInt("priority").orElse(5),
            JawsUtils.formatUnixTimestamp(createdAt),
            completedAt != null ? JawsUtils.formatUnixTimestamp(completedAt) : "In progress",
            jobId,
            generateJobActionButtons(status, jobId)
        );
    }

    private String getJobStatusClass(String status) {
        return switch (status) {
            case "PENDING" -> "bg-yellow-100 text-yellow-800";
            case "PROCESSING" -> "bg-blue-100 text-blue-800";
            case "COMPLETED" -> "bg-green-100 text-green-800";
            case "FAILED" -> "bg-red-100 text-red-800";
            case "TIMEOUT" -> "bg-red-100 text-red-800";
            case "RETRY_SCHEDULED" -> "bg-yellow-100 text-yellow-800";
            case "DEAD_LETTER" -> "bg-gray-100 text-gray-800";
            default -> "bg-gray-100 text-gray-800";
        };
    }

    private String generateJobActionButtons(String status, String jobId) {
        StringBuilder buttons = new StringBuilder();
        
        // Add reprocess button for failed/timeout jobs
        if ("FAILED".equals(status) || "TIMEOUT".equals(status) || "DEAD_LETTER".equals(status)) {
            buttons.append(String.format("""
                <button
                    class="ml-2 inline-flex items-center px-2 py-1 border border-transparent rounded text-xs font-medium text-white bg-green-600 hover:bg-green-700"
                    hx-post="/htmx/backoffice/jobs/%s/reprocess"
                    hx-headers='{"Authorization": "Bearer " + localStorage.getItem("auth_token")}'
                    hx-target="#jobs-table-body"
                    hx-swap="innerHTML transition:true"
                    title="Reprocess job">
                    ↻
                </button>
                """, jobId));
        }
        
        // Add delete button
        buttons.append(String.format("""
            <button
                class="ml-2 inline-flex items-center px-2 py-1 border border-transparent rounded text-xs font-medium text-white bg-red-600 hover:bg-red-700"
                hx-post="/htmx/backoffice/jobs/%s/delete"
                hx-confirm="Are you sure you want to delete this job?"
                hx-headers='{"Authorization": "Bearer " + localStorage.getItem("auth_token")}'
                hx-target="#jobs-table-body"
                hx-swap="innerHTML transition:true"
                title="Delete job">
                ×
            </button>
            """, jobId));
        
        return buttons.toString();
    }

    private String generateEmptyJobsRow(String statusFilter) {
        StringBuilder html = new StringBuilder();
        html.append("<tr>")
            .append("<td colspan=\"6\" class=\"px-6 py-4 text-center text-gray-500\">")
            .append("<div class=\"flex flex-col items-center justify-center py-8\">")
            .append("<p class=\"text-sm text-gray-600 mb-1\">No jobs found</p>");
            
        if (statusFilter != null && !statusFilter.isEmpty()) {
            html.append("<p class=\"text-xs text-gray-500\">No jobs with status: ").append(statusFilter).append("</p>");
        } else {
            html.append("<p class=\"text-xs text-gray-500\">The job queue is currently empty</p>");
        }
        
        html.append("</div>")
            .append("</td>")
            .append("</tr>");
            
        return html.toString();
    }

    private String generateUserTableRow(User user) {
        return String.format("""
            <tr class="hover:bg-gray-50">
                <td class="px-6 py-4 whitespace-nowrap">
                    <div class="flex items-center">
                        <div class="h-10 w-10 flex-shrink-0">
                            <img class="h-10 w-10 rounded-full" src="%s" alt="">
                        </div>
                        <div class="ml-4">
                            <div class="text-sm font-medium text-gray-900">%s</div>
                            <div class="text-sm text-gray-500">%s</div>
                        </div>
                    </div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap">
                    <div class="text-sm text-gray-900">%s %s</div>
                    <div class="text-sm text-gray-500">%s</div>
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    %s
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-sm text-gray-500">
                    %s
                </td>
                <td class="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                    <a href="/backoffice/profile/%d" 
                       class="inline-flex items-center px-3 py-1.5 border border-transparent rounded-md text-xs font-medium text-white bg-primary-600 hover:bg-primary-700"
                       title="View profile">
                        <svg class="h-3 w-3" fill="none" viewBox="0 0 24 24" stroke-width="1.5" stroke="currentColor">
                            <path stroke-linecap="round" stroke-linejoin="round" d="M2.036 12.322a1.012 1.012 0 010-.639C3.423 7.51 7.36 4.5 12 4.5c4.638 0 8.573 3.007 9.963 7.178.07.207.07.431 0 .639C20.577 16.49 16.64 19.5 12 19.5c-4.638 0-8.573-3.007-9.963-7.178z" />
                            <path stroke-linecap="round" stroke-linejoin="round" d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                        </svg>
                        View
                    </a>
                </td>
            </tr>
            """,
            user.profilePicture() != null && !user.profilePicture().isEmpty() ? 
                user.profilePicture() : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg",
            user.user(),
            user.email() != null ? user.email() : "No email",
            user.firstName() != null ? user.firstName() : "",
            user.lastName() != null ? user.lastName() : "",
            user.phoneNumber() != null ? user.phoneNumber() : "No phone",
            user.createdAt() != null ? JawsUtils.formatUnixTimestamp(user.createdAt()) : "Unknown",
            user.lastLogin() != null ? JawsUtils.formatUnixTimestamp(user.lastLogin(), "yyyy-MM-dd HH:mm") : "Never",
            user.id()
        );
    }

    private String generateRoleTableRow(Role role) {
        int userCount = authorizationService.getUserCountForRole(role.id());
        
        return String.format("""
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
        );
    }

    private String generateUserRoleTableRow(UserRole userRole) {
        // Get user and role details
        User user = authRepo.getUserById(userRole.userId().longValue()).orElse(null);
        Role role = authorizationService.getRoleById(userRole.roleId()).orElse(null);
        User assignedByUser = userRole.assignedBy() != null ? 
            authRepo.getUserById(userRole.assignedBy().longValue()).orElse(null) : null;
        
        if (user != null && role != null) {
            return String.format("""
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
            );
        } else {
            return "<tr><td colspan=\"5\">Invalid user role assignment</td></tr>";
        }
    }

    private String generateSuccessRow(String message, int colspan) {
        return String.format("""
            <tr class="bg-green-50">
                <td colspan="%d" class="px-6 py-4">
                    <div class="bg-green-100 border border-green-400 text-green-700 px-4 py-3 rounded">
                        <strong>Success!</strong> %s
                    </div>
                </td>
            </tr>
            """, colspan, message);
    }

    private String generateErrorRow(String message, int colspan) {
        return String.format("""
            <tr class="bg-red-50">
                <td colspan="%d" class="px-6 py-4">
                    <div class="bg-red-100 border border-red-400 text-red-700 px-4 py-3 rounded">
                        <strong>Error!</strong> %s
                    </div>
                </td>
            </tr>
            """, colspan, message);
    }

    private String generateWarningRow(String message, int colspan) {
        return String.format("""
            <tr class="bg-yellow-50">
                <td colspan="%d" class="px-6 py-4">
                    <div class="bg-yellow-100 border border-yellow-400 text-yellow-700 px-4 py-3 rounded">
                        <strong>Warning!</strong> %s
                    </div>
                </td>
            </tr>
            """, colspan, message);
    }

    private String generateEmptyUsersRow() {
        return """
            <tr>
                <td colspan="5" class="px-6 py-4 text-center text-gray-500">
                    <div class="flex flex-col items-center justify-center py-8">
                        <p class="text-sm text-gray-600 mb-1">No users found</p>
                        <p class="text-xs text-gray-500">The user list is currently empty</p>
                    </div>
                </td>
            </tr>
            """;
    }

    private String generateEmptyRolesRow() {
        return """
            <tr>
                <td colspan="5" class="px-6 py-4 text-center text-gray-500">
                    No roles found. Create your first role!
                </td>
            </tr>
            """;
    }

    private String generateEmptyUserRolesRow() {
        return """
            <tr>
                <td colspan="5" class="px-6 py-4 text-center text-gray-500">
                    No user roles found. Assign user roles!
                </td>
            </tr>
            """;
    }

    private String generateStatCard(String title, Object value, String color, String icon) {
        return String.format("""
            <div class="bg-white rounded-lg shadow border border-gray-200 p-6">
                <div class="flex items-center justify-between">
                    <div>
                        <p class="text-sm font-medium text-gray-600">%s</p>
                        <p class="text-2xl font-bold text-%s-600">%s</p>
                    </div>
                </div>
            </div>
            """, title, color, value);
    }

    private int getTableColspan(String endpoint) {
        return switch (endpoint) {
            case "jobs" -> 6;    // Status, Type, Priority, Created, Completed, Actions
            case "users" -> 5;   // Avatar+Name, Full Name, Created, Last Login, Actions  
            case "roles" -> 5;   // Role, Description, Users, Created, Actions
            case "logs" -> 6;    // Level, Message, Logger, Timestamp, Thread, Actions
            default -> 5;        // Default fallback
        };
    }
} 
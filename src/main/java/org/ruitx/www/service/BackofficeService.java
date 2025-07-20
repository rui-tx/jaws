package org.ruitx.www.service;

import org.ruitx.jaws.types.Context;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.www.repository.BackofficeRepo;

import java.util.Arrays;
import java.util.Map;

public class BackofficeService {

    private final BackofficeRepo backofficeRepo;

    public BackofficeService() {
        this.backofficeRepo = new BackofficeRepo();
    }

    /**
     * Retrieves the context for the backoffice dashboard.
     *
     * @return Context containing data for the backoffice dashboard.
     */
    public Context getBackofficeContext() {
        Map<String, Object> data = Map.of(
                "currentPage", "dashboard"
        );

        return Context.builder()
                .with("data", data)
                .build();
    }

    /**
     * Retrieves the total number of users in the system.
     *
     * @return Context containing user count data.
     */
    public Context getUserCount() {
        Map<String, Object> data = Map.of(
                "icon", "icon-users",
                "label", "Total Users",
                "value", backofficeRepo.getAllUsers().size()
        );

        return Context.builder()
                .with("data", data)
                .build();
    }

    /**
     * Retrieves the total number of user sessions in the system.
     *
     * @return Context containing user session count data.
     */
    public Context getUserSessionCount() {
        Map<String, Object> data = Map.of(
                "icon", "icon-users",
                "label", "Total Users",
                "value", backofficeRepo.getAllUserSessions().size()
        );

        return Context.builder()
                .with("data", data)
                .build();
    }

    /**
     * Retrieves paginated log table data.
     *
     * @param pageRequest Pagination parameters
     * @return Context containing paginated log table data.
     */
    public Context getPaginatedLogTableData(PageRequest pageRequest) {
        Page<Map<String, String>> logPage = backofficeRepo.getPaginatedLogs(pageRequest);
        Map<String, Object> data = Map.of(
                "headers", Arrays.asList("Timestamp", "Level", "Message", "Source"),
                "rows", logPage.getContent(),
                "caption", "Recent System Logs",
                "actions", Arrays.asList("view"),
                "pagination", Map.of(
                        "currentPage", logPage.getCurrentPage(),
                        "totalPages", logPage.getTotalPages(),
                        "totalElements", logPage.getTotalElements(),
                        "pageSize", logPage.getPageSize(),
                        "hasNext", logPage.hasNext(),
                        "hasPrevious", logPage.hasPrevious()
                )
        );

        return Context.builder()
                .with("data", data)
                .build();
    }

    /**
     * Retrieves the context for the logs page.
     *
     * @return Context containing data for the logs page.
     */
    public Context getLogsPageContext() {
        Map<String, Object> data = Map.of(
                "currentPage", "logs",
                "logLevels", backofficeRepo.getDistinctLogLevels(),
                "logSources", backofficeRepo.getDistinctLogSources()
        );

        return Context.builder()
                .with("data", data)
                .build();
    }

    /**
     * Retrieves filtered paginated log table data for the logs page.
     *
     * @param pageRequest Pagination parameters
     * @param level Filter by log level (null for all levels)
     * @param source Filter by logger/source (null for all sources)
     * @param search Search term in message (null for no search)
     * @return Context containing filtered paginated log table data.
     */
    public Context getFilteredLogTableData(PageRequest pageRequest, String level, String source, String search) {
        Page<Map<String, String>> logPage = backofficeRepo.getFilteredLogs(pageRequest, level, source, search);
        Map<String, Object> data = Map.of(
                "headers", Arrays.asList("Timestamp", "Level", "Message", "Source"),
                "rows", logPage.getContent(),
                "caption", "System Logs",
                "actions", Arrays.asList("view"),
                "pagination", Map.of(
                        "currentPage", logPage.getCurrentPage(),
                        "totalPages", logPage.getTotalPages(),
                        "totalElements", logPage.getTotalElements(),
                        "pageSize", logPage.getPageSize(),
                        "hasNext", logPage.hasNext(),
                        "hasPrevious", logPage.hasPrevious()
                ),
                "filters", Map.of(
                        "level", level != null ? level : "",
                        "source", source != null ? source : "",
                        "search", search != null ? search : ""
                )
        );

        return Context.builder()
                .with("data", data)
                .build();
    }

    /**
     * Retrieves a single log entry for detail view.
     *
     * @param logId The ID of the log entry to retrieve
     * @return Context containing the log entry data.
     */
    public Context getLogDetailContext(String logId) {
        Map<String, String> logEntry = backofficeRepo.getLogById(logId);
        
        if (logEntry == null) {
            Map<String, Object> data = Map.of(
                    "error", "Log entry not found",
                    "logId", logId
            );
            return Context.builder()
                    .with("data", data)
                    .build();
        }

        Map<String, Object> data = Map.of(
                "currentPage", "log-detail",
                "logEntry", logEntry
        );

        return Context.builder()
                .with("data", data)
                .build();
    }
}
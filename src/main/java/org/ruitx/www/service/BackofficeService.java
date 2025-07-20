package org.ruitx.www.service;

import org.ruitx.jaws.types.Context;
import org.ruitx.jaws.types.Page;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.www.repository.BackofficeRepo;

import java.util.Arrays;
import java.util.List;
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
     * Retrieves the log table data.
     *
     * @param amount The amount to retrieve.
     * @return Context containing log table data.
     */
    public Context getLogTableData(int amount) {
        List<Map<String, String>> logRows = backofficeRepo.getTopLogs(amount);

        Map<String, Object> data = Map.of(
                "headers", Arrays.asList("Timestamp", "Level", "Message", "Source"),
                "rows", logRows,
                "caption", "Recent System Logs",
                "actions", Arrays.asList("view")
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
}
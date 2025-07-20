package org.ruitx.www.service;

import org.ruitx.jaws.types.Context;
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
     * Retrieves the log table data for the top 25 log entries.
     *
     * @return Context containing log table data.
     */
    public Context getLogTableData() {
        // Get real log data from the database
        List<Map<String, String>> logRows = backofficeRepo.getTopLogs(25);

        Map<String, Object> data = Map.of(
                "headers", Arrays.asList("Timestamp", "Level", "Message", "Source"),
                "rows", logRows,
                "caption", "Recent System Logs",
                "actions", Arrays.asList("view", "delete", "refresh")
        );

        try {
            // add a delay to simulate log retrieval
            Thread.sleep(1000); // Simulate delay for log retrieval
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt(); // Restore interrupted status
            throw new RuntimeException("Log retrieval interrupted", e);
        }

        return Context.builder()
                .with("data", data)
                .build();
    }
}
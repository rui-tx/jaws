package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.www.service.BackofficeService;
import org.tinylog.Logger;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.BAD_REQUEST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.HTML;
import static org.ruitx.jaws.types.ParamType.PATH;
import static org.ruitx.jaws.types.ParamType.QUERY;

public class BackofficeController extends Bragi {

    private static final String API_ENDPOINT = "/backoffice";
    private static final String HTMX_ENDPOINT = API_ENDPOINT + "/htmx";

    private final BackofficeService backofficeService;

    public BackofficeController() {
        this.backofficeService = new BackofficeService();
    }

    // region HTML endpoints

    /**
     * Renders the main backoffice dashboard page.
     * Accessible via GET request to /backoffice.
     */
    @Route(endpoint = API_ENDPOINT, method = GET, responseType = HTML)
    public void renderBackoffice() {
        sendHTML(
                OK,
                render("backoffice/main.html", backofficeService.getBackofficeContext()));
    }

    // endregion

    // region HTMX endpoints

    /**
     * HTMX endpoint to fetch the backoffice dashboard.
     * Accessible via GET request to /backoffice/htmx/dashboard.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/user-count", method = GET, responseType = HTML)
    public void getUserCount() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        sendHTML(
                OK,
                render(
                        "backoffice/components/card/stats-card-view.html",
                        backofficeService.getUserCount()));

    }

    /**
     * HTMX endpoint to fetch the user session count.
     * Accessible via GET request to /backoffice/htmx/usersession-count.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/usersession-count", method = GET, responseType = HTML)
    public void getUserSessionCount() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        sendHTML(
                OK,
                render("backoffice/components/card/stats-card-view.html",
                        backofficeService.getUserSessionCount()));

    }

    /**
     * HTMX endpoint to fetch paginated log table data.
     * Accessible via GET request to /backoffice/htmx/logs-paginated.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/logs", method = GET, responseType = HTML)
    public void getPaginatedLogTable() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        // Parse pagination parameters with defaults
        int page = 0;
        int size = 10;

        try {
            String pageParam = get("page", QUERY);
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid page number: {}", get("page", QUERY));
        }

        try {
            String sizeParam = get("size", QUERY);
            if (sizeParam != null) {
                size = Integer.parseInt(sizeParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid size number: {}", get("size", QUERY));
        }

        PageRequest pageRequest = new PageRequest(page, size);

        sendHTML(
                OK,
                render("backoffice/components/table/table-view.html",
                        backofficeService.getPaginatedLogTableData(pageRequest)));

    }

    /**
     * Renders the logs page.
     * Accessible via GET request to /backoffice/logs.
     */
    @Route(endpoint = API_ENDPOINT + "/logs", method = GET, responseType = HTML)
    public void renderLogsPage() {
        sendHTML(
                OK,
                render("backoffice/logs.html", backofficeService.getLogsPageContext()));
    }

    /**
     * Renders the log detail page.
     * Accessible via GET request to /backoffice/logs/{id}.
     */
    @Route(endpoint = API_ENDPOINT + "/logs/:id", method = GET, responseType = HTML)
    public void renderLogDetail() {
        String logId = get("id", PATH);
        if (logId == null) {
            sendFail(BAD_REQUEST, "Log ID is required.");
            return;
        }

        sendHTML(
                OK,
                render("backoffice/log-detail.html", backofficeService.getLogDetailContext(logId)));
    }

    /**
     * HTMX endpoint to fetch filtered paginated log table data.
     * Accessible via GET request to /backoffice/htmx/logs-filtered.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/logs-filtered", method = GET, responseType = HTML)
    public void getFilteredLogTable() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        // Parse pagination parameters with defaults
        int page = 0;
        int size = 25;

        try {
            String pageParam = get("page", QUERY);
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid page number: {}", get("page", QUERY));
        }

        try {
            String sizeParam = get("size", QUERY);
            if (sizeParam != null) {
                size = Integer.parseInt(sizeParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid size number: {}", get("size", QUERY));
        }

        // Parse filter parameters
        String level = get("level", QUERY);
        String source = get("source", QUERY);
        String search = get("search", QUERY);

        PageRequest pageRequest = new PageRequest(page, size);

        sendHTML(
                OK,
                render("backoffice/components/table/table-view.html",
                        backofficeService.getFilteredLogTableData(pageRequest, level, source, search)));

    }

    // endregion

    // region User Management Endpoints

    /**
     * HTMX endpoint to fetch paginated user table data.
     * Accessible via GET request to /backoffice/htmx/users.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/users", method = GET, responseType = HTML)
    public void getPaginatedUserTable() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        // Parse pagination parameters with defaults
        int page = 0;
        int size = 25;

        try {
            String pageParam = get("page", QUERY);
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid page number: {}", get("page", QUERY));
        }

        try {
            String sizeParam = get("size", QUERY);
            if (sizeParam != null) {
                size = Integer.parseInt(sizeParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid size number: {}", get("size", QUERY));
        }

        PageRequest pageRequest = new PageRequest(page, size);

        sendHTML(
                OK,
                render("backoffice/components/table/table-view.html",
                        backofficeService.getPaginatedUserTableData(pageRequest)));

    }

    /**
     * Renders the users page.
     * Accessible via GET request to /backoffice/users.
     */
    @Route(endpoint = API_ENDPOINT + "/users", method = GET, responseType = HTML)
    public void renderUsersPage() {
        sendHTML(
                OK,
                render("backoffice/users.html", backofficeService.getUsersPageContext()));
    }

    /**
     * Renders the user detail page.
     * Accessible via GET request to /backoffice/users/{id}.
     */
    @Route(endpoint = API_ENDPOINT + "/users/:id", method = GET, responseType = HTML)
    public void renderUserDetail() {
        String userId = get("id", PATH);
        if (userId == null) {
            sendFail(BAD_REQUEST, "User ID is required.");
            return;
        }

        sendHTML(
                OK,
                render("backoffice/user-detail.html", backofficeService.getUserDetailContext(userId)));
    }

    /**
     * HTMX endpoint to fetch filtered paginated user table data.
     * Accessible via GET request to /backoffice/htmx/users-filtered.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/users-filtered", method = GET, responseType = HTML)
    public void getFilteredUserTable() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        // Parse pagination parameters with defaults
        int page = 0;
        int size = 25;

        try {
            String pageParam = get("page", QUERY);
            if (pageParam != null) {
                page = Integer.parseInt(pageParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid page number: {}", get("page", QUERY));
        }

        try {
            String sizeParam = get("size", QUERY);
            if (sizeParam != null) {
                size = Integer.parseInt(sizeParam);
            }
        } catch (NumberFormatException e) {
            Logger.warn("Invalid size number: {}", get("size", QUERY));
        }

        // Parse filter parameters
        String status = get("status", QUERY);
        String role = get("role", QUERY);
        String search = get("search", QUERY);

        PageRequest pageRequest = new PageRequest(page, size);

        sendHTML(
                OK,
                render("backoffice/components/table/table-view.html",
                        backofficeService.getFilteredUserTableData(pageRequest, status, role, search)));

    }

    // endregion

}
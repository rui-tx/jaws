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
import static org.ruitx.jaws.types.ParamType.QUERY;
import static org.ruitx.jaws.types.ParamType.PATH;

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
    @Route(endpoint = API_ENDPOINT + "/logs/{id}", method = GET, responseType = HTML)
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

}
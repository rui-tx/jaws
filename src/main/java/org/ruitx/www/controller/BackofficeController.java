package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.www.service.BackofficeService;
import org.tinylog.Logger;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseCode.BAD_REQUEST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.HTML;
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
     * HTMX endpoint to fetch the log table data.
     * Accessible via GET request to /backoffice/htmx/logs.
     */
    @Route(endpoint = HTMX_ENDPOINT + "/logs", method = GET, responseType = HTML)
    public void getLogTable() {
        if (!getRequestContext().isHTMX()) {
            sendFail(BAD_REQUEST, "This endpoint is only accessible via HTMX.");
        }

        int amount = 5;
        try {
            String pageParam = get("amount", QUERY);
            amount = Integer.parseInt(pageParam);
        } catch (NumberFormatException e) {
            Logger.warn("Invalid amount number: {}", get("amount", QUERY));
        }

        sendHTML(
                OK,
                render("backoffice/components/table/table-view.html",
                        backofficeService.getLogTableData(amount)));

    }

    // endregion

}
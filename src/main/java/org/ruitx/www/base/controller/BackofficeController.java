package org.ruitx.www.base.controller;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.BAD_REQUEST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.HTML;
import static org.ruitx.jaws.types.ParamType.PATH;
import static org.ruitx.jaws.types.ParamType.QUERY;

import java.util.Map;
import java.util.function.Function;
import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.interfaces.AccessControl;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.Context;
import org.ruitx.jaws.types.PageRequest;
import org.ruitx.www.base.dto.auth.UserUpdateRequest;
import org.ruitx.www.base.service.AuthService;
import org.ruitx.www.base.service.BackofficeService;
import org.tinylog.Logger;

public class BackofficeController extends Bragi {

  private static final String API_ENDPOINT = "/backoffice";
  private static final String HTMX_ENDPOINT = API_ENDPOINT + "/htmx";

  private final BackofficeService backofficeService;
  private final AuthService authService;

  public BackofficeController() {
    this.backofficeService = new BackofficeService();
    this.authService = new AuthService();
  }

  /**
   * Renders the backoffice login page. Accessible via GET request to /backoffice/login.
   */
  @Route(endpoint = API_ENDPOINT + "/login", method = GET, responseType = HTML)
  public void renderBackofficeLogin() {
    sendHTML(OK, render("backoffice/login.html"));
  }

  /**
   * Renders the main backoffice dashboard page. Accessible via GET request to /backoffice.
   */
  @AccessControl(login = true)
  @Route(endpoint = API_ENDPOINT, method = GET, responseType = HTML)
  public void renderBackoffice() {
    sendHTML(
        OK,
        render(
            "backoffice/main.html",
            backofficeService.getBackofficeContext()));
  }

  /**
   * HTMX endpoint to create a new toast.
   */
  @AccessControl(login = true)
  @Route(
      endpoint = HTMX_ENDPOINT + "/toast/:title/:description",
      method = GET,
      responseType = HTML,
      htmx = true)
  public void getToast() {
    String title = get("title") != null
        ? get("title")
        : "title";
    String description = get("description") != null
        ? get("description")
        : "desc";

    Context ctx = Context.builder()
        .with("title", title)
        .with("description", description)
        .build();

    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/toast/toast.html",
            "toast",
            ctx));
  }

  /**
   * HTMX endpoint to fetch the user count.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT + "/user-count", method = GET, responseType = HTML, htmx = true)
  public void getUserCount() {
    Context svc = backofficeService.getUserCount();
    Map<String, Object> data = (Map<String, Object>) svc.context().get("data");
    String iconClass = (String) data.get("icon");

    Context ctx = Context.builder()
        .with("iconClass", iconClass)
        .with("label", data.get("label"))
        .with("value", data.get("value"))
        .with("color", "blue")
        .build();

    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/card/card.html",
            "stats-card",
            ctx));
  }

  /**
   * HTMX endpoint to fetch the user session count. Accessible via GET request to
   * /backoffice/htmx/usersession-count.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT
      + "/usersession-count", method = GET, responseType = HTML, htmx = true)
  public void getUserSessionCount() {
    Context svc = backofficeService.getUserSessionCount();
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) svc.context().get("data");
    String iconClass = (String) data.get("icon");

    Context ctx = Context.builder()
        .with("iconClass", iconClass)
        .with("label", data.get("label"))
        .with("value", data.get("value"))
        .with("color", "blue")
        .build();

    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/card/card.html",
            "stats-card",
            ctx));
  }

  /**
   * HTMX endpoint to fetch paginated log table data. Accessible via GET request to
   * /backoffice/htmx/logs.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT + "/logs", method = GET, responseType = HTML, htmx = true)
  public void getPaginatedLogTable() {

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

    Context svc = backofficeService.getPaginatedLogTableData(pageRequest);
    Map<String, Object> data = (Map<String, Object>) svc.context().get("data");
    Context ctx = Context.builder()
        .with("headers", data.get("headers"))
        .with("rows", data.get("rows"))
        .with("caption", data.get("caption"))
        .with("actions", data.get("actions"))
        .with("pagination", data.get("pagination"))
        .with("data", data)
        .build();

    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/table/table.html",
            "table-logs-with-pagination",
            ctx));

  }

  /**
   * Renders the logs page. Accessible via GET request to /backoffice/logs.
   */
  @AccessControl(login = true)
  @Route(endpoint = API_ENDPOINT + "/logs", method = GET, responseType = HTML)
  public void renderLogsPage() {
    sendHTML(
        OK,
        render(
            "backoffice/logs.html",
            backofficeService.getLogsPageContext()));
  }

  /**
   * Renders the log detail page. Accessible via GET request to /backoffice/logs/{id}.
   */
  @AccessControl(login = true)
  @Route(endpoint = API_ENDPOINT + "/logs/:id", method = GET, responseType = HTML)
  public void renderLogDetail() {
    String logId = get("id", PATH);
    if (logId == null) {
      sendFail(BAD_REQUEST, "Log ID is required.");
      return;
    }

    sendHTML(
        OK,
        render(
            "backoffice/log-detail.html",
            backofficeService.getLogDetailContext(logId)));
  }

  /**
   * HTMX endpoint to fetch filtered paginated log table data. Accessible via GET request to
   * /backoffice/htmx/logs-filtered.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT
      + "/logs-filtered", method = GET, responseType = HTML, htmx = true)
  public void getFilteredLogTable() {

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

    Context svc = backofficeService.getFilteredLogTableData(pageRequest, level, source, search);
    Map<String, Object> data = (Map<String, Object>) svc.context().get("data");
    Context ctx = Context.builder()
        .with("headers", data.get("headers"))
        .with("rows", data.get("rows"))
        .with("caption", data.get("caption"))
        .with("actions", data.get("actions"))
        .with("pagination", data.get("pagination"))
        .with("data", data)
        .build();

    sendHTML(OK,
        renderFragment("backoffice/components/table/table.html", "table-logs-with-pagination",
            ctx));

  }

  /**
   * HTMX endpoint to fetch paginated user table data. Accessible via GET request to
   * /backoffice/htmx/users.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT + "/users", method = GET, responseType = HTML, htmx = true)
  public void getPaginatedUserTable() {

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

    Context svc = backofficeService.getPaginatedUserTableData(pageRequest);
    Map<String, Object> data = (Map<String, Object>) svc.context().get("data");
    Context ctx = Context.builder()
        .with("headers", data.get("headers"))
        .with("rows", data.get("rows"))
        .with("caption", data.get("caption"))
        .with("actions", data.get("actions"))
        .with("pagination", data.get("pagination"))
        .with("data", data)
        .build();

    sendHTML(OK,
        renderFragment("backoffice/components/table/table.html", "table-users-with-pagination",
            ctx));

  }

  /**
   * Renders the users page. Accessible via GET request to /backoffice/users.
   */
  @AccessControl(login = true)
  @Route(endpoint = API_ENDPOINT + "/users", method = GET, responseType = HTML)
  public void renderUsersPage() {
    sendHTML(
        OK,
        render(
            "backoffice/users.html",
            backofficeService.getUsersPageContext()));
  }

  /**
   * Renders the user detail page. Accessible via GET request to /backoffice/users/{id}.
   */
  @AccessControl(login = true)
  @Route(endpoint = API_ENDPOINT + "/users/:id", method = GET, responseType = HTML)
  public void renderUserDetail() {
    String userId = get("id", PATH);
    if (userId == null) {
      sendFail(BAD_REQUEST, "User ID is required.");
      return;
    }

    sendHTML(
        OK,
        render(
            "backoffice/user-detail.html",
            backofficeService.getUserDetailContext(userId)));
  }

  /**
   * HTMX endpoint to fetch filtered paginated user table data. Accessible via GET request to
   * /backoffice/htmx/users-filtered.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT + "/users-filtered", method = GET, responseType = HTML,
      htmx = true)
  public void getFilteredUserTable() {

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

    Context svc = backofficeService.getFilteredUserTableData(pageRequest, status, role, search);
    Map<String, Object> data = (Map<String, Object>) svc.context().get("data");
    Context ctx = Context.builder()
        .with("headers", data.get("headers"))
        .with("rows", data.get("rows"))
        .with("caption", data.get("caption"))
        .with("actions", data.get("actions"))
        .with("pagination", data.get("pagination"))
        .with("data", data)
        .build();

    sendHTML(OK,
        renderFragment("backoffice/components/table/table.html", "table-users-with-pagination",
            ctx));

  }

  /**
   * HTMX endpoint: render user detail EDIT fragment for given user id.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT
      + "/users/:id/edit", method = GET, responseType = HTML, htmx = true)
  public void renderUserEditFragment() {
    String userId = get("id", PATH);
    if (userId == null) {
      sendFail(BAD_REQUEST, "User ID is required.");
      return;
    }

    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/user/user-detail-edit.html",
            "user-edit",
            backofficeService.getUserDetailContext(userId))
    );
  }

  /**
   * HTMX endpoint: render user detail VIEW fragment for given user id.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT
      + "/users/:id/view", method = GET, responseType = HTML, htmx = true)
  public void renderUserViewFragment() {
    String userId = get("id", PATH);
    if (userId == null) {
      sendFail(BAD_REQUEST, "User ID is required.");
      return;
    }

    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/user/user-detail-view.html",
            "user-detail",
            backofficeService.getUserDetailContext(userId))
    );
  }

  /**
   * HTMX endpoint: handle user update (PATCH-like via POST) and return refreshed VIEW fragment.
   */
  @AccessControl(login = true)
  @Route(endpoint = HTMX_ENDPOINT + "/users/:id", method = POST, responseType = HTML, htmx = true)
  public void updateUserFromForm() {
    String userIdStr = get("id", PATH);
    if (userIdStr == null) {
      sendFail(BAD_REQUEST, "User ID is required.");
      return;
    }

    int userId;
    try {
      userId = Integer.parseInt(userIdStr);
    } catch (NumberFormatException e) {
      sendFail(BAD_REQUEST, "Invalid User ID.");
      return;
    }

    // Helpers to normalize input
    Function<String, String> nn = (s) -> (s == null || s.isBlank()) ? null : s;
    Function<String, Long> toLong = (s) -> {
      try {
        return (s == null || s.isBlank()) ? null : Long.parseLong(s);
      } catch (Exception ex) {
        return null;
      }
    };
    Function<String, Integer> toInt = (s) -> {
      try {
        return (s == null || s.isBlank()) ? null : Integer.parseInt(s);
      } catch (Exception ex) {
        return null;
      }
    };

    String password = nn.apply(get("password"));
    String email = nn.apply(get("email"));
    String firstName = nn.apply(get("firstName"));
    String lastName = nn.apply(get("lastName"));
    Long birthdate = toLong.apply(get("birthdateEpoch"));
    String gender = nn.apply(get("gender"));
    String phoneNumber = nn.apply(get("phoneNumber"));
    String profilePicture = nn.apply(get("profilePicture"));
    String bio = nn.apply(get("bio"));
    String location = nn.apply(get("location"));
    String website = nn.apply(get("website"));
    Integer isActive = toInt.apply(get("isActive")); // expect 1 or 0
    Long lockoutUntil = toLong.apply(get("lockoutUntilEpoch"));

    UserUpdateRequest request = new UserUpdateRequest(
        password,
        email,
        firstName,
        lastName,
        birthdate,
        gender,
        phoneNumber,
        profilePicture,
        bio,
        location,
        website,
        isActive,
        lockoutUntil
    );

    // Perform update
    authService.updateUser(userId, request);

    // Return refreshed VIEW fragment
    sendHTML(
        OK,
        renderFragment(
            "backoffice/components/user/user-detail-view.html",
            "user-detail",
            backofficeService.getUserDetailContext(userIdStr))
    );
  }
}
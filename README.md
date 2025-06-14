# JAWS

<div align="center">
<a href="https://github.com/rui-tx/jaws">
    <img src="https://openmoji.org/data/color/svg/1F988.svg" alt="Logo" width="128" height="128">
</a>
</div>

JAWS (Just Another Web Server) is a web server implemented in Java

## Features

Among other features, these are the main ones

- **Static Files**: Serve HTML static files
- **API Builder**: Build simple APIs
- **Dynamic Routing**: Simple dynamic endpoint routing
- **HTML Parsing**: HTML can be enhanced with dynamic values
- **Database System**: A (very) basic ORM using SQLite
- **JWT**: Generate access and refresh tokens with ease
- **Robust HTTP Server**: Built on Eclipse Jetty 
- **Middleware System**: Extensible middleware for cross-cutting concerns
- **Async**: A job system for async processing


## Setup

### Prerequisites

- Java 17 or higher
- Maven (for dependency management and building the project)

### Build

To build the project, run the following:

```sh
git clone https://github.com/rui-tx/jaws.git
cd jaws
mvn clean package
```

The compiled JAR file will be located in the `target/jaws-[version].jar`.

> You can also use to just run it ```mvn clean compile exec:java -Dexec.mainClass=org.ruitx.Jaws```

## Running JAWS

To start JAWS, use the following:

```sh
WWWPATH="<www_path>" PORT="<port>" java -jar jaws[version].jar
```

### Example:

```sh
WWWPATH="/Downloads/www" PORT="8080" java -jar jaws[version].jar
```

> Note: Please use absolute paths for the WWWPATH environment variable.

## Usage

After starting JAWS, you can access the server using a web browser or an HTTP client like `curl`.

```sh
curl http://localhost:8080
```

> Note: You can test the online version of **JAWS** at the link in the about section. The login for the backoffice is **admin** - **admin1234!**

## About JAWS

JAWS is built with a modular system. Every module is responsible for one aspect of the server.

- `Bifrost`: Middlware implementation
- `Bragi`: The base for all controllers. Contains methods to respond to the client
- `Freyr`: Asynchronous job queue system with priority queuing and retry mechanisms
- `Heimdall`: A file watcher that monitors changes in the specified directory
- `Hermod`: HTML parser that handles template processing and page assembly
- `Yggdrassil`: The unified HTTP server with integrated request handling, middleware support, and direct controller routing
- `Mimir`: Database interface for SQLite. Acts as a mini basic ORM
- `Njord`: Dynamic router that routes requests to controllers
- `Norns`: Scheduler for tasks, like a cron job
- `Odin`: Module responsible for starting all the other modules
- `Tyr`:  JWT handling
- `Volundr`: Makes the response header for all the responses

### Architecture

JAWS uses a typical server request/response logic, like this:

**Sync request**
```
[HTTP Request] → [Yggdrassil (Jetty)] → [Middleware Chain] → [Route Discovery] → [Controller Execution] → [Response]
```

**Async request**
```
[HTTP Request] → [Yggdrassil (Jetty)] → [Middleware Chain] → [Route Discovery] → [Controller] → [Job Submission to Freyr] → [Immediate Response with Job ID]
                                                                                                          *   
            *       
[Job Persistence to Mimir(db)] → [Freyr Queue System] → [Job Processing]
     ↓
[Job Execution] → [Result Storage] → [Status Updates in Database]
     ↓
[Client Polling] → [Status/Result Endpoints] → [Job Results Retrieved]
```

### Bifrost

Bifrost is a middleware system for handling cross-cutting concerns:

- **LoggingMiddleware**: Automatic request/response logging
- **CorsMiddleware**: Cross-Origin Resource Sharing support
- **AuthMiddleware**: JWT authentication for protected routes

Example middleware:
```java
public class AuthMiddleware implements Middleware {
    @Override
    public boolean handle(RequestContext context, MiddlewareChain chain) {
        if (!isAuthenticated(context)) {
            sendUnauthorized(context);
            return false; // Stop the chain
        }
        return chain.next(); // Continue to next middleware
    }
}
```

### Bragi

All controllers can and should extend this class. It provides convenient methods for accessing request data and sending responses. The class automatically adapts to work with the current request context.

**Example**

```java

@Route(endpoint = API_ENDPOINT + "posts", responseType = JSON)
public void testGetExternalAPI() {
    String url = "https://jsonplaceholder.typicode.com/posts";
    APIResponse<List<Post>> response = callAPI(url, LIST_POST);

    if (!response.success()) {
        sendErrorResponse(response.code(), response.info());
        return;
    }

    sendSucessfulResponse(response.code(), response.data());
}
```

```callApi```, ```sendErrorResponse``` and ```sendSucessfulResponse``` are all methods from ```Bragi```

### Freyr

```Freyr``` is an asynchronous job queue system that provides powerful background job processing capabilities. It supports both parallel and sequential job execution, priority-based processing, automatic retry mechanisms with exponential backoff and job monitoring and statistics.

**Key Features:**
- **Dual Queue System**: Separate parallel and sequential job processing queues
- **Priority-Based Processing**: Jobs are processed based on their priority levels
- **Retry Management**: Automatic retry with exponential backoff and dead letter queue
- **Persistent Job Storage**: Jobs are persisted to SQLite database for reliability

**Job Types:**
- **Parallel Jobs**: Execute concurrently using multiple worker threads
- **Sequential Jobs**: Execute one at a time in FIFO order for tasks requiring strict ordering

**Example**

```java
public class ExternalApiJob extends BaseJob {
    public static final String JOB_TYPE = "external-api-call";
    
    public ExternalApiJob(Map<String, Object> payload) {
        super(JOB_TYPE, 5, 2, 30000L, payload); // priority 5, 2 retries, 30s timeout
    }

    @Override
    public void execute() throws Exception {
        String url = getString("url");
        // Perform API call
        APIResponse<List<Post>> response = callExternal(url);
        
        if (response.success()) {
            JobResultStore.storeSuccess(getId(), responseData);
        } else {
            JobResultStore.storeError(getId(), response.code(), response.info());
        }
    }
}
```

```java
public class JobRegistryConfig {
    public static void registerJobs() {
        JobRegistry registry = JobRegistry.getInstance();
        registry.registerJob("external-api-call", ExternalApiJob.class);
        registry.registerJob("parallel-ping", ParallelPingJob.class);
        registry.registerJob("sequential-ping", SequentialPingJob.class);
    }
}
```

```java
@Route(endpoint = API_ENDPOINT + "submit-job", method = POST, responseType = JSON)
public void submitJob() {
    Map<String, Object> payload = Map.of(
        "url", "https://api.example.com/data",
        "requestedBy", getCurrentToken()
    );
    
    Freyr jobQueue = Freyr.getInstance();
    String jobId = jobQueue.submit(new ExternalApiJob(payload));
    
    sendSucessfulResponse(OK, Map.of("jobId", jobId, "status", "submitted"));
}

@Route(endpoint = API_ENDPOINT + "job-status/:id", method = GET, responseType = JSON)
public void getJobStatus() {
    String jobId = getPathParam("id");
    Freyr jobQueue = Freyr.getInstance();
    
    JobStatus status = jobQueue.getJobStatus(jobId);
    JobResult result = jobQueue.getJobResult(jobId);
    
    Map<String, Object> response = new HashMap<>();
    response.put("jobId", jobId);
    response.put("status", status.name());
    if (result != null) {
        response.put("result", result);
    }
    
    sendSucessfulResponse(OK, response);
}
```

The job system automatically handles persistence, retry logic and provides monitoring capabilities. Failed jobs are automatically retried with exponential backoff, and permanently failed jobs are moved to a dead letter queue for manual inspection.

### Heimdall

```Heimdall``` is a file watcher that monitors the specified directory for any file changes. As of now it just logs the
changes

### Hermod

```Hermod``` is responsible for HTML template processing and page assembly using **Thymeleaf**. It provides powerful template processing capabilities with proper servlet context integration, URL resolution, template inheritance, and enhanced performance through caching.

**Key Features:**
- **Thymeleaf Integration**: Full Thymeleaf template engine support
- **WebContext Support**: Proper servlet context for web applications  
- **Template Caching**: Configurable caching with TTL for development and production modes
- **Fragment Inclusion**: Support for Thymeleaf fragments and template inheritance
- **URL Building**: Robust URL resolution with `@{/path}` syntax
- **Utility Functions**: Built-in utility methods for common template operations
- **Thread-Safe**: ThreadLocal template variables for concurrent request handling

**Template Syntax Examples:**

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>JAWS - Just Another Web Server</title>
</head>
<body>
    <!-- URL Building -->
    <a th:href="@{/backoffice/login.html}">Backoffice</a>
    
    <!-- Fragment Inclusion -->
    <div th:insert="~{docs/staticfiles.html}">Loading guide...</div>
    
    <!-- Variable Display -->
    <p th:text="${apiPath}">API Path</p>
    
    <!-- Conditional Rendering -->
    <div th:if="${currentUser}">
        <span th:text="${currentUser}">User Name</span>
    </div>
    
</body>
</html>
```

**Controller Example**

```java
    @AccessControl(login = true)
    @Route(endpoint = "/backoffice", method = GET)
    public void renderIndex() {
        User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

        Map<String, String> context = new HashMap<>();
        context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
        context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
        context.put("profilePicture", user.profilePicture() != null && !user.profilePicture().isEmpty()
                ? user.profilePicture()
                : "https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg");
        setContext(context);

        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, DASHBOARD_PAGE));
    }
```

### Mimir

```Mimir``` is the database / ORM that we can use to interface with an SQLite database. It handles all the db
connections, and it has a basic transaction handling logic. It returns an object called ```Row``` that can be then map
to a model

**Example**

```java
public void updateLastLogin(Integer userId) {
    db.executeSql(
            "UPDATE USER SET last_login = ? WHERE id = ?",
            Date.from(Instant.now()),
            userId
    );
}

public Optional<User> getUserById(Long id) {
    Row row = db.getRow("SELECT * FROM USER WHERE id = ?", id);
    if (row == null) {
        return Optional.empty();
    }
    return User.fromRow(row);
}

public List<User> getAllUsers() {
    List<Row> rows = db.getRows("SELECT * FROM USER ORDER BY created_at DESC");
    return rows.stream()
            .map(User::fromRow)
            .flatMap(Optional::stream)
            .toList();
}
```

```java
public APIResponse<List<User>> listUsers() {
    return APIResponse.success(OK,
            authRepo.getAllUsers().stream()
                    .map(User::defaultView)
                    .toList());
}
```

The ```User``` is the data model for the table ```USER```, and it's responsible for implementing the map from Row to
itself. Creating the tables is made with a SQL schema file

### Njord

```Njord``` is a dynamic router class that routes requests to controllers. Annotating and method with ```@Route``` will
be picked up by ```Njord```. It's is not *completely* dynamic because we need to before hand config the classes that we
check for these annotations

**Example**

```java

public class RoutesConfig {

    // All the dynamic routes that will be registered
    // File paths are not needed here, as they are handled by Yggdrasill

    public static final List<Object> ROUTES = List.of(
            new AuthController(),
            new APIController(),
            new BackofficeController()
    );
}
```

```java

@Route(endpoint = API_ENDPOINT + "ping", responseType = JSON)
public void ping() {
    sendSucessfulResponse(OK, apiService.ping());
}
```

```java

@AccessControl(login = true)
@Route(endpoint = "/backoffice/profile/:id", method = GET)
public void renderUserProfile() {
    String userId = getPathParam("id");
    User currentUser = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();
    User user = authRepo.getUserById(Long.parseLong(userId)).get();
    // ...
}
```

```@Route``` is also responsible for setting the request type, like ```GET``` or ```POST```. The path params are also
possible with this annotation, like in the example. It is very simplistic, no types, for example.

### Norns

This module is a classic scheduler, like a cron tab.

**Example**

```java
private static Thread createNorns() {
    Norns norns = Norns.getInstance();
    norns.registerTask(
            "clean-old-sessions",
            () -> new AuthService().cleanOldSessions(),
            5,
            TimeUnit.MINUTES
    );
    return new Thread(norns, "norns");
}
```

There is not much more to say, just that it does in its own thread

### Odin

```Odin``` is where JAWS is started. The modules are started here. Nothing more to say, just a init class

```java
private static void startComponents() {
    ExecutorService executor = Executors.newCachedThreadPool();

    createMimir();
    createNjord();
    List<Thread> threads = Arrays.asList(
            createYggdrasill(),
            createHeimdall(),
            createNorns());

    for (Thread thread : threads) {
        executor.execute(thread);
    }

    createHel(executor);
}
```

### Tyr

```Tyr``` is responsible for JWT handling. As of now a simple login system with access and refresh token can be used

**Example**

```java

@Route(endpoint = API_ENDPOINT + "login", method = POST, responseType = JSON)
public void loginUser(LoginRequest request) {
    String username = request.user();
    String password = request.password();

    String userAgent = getHeaders().get("User-Agent");
    String ipAddress = getClientIpAddress();

    APIResponse<TokenResponse> response = authService.loginUser(
            username,
            password,
            userAgent,
            ipAddress
    );

    if (!response.success()) {
        sendErrorResponse(response.code(), response.info());
        return;
    }

    sendSucessfulResponse(OK, response.data());
}
```

```java

public APIResponse<TokenResponse> loginUser(String username, String password, String userAgent, String ipAddress) {
    if (username == null || password == null) {
        return APIResponse.error(BAD_REQUEST, "User / password is missing");
    }

    Optional<User> user = authRepo.getUserByUsername(username.toLowerCase());
    if (user.isEmpty()) {
        return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
    }

    if (!BCrypt.verifyer()
            .verify(password.toCharArray(), user.get().passwordHash()).verified) {
        return APIResponse.error(UNAUTHORIZED, "Credentials are invalid");
    }

    Tyr.TokenPair tokenPair = Tyr.createTokenPair(
            user.get().id().toString(),
            userAgent,
            ipAddress
    );

    authRepo.updateLastLogin(user.get().id());
    return APIResponse.success(OK, TokenResponse.fromTokenPair(tokenPair));
}
```

```java

@AccessControl(login = true)
@Route(endpoint = "/backoffice", method = GET)
public void renderIndex() {
    // ...
}
```

In this example ```createTokenPair``` is a ```Tyr``` method that generate a new pair of access and refresh tokens. In
combination with the annotation ```@AccessControl(login = true)``` we can easily block endpoints that need
authentication. Profiles are not implemented, but one day they will (let's hope)

### Volundr

This module is just a simple builder that constructs all the response headers. Nothing to see here

### Yggdrasill

This module is the heart of JAWS. `Yggdrasill` is responsible for handling HTTP requests, processing them through middleware chains, discovering routes, and executing controller methods.

Key features:
- **Jetty-based**: Built on the proven Eclipse Jetty server for reliability and performance
- **Integrated request handling**: Direct processing without bridge layers
- **Middleware support**: Extensible middleware chain for cross-cutting concerns
- **Route discovery**: Automatic route detection and parameter injection
- **Static file serving**: Efficiently serves static resources
- **Thread management**: Handles concurrent connections with proper resource management
- **Exception handling**: Comprehensive error handling and response management

The `RequestContext` contains all the relevant information about the request, including headers, body, JWT tokens, path parameters, and query parameters. Controllers access this through the `Bragi` base class methods.

**Example request flow:**

```java
// Yggdrasill processes request through middleware chain
LoggingMiddleware -> CorsMiddleware -> AuthMiddleware -> Route Discovery -> Controller

// Controller handles business logic
@Route(endpoint = "/api/users", method = GET)
public void getUsers() {
    // Access request data through Bragi methods
    String token = getCurrentToken();
    Map<String, String> headers = getHeaders();
    
    // Process and respond
    sendSucessfulResponse(OK, userService.getAllUsers());
}
```

## Notes

### Why Norse Mythology?

I say to that: Why not have fun while learning and not taking everything so seriously? 😁

### Serious? Another webserver?

This is a hobby project where I don't really take it too seriously. Again, to me, this is about learning and having fun
while doing it. I use it as a *thing* to apply new technics or ideas, even if they are bad. For example, this is where I
first tried the famous *vibe coding*

## License

This project is licensed under the MIT License.

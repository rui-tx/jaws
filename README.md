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

## About JAWS

JAWS is built with a modular system. Every module is responsible for one aspect of the server.

- `Bragi`: The base for all controllers. Contains methods to respond to the client
- `Heimdall`: A file watcher that monitors changes in the specified directory
- `Hermod`: HTML parser that handles template processing and page assembly
- `Mimir`: Database interface for SQLite. Acts as a mini basic ORM
- `Njord`: Dynamic router that routes requests to controllers
- `Norns`: Scheduler for tasks, like a cron job
- `Odin`: Module responsible for starting all the other modules
- `Tyr`:  JWT handling
- `Volundr`: Makes the response header for all the responses
- `Yggdrasill`: Main server class that handles incoming HTTP requests

### Bragi

All controllers can and should extend this class. It wraps one of the most important class, the
```Yggdrasill.RequestHandler```. The ```RequestHandler``` contains all the relevant information about the request, like
the path or query params, among other important data. We can use ```RequestHandler``` without extending ```Bragi``` in
the controller, this module is just for ease of use

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

### Heimdall

```Heimdall``` is a file watcher that monitors the specified directory for any file changes. As of now it just logs the
changes

### Hermod

```Hermod``` is responsible for HTML parsing. With it, we can have 2 things: Render partial HTML files inside another
HTML
file and have dynamic values inside HTML files.

**Example**

```html

<main>
    <section>
        <a href="/backoffice/index.html">Jaws backoffice</a>
    </section>
    <section>
        <h2>Quick Start Guide</h2>
        {{renderPartial("docs/staticfiles.html")}}
        <hr/>
        {{renderPartial("docs/placeholders.html")}}
    </section>
</main>
```

```html

<a class="navbar-link is-arrowless">
    <div class="is-user-avatar">
        <img alt="{{currentUser}}" src="https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg">
    </div>
    <div class="is-user-name"><span>{{currentUser}}</span></div>
    <span class="icon"><i class="mdi mdi-chevron-down"></i></span>
</a>
```

```java

@AccessControl(login = true)
@Route(endpoint = "/backoffice", method = GET)
public void renderIndex() {
    User user = authRepo.getUserById(Long.parseLong(Tyr.getUserIdFromJWT(getCurrentToken()))).get();

    Map<String, String> context = new HashMap<>();
    context.put("userId", Tyr.getUserIdFromJWT(getCurrentToken()));
    context.put("currentUser", getCurrentToken().isEmpty() ? "-" : user.firstName() + " " + user.lastName());
    setContext(context);

    sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, BODY_HTML_PATH));
}
```

- ```renderPartial``` is a command that gets the partial inside ```{{}}``` and renders it. It is **recursive** so we
  need
  to be careful
- ```{{currentUser}}``` is a variable that is set at the controller level. When ```Hermod``` is called, it will change
  the variable inside the curly brackets with the value set in the context. In this example, the ```userId``` is also
  available to be rendered in the HTML

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

This module is the heart of JAWS. ```Yggdrasill``` is responsible for decoding the request, passing it to the
controller and then responding with the correct data. It's also here where we get the header, body, JWT token and other
request data. It contains its own thread pool, as it is one thread per connection

**Example**

```java

private void processRequest() {
    initializeStreams();
    readHeaders();
    readBody();
    checkRequestAndSendResponse();
    closeSocket();
}
```

It's not an example, but that method shows in a simplified way the flow that ```Yggdrasill``` uses.

## Notes

### Why Norse Mythology?

I say to that: Why not have fun while learning and not taking everything so seriously? 😁

### Serious? Another webserver?

This is a hobby project where I don't really take it too seriously. Again, to me, this is about learning and having fun
while doing it. I use it as a *thing* to apply new technics or ideas, even if they are bad. For example, this is where I
first tried the famous *vibe coding*

## License

This project is licensed under the MIT License.


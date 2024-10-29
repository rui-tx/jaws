# JAWS
<div align="center">
<a href="https://github.com/rui-tx/jaws">
    <img src="https://openmoji.org/data/color/svg/1F988.svg" alt="Logo" width="128" height="128">
</a>
</div>
  
JAWS (Just Another Web Server) is a web server implemented in Java. It can serve static files, process
basic HTTP requests and comes with:
- A file watcher component to monitor changes in the specified resources directory
- A way to have variables in the HTML and change them on-the-fly, like a mini template engine
- Dynamic routing, so it's easier to create new endpoints

## Features

- **Serve Static Files**: Serves HTML, CSS, JS, and other static files from a specified directory.
- **HTTP Request Handling**: Supports basic HTTP methods including GET, POST, PUT, PATCH, and DELETE.
- **Dynamic Routing**: Create dynamic routes using annotations for flexible request handling.
- **HTML Parsing**: Create more dynamic HTML with custom variables that are changed when it is rendered
- **File Watching**: Monitors a specified directory for file changes and logs these changes.
- **Aspect-Oriented Capable**: Uses AspectJ to log different stages of request processing and deal with exceptions.

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

## Project Structure

- `org.ruitx.server.components.Yggdrasill`: Main server class that handles incoming HTTP requests.
    - `Yggdrasill.RequestHandler`: Inner class that processes different types of HTTP requests.
- `org.ruitx.server.components.Heimdall`: A file watcher that monitors changes in the specified directory.
- `org.ruitx.server.components.Hephaestus`: Represents an HTTP response header.
- `org.ruitx.server.components.Hermes`: HTML parser.
- `org.ruitx.server.components.Njord`: Dynamic router that routes requests to controllers
- `org.ruitx.server.aspects.LoggingAspect`: AspectJ-based logging aspect to log request processing events.
- `org.ruitx.server.aspects.ExceptionAspect`: AspectJ-based exception handler.

### Yggdrasill

Yggdrasill is the main server class responsible for accepting incoming client connections and handling HTTP requests. It
includes an inner RequestHandler class that processes different types of HTTP requests (GET, POST, PUT, PATCH, DELETE,
etc.).

### Heimdall

Heimdall is a file watcher that monitors the specified directory for any file changes. It currently logs the changes
detected in the console.

### Hephaestus

Hephaestus is a class that represents an HTTP response header. It includes functionality to build and convert response
headers to byte arrays or strings.

### Hermes

Hermes is a utility class that contains methods for parsing HTML files.

### Njord

Njord is a dynamic router class that routes requests to controllers. It currently only works for GET commands.

### LoggingAspect

LoggingAspect uses AspectJ for logging different stages of request processing. It logs before the request is processed,
after the request is successfully processed, and if an exception occurs during the request processing.

### ExceptionAspect

ExceptionAspect uses AspectJ for dealing with exceptions during runtime.

## License

This project is licensed under the MIT License.

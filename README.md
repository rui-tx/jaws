# JAWS Server

JAWS (Just Another Web Server) is a lightweight web server implemented in Java. It can serve static files and process
basic HTTP requests such as GET, POST, PUT, PATCH, and DELETE. It also includes a file watcher component to monitor
changes in the specified resources directory and logs request processing events.

## Features

- **Serve Static Files**: Serves HTML, CSS, JS, and other static files from a specified directory.
- **HTTP Request Handling**: Supports basic HTTP methods including GET, POST, PUT, PATCH, and DELETE.
- **File Watching**: Monitors a specified directory for file changes and logs these changes.
- **Aspect-Oriented Logging**: Uses AspectJ to log different stages of request processing.

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
- `org.ruitx.server.aspects.LoggingAspect`: AspectJ-based logging aspect to log request processing events.

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

### LoggingAspect

LoggingAspect uses AspectJ for logging different stages of request processing. It logs before the request is processed,
after the request is successfully processed, and if an exception occurs during the request processing.

## License

This project is licensed under the MIT License.

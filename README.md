# JAWS Server

JAWS (Just Another Web Server) is a lightweight web server implemented in Java. It can serve static files and process
basic HTTP requests such as GET, POST, and PUT. It also includes a file watcher component to monitor changes in the
specified resources directory and logs request processing events.

## Features

- **Serve Static Files**: Serves HTML, CSS, JS, and other static files from a specified directory.
- **HTTP Request Handling**: Supports basic HTTP methods including GET, POST, PUT, PATCH, and DELETE.
- **File Watching**: Monitors a specified directory for file changes and logs these changes.
- **Aspect-Oriented Logging**: Uses AspectJ to log different stages of request processing.

## Project Structure

- `org.ruitx.server.Yggdrasill`: Main server class which handles incoming HTTP requests.
- `org.ruitx.server.components.Heimdall`: A file watcher that monitors changes in the specified directory.
- `org.ruitx.server.aspects.LoggingAspect`: AspectJ-based logging aspect to log request processing events.

## Setup

### Prerequisites

- Java 11 or higher
- Maven (for dependency management and building the project)

### Build

To build the project, run the following command:

```sh
mvn clean install
```

### Running the Server

To start the server, use the following command (replace <port> and <resources_path> with your desired port number and
the path to the resource directory, respectively):

```
java -cp target/jaws-1.0-SNAPSHOT.jar org.ruitx.server.Yggdrasill
```

Example:

```
PORT=<8080> WWWPATH=<"/Downloads/www"> java -cp target/jaws-1.0-SNAPSHOT.jar org.ruitx.server.Yggdrasill
```

### Configuration

The server can be configured by passing the desired port number and the path to the resources directory where your
static files are located.

### Components

####Yggdrasill
Yggdrasill is the main server class responsible for accepting incoming client connections and handling HTTP requests. It
includes an inner RequestHandler class that processes different types of HTTP requests (GET, POST, PUT, etc.).

#### Heimdall

Heimdall is a file watcher that monitors the specified directory for any file changes. It currently logs the changes
detected in the console.

#### LoggingAspect

LoggingAspect uses AspectJ for logging different stages of request processing. It logs before the request is processed,
after the request is successfully processed, and if an exception occurs during the request processing.

### Example Usage

Start the server with the desired port and resources directory.

Access the server using a web browser or an HTTP client like curl:

```
curl http://localhost:8080
```

License
This project is licensed under the MIT License.
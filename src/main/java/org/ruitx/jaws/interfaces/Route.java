package org.ruitx.jaws.interfaces;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseType.HTML;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseType;

/**
 * Annotation to define a route for handling HTTP requests. It specifies the endpoint, request
 * method, and response type.
 */
@Retention(RetentionPolicy.RUNTIME)
public @interface Route {

  /**
   * The endpoint for the route, e.g., "/api/users". This is the path that the server will listen
   * to.
   *
   * @return the endpoint string
   */
  String endpoint();

  /**
   * The HTTP method for the route, e.g., GET, POST. This defines how the server will respond to
   * requests made to the endpoint.
   *
   * @return the request method
   */
  RequestType method() default GET;

  /**
   * The expected response type for the route, e.g., HTML, JSON. This indicates the format of the
   * response that the server will return.
   *
   * @return the response type
   */
  ResponseType responseType() default HTML;
  
  /**
   * Indicates if this route requires HTMX headers to be present in the request.
   * If true, requests without HTMX headers will be rejected.
   * 
   * @return true if HTMX headers are required, false otherwise
   */
  boolean htmx() default false;
}

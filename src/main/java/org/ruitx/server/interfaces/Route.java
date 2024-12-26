package org.ruitx.server.interfaces;

import org.ruitx.server.strings.RequestType;
import org.ruitx.server.strings.ResponseType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.ruitx.server.strings.RequestType.GET;
import static org.ruitx.server.strings.ResponseType.HTML;

@Retention(RetentionPolicy.RUNTIME)
public @interface Route {
    String endpoint();

    RequestType method() default GET;

    ResponseType responseType() default HTML;
}

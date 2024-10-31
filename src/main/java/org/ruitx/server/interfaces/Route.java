package org.ruitx.server.interfaces;

import org.ruitx.server.strings.RequestType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.ruitx.server.strings.RequestType.GET;

@Retention(RetentionPolicy.RUNTIME)
public @interface Route {
    String endpoint();

    RequestType method() default GET;
}

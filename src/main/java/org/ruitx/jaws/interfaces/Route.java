package org.ruitx.jaws.interfaces;

import org.ruitx.jaws.strings.RequestType;
import org.ruitx.jaws.strings.ResponseType;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.ResponseType.HTML;

@Retention(RetentionPolicy.RUNTIME)
public @interface Route {
    String endpoint();

    RequestType method() default GET;

    ResponseType responseType() default HTML;
}

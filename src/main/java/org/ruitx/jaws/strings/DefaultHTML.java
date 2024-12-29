package org.ruitx.jaws.strings;

public class DefaultHTML {

    public static final String HTML_404_NOT_FOUND = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>404 - Not Found</title>
            </head>
            <body>
                <h1>404 - Not Found</h1>
            </body>
            </html>
            """;

    public static final String HTML_404_NOT_FOUND_HTMX = """
            <h1>404 - Not Found</h1>
            """;

    public static final String HTML_401_UNAUTHORIZED = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>401 - Unauthorized</title>
            </head>
            <body>
                <h1>401 - Unauthorized</h1>
            </body>
            </html>
            """;

    public static final String HTML_401_UNAUTHORIZED_HTMX = """
            <h1>401 - Unauthorized</h1>
            """;
}

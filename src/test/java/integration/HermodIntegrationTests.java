package integration;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.ruitx.jaws.components.Hermod;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class HermodIntegrationTests {

    @Test
    public void givenGetPathForCommand_whenDynamicHTMLParsed_thenReturnsExpectedOutput() throws IOException {
        String htmlInput = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>{{getPathFor("index.html")}}</p>
                    </body>
                    </html>
                """;

        String result = Hermod.processTemplate(htmlInput);

        assertEquals("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>http://localhost:15000/index.html</p>
                    </body>
                    </html>
                """, result
        );
    }

    @Test
    public void givenGetServerPortCommand_whenDynamicHTMLParsed_thenReturnsExpectedOutput() throws IOException {
        String htmlInput = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>{{getServerPort()}}</p>
                    </body>
                    </html>
                """;

        String result = Hermod.processTemplate(htmlInput);

        assertEquals("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>15000</p>
                    </body>
                    </html>
                """, result
        );
    }

    @Disabled
    @Test
    public void givenGetCurrentConnectionsCommand_whenDynamicHTMLParsed_thenReturnsExpectedOutput() throws IOException {
        String htmlInput = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>{{getCurrentConnections()}}</p>
                    </body>
                    </html>
                """;

        String result = Hermod.processTemplate(htmlInput);

        assertEquals("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>0</p>
                    </body>
                    </html>
                """, result
        );
    }

    @Test
    public void givenInvalidCommand_whenDynamicHTMLParsed_thenReturnsExpectedOutput() throws IOException {
        String htmlInput = """
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>{{invalidCommand()}}</p>
                    </body>
                    </html>
                """;

        String result = Hermod.processTemplate(htmlInput);

        assertEquals("""
                    <!DOCTYPE html>
                    <html lang="en">
                    <head>
                        <meta charset="UTF-8">
                        <title>Dynamic Content</title>
                    </head>
                    <body>
                        <h1>Welcome</h1>
                        <p>{{invalidCommand()}}</p>
                    </body>
                    </html>
                """, result
        );
    }

}

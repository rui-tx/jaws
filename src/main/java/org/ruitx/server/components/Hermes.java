package org.ruitx.server.components;

import org.ruitx.server.commands.CommandList;
import org.ruitx.server.interfaces.Command;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.ruitx.server.configs.ApplicationConfig.WWW_PATH;

/**
 * Hermes is a utility class that contains methods for parsing HTML files.
 */
public final class Hermes {

    private static final String DEFAULT_BODY_PATH = "_body.html";
    private static String BODY_PATH;

    /**
     * Get the body path for the default body partial.
     * This method is synchronized to prevent concurrent access.
     *
     * @return the path to the default partials.
     */
    public static synchronized String getBodyPath() {
        return BODY_PATH;
    }

    /**
     * Set the body path for the default body partial.
     * This method is synchronized to prevent concurrent access.
     *
     * @param path the path to the default partials.
     */
    public static synchronized void setBodyPath(String path) {
        if (path != null && !path.isEmpty()) {
            BODY_PATH = path;
            return;
        }

        BODY_PATH = DEFAULT_BODY_PATH;
    }

    /**
     * Parse the HTML file and replace the placeholders with the actual values
     *
     * @param htmlPage The HTML file to parse
     * @return the parsed HTML
     * @throws IOException if there's an error reading the HTML file
     */
    public static String parseHTML(File htmlPage) throws IOException {
        String html = new String(Files.readAllBytes(Path.of(htmlPage.getPath())));
        return parseHTMLString(html);
    }

    /**
     * Parse the HTML string and replace the placeholders with the actual values
     *
     * @param htmlPage The HTML string to parse
     * @return the parsed HTML
     * @throws IOException if there's an error reading the HTML file
     */
    public static String parseHTML(String htmlPage) throws IOException {
        return parseHTMLString(htmlPage);
    }

    /**
     * Parse the HTML string and replace the placeholders with the actual values, using the provided request parameters.
     *
     * @param htmlPage    The HTML string to parse
     * @param queryParams The query parameters map
     * @param bodyParams  The body parameters map
     * @return the parsed HTML
     * @throws IOException if there's an error reading the HTML file
     */
    public static String parseHTML(String htmlPage, Map<String, String> queryParams, Map<String, String> bodyParams) throws IOException {
        if (queryParams == null) {
            queryParams = new LinkedHashMap<>();
        }

        if (bodyParams == null) {
            bodyParams = new LinkedHashMap<>();
        }

        return parseHTMLStringWithParams(htmlPage, queryParams, bodyParams);
    }

    private static String parseHTMLString(String html) {
        return parseHTMLStringWithParams(html, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    // TODO: Refactor this method
    // needs more testing
    private static String parseHTMLStringWithParams(String html, Map<String, String> queryParams, Map<String, String> bodyParams) {
        // Regex to match placeholders like {{test}} (e.g., {{test}})
        String regex = "\\{\\{([^}]*)}}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(html);
        StringBuilder result = new StringBuilder();

        // Combine queryParams and bodyParams into one map
        Map<String, String> requestParams = new LinkedHashMap<>(queryParams);
        requestParams.putAll(bodyParams);

        // Loop through the HTML and match each placeholder
        while (matcher.find()) {
            String placeholderContent = matcher.group(1).trim(); // Get the content inside {{...}}

            // Temporarily escape $ signs for regex matching (to avoid conflicts with regex special chars)
            String escapedContent = escapeDollarSigns(placeholderContent);

            // Now we need to check if it's a command or parameter
            String commandName = escapedContent.split("\\s*\\(")[0]; // Get the command name (before any parentheses)
            Command commandExecutor = CommandList.LIST.get(commandName);

            if (commandExecutor != null) {
                // If it's a command, execute it and append the result
                String commandResult = commandExecutor.execute(escapedContent);
                matcher.appendReplacement(result, Matcher.quoteReplacement(restoreDollarSigns(commandResult)));
            } else {
                // If it's a parameter, check if it's present in queryParams or bodyParams
                String paramValue = requestParams.get(escapedContent);
                if (paramValue != null) {
                    // Directly use the parameter value without escaping the $ sign
                    matcher.appendReplacement(result, Matcher.quoteReplacement(paramValue));
                } else {

                    // TODO: Refactor this logic - not sure of the thread safety here
                    // if param is bodyContent and is empty, then render default bodyContent
                    // if more variables are needed this will be unmanageable fast
                    if (escapedContent.equals("_BODY_CONTENT_")) {
                        try {
                            synchronized (Hermes.class) {
                                if (getBodyPath() == null || getBodyPath().isEmpty()) {
                                    setBodyPath(DEFAULT_BODY_PATH);
                                }
                                // parses the default body content, replacing placeholders with actual values
                                paramValue = parseHTML(new String(Files.readAllBytes(Path.of(WWW_PATH + getBodyPath()))));
                            }
                        } catch (IOException e) {
                            Logger.error("Error reading default body content: " + e.getMessage());
                            matcher.appendReplacement(result, "{{" + placeholderContent + "}}");
                        }
                        matcher.appendReplacement(result, Matcher.quoteReplacement(restoreDollarSigns(paramValue)));
                    } else {
                        // If no match is found, leave the placeholder as it is
                        matcher.appendReplacement(result, "{{" + placeholderContent + "}}");
                    }
                }
            }
        }

        // Append the rest of the HTML after the last match
        matcher.appendTail(result);

        return result.toString();
    }

    /**
     * Make a full HTML page by parsing the base HTML file and rendering the specified partial.
     *
     * @param basePath    the path to the base HTML file.
     * @param partialPath the path to the partial HTML file.
     * @return the full HTML page.
     * @throws IOException if an error occurs while reading the base HTML file or the partial HTML file.
     */
    public static String makeFullPage(String basePath, String partialPath) throws IOException {
        HashMap<String, String> params = new HashMap<>();
        params.put("_BODY_CONTENT_", "{{renderPartial(\"" + partialPath + "\")}}");
        return parseHTML(new String(Files.readAllBytes(Path.of(WWW_PATH + basePath))), params, null);
    }

    public static String makeFullPageWithHTML(String basePath, String html) throws IOException {
        HashMap<String, String> params = new HashMap<>();
        params.put("_BODY_CONTENT_", html);
        return parseHTML(new String(Files.readAllBytes(Path.of(WWW_PATH + basePath))), params, null);
    }

    /**
     * Make a partial HTML page by parsing the specified partial HTML file.
     *
     * @param partialPath the path to the partial HTML file.
     * @return the partial HTML page.
     * @throws IOException if an error occurs while reading the partial HTML file.
     */
    public static String makePartialPage(String partialPath) throws IOException {
        return "{{renderPartial(\"" + partialPath + "\")}}";
    }

    /**
     * Escape all $ signs in the input string by adding a backslash before them.
     */
    private static String escapeDollarSigns(String html) {
        return html.replaceAll("\\$", "\\\\\\$"); // Escape the dollar sign
    }

    /**
     * Restore all escaped $ signs in the output string.
     */
    private static String restoreDollarSigns(String html) {
        return html.replaceAll("\\\\\\$", "\\$"); // Restore the escaped dollar sign
    }
}

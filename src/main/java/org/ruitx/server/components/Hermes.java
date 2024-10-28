package org.ruitx.server.components;

import org.ruitx.server.commands.Command;
import org.ruitx.server.commands.CommandList;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Hermes is a utility class that contains methods for parsing HTML files.
 */
public final class Hermes {

    /**
     * Parse the HTML file and replace the placeholders with the actual values
     *
     * @param htmlPage
     * @return the parsed HTML
     * @throws IOException
     */
    public static String parseHTML(File htmlPage) throws IOException {
        String html = new String(Files.readAllBytes(Path.of(htmlPage.getPath())));
        return parseHTMLString(html);
    }

    public static String parseHTML(String htmlPage) throws IOException {
        return parseHTMLString(htmlPage);
    }

    public static String parseHTML(String htmlPage, Map<String, String> queryParams, Map<String, String> bodyParams) throws IOException {
        return parseHTMLString(htmlPage, queryParams, bodyParams);
    }

    private static String parseHTMLString(String html) {
        String regex = "\\{\\{([^}]*)}}"; // gets what is inside the double curly braces
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(html);
        StringBuilder result = new StringBuilder();

        while (matcher.find()) {
            String command = matcher.group(1).trim();
            String commandName = command.split("\\s*\\(")[0]; // gets what is before the parentheses

            Command commandExecutor = CommandList.LIST.get(commandName);
            if (commandExecutor != null) {
                matcher.appendReplacement(result, commandExecutor.execute(command));
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }

    private static String parseHTMLString(String html, Map<String, String> queryParams, Map<String, String> bodyParams) {
        String regex = "\\{\\{([^}]*)}}"; // gets what is inside the double curly braces
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(html);
        StringBuilder result = new StringBuilder();

        Map<String, String> allParams = new LinkedHashMap<>(queryParams);
        if (bodyParams != null) {
            allParams.putAll(bodyParams);
        }

        while (matcher.find()) {
            String command = matcher.group(1).trim();
            String commandName = command.split("\\s*\\(")[0]; // gets what is before the parentheses

            Command commandExecutor = CommandList.LIST.get(commandName);
            if (commandExecutor != null) {
                matcher.appendReplacement(result, commandExecutor.execute(command));
            } else {
                String paramValue = allParams.get(command);
                if (paramValue != null) {
                    matcher.appendReplacement(result, paramValue);
                } else {
                    matcher.appendReplacement(result, "{{" + command + "}}"); // Leave the placeholder if not found
                }
            }
        }

        matcher.appendTail(result);
        return result.toString();
    }
}


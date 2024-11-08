package org.ruitx.server.commands;

import org.ruitx.server.components.Hermes;
import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.interfaces.Command;
import org.tinylog.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RenderPartialCommand implements Command {

    private static final String COMMAND = "renderPartial";

    @Override
    public String execute(String command) {
        String file = command.replace(COMMAND, "").trim();
        String cmdRegex = "\\(\"([^\"]+)\"\\)"; // Extracts the file name inside the parentheses
        Matcher cmdMatcher = Pattern.compile(cmdRegex).matcher(file);

        while (cmdMatcher.find()) {
            file = cmdMatcher.group(1); // Get the actual file name
        }

        Path path = Paths.get(ApplicationConfig.WWW_PATH + file);
        if (!Files.exists(path)) {
            Logger.error("Could not find partial file: " + ApplicationConfig.WWW_PATH + file);
            return "";
        }

        String parsedHTML;
        try {
            String rawHTML = new String(Files.readAllBytes(path));
            rawHTML = escapeDollarSigns(rawHTML); // Escape $ signs in the partial HTML content to avoid regex issues
            parsedHTML = Hermes.parseHTML(rawHTML);
            parsedHTML = restoreDollarSigns(parsedHTML);

        } catch (IOException e) {
            throw new RuntimeException("Error reading partial file: " + file, e);
        }

        return parsedHTML;
    }

    /**
     * Escape all $ signs in the input string by adding a backslash before them.
     */
    private String escapeDollarSigns(String html) {
        return html.replaceAll("\\$", "\\\\\\$"); // Escape the dollar sign
    }

    /**
     * Restore all escaped $ signs in the output string.
     */
    private String restoreDollarSigns(String html) {
        return html.replaceAll("\\\\\\$", "\\$"); // Restore the escaped dollar sign
    }
}






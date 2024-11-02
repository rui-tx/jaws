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
        String cmdRegex = "\\(\"([^\"]+)\"\\)"; // gets what is inside the parentheses
        Matcher cmdMatcher = Pattern.compile(cmdRegex).matcher(file);
        while (cmdMatcher.find()) {
            file = cmdMatcher.group(1);
        }

        Path path = Paths.get(ApplicationConfig.WWW_PATH + file);
        if (!Files.exists(path)) {
            Logger.error("Could not find partial file: " + file);
            return "";
        }

        String parsedHTML;
        try {
            parsedHTML = Hermes.parseHTML(path.toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return parsedHTML;
    }

}

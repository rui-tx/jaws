package org.ruitx.server.commands;

import org.ruitx.server.configs.ApplicationConfig;
import org.ruitx.server.interfaces.Command;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GetPathForCommand implements Command {

    private static final String COMMAND = "getPathFor";

    @Override
    public String execute(String command) {
        String file = command.replace(COMMAND, "").trim();
        String cmdRegex = "\\(\"([^\"]+)\"\\)"; // gets what is inside the parentheses
        Matcher cmdMatcher = Pattern.compile(cmdRegex).matcher(file);
        while (cmdMatcher.find()) {
            file = cmdMatcher.group(1);
        }
        return ApplicationConfig.URL + file;
    }
}

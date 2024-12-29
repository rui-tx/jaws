package org.ruitx.jaws.commands;

import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Command;
import org.tinylog.Logger;

public class IfCommand implements Command {
    private static final String COMMAND = "if";

    @Override
    public String execute(String command) {
        //Logger.info("Evaluating condition: " + command);

        // First, extract everything after "if"
        if (!command.startsWith("if")) {
            Logger.warn("Command must start with 'if': " + command);
            return "";
        }

        String remaining = command.substring(2).trim();

        // Extract condition between parentheses if they exist, otherwise take the first word
        String condition;
        String content;

        if (remaining.startsWith("(")) {
            int closeParen = remaining.indexOf(')');
            if (closeParen == -1) {
                Logger.warn("Missing closing parenthesis: " + remaining);
                return "";
            }
            condition = remaining.substring(1, closeParen).trim();
            content = remaining.substring(closeParen + 1).trim();
        } else {
            // No parentheses - take first word as condition
            int spaceIndex = remaining.indexOf(' ');
            if (spaceIndex == -1) {
                condition = remaining;
                content = "";
            } else {
                condition = remaining.substring(0, spaceIndex).trim();
                content = remaining.substring(spaceIndex + 1).trim();
            }
        }

//        Logger.info("Condition: " + condition);
//        Logger.info("Content: " + content);

        // Split content into true and false branches if there's an else
        String trueContent = content;
        String falseContent = null;

        int elseIndex = content.indexOf("else");
        if (elseIndex != -1) {
            trueContent = content.substring(0, elseIndex).trim();
            falseContent = content.substring(elseIndex + 4).trim();
        }

        boolean result = evaluateCondition(condition);

        if (result) {
            return trueContent;
        } else if (falseContent != null) {
            return falseContent;
        }
        return "";
    }

    private boolean evaluateCondition(String condition) {
        String token = Yggdrasill.RequestHandler.getCurrentToken();
        //Logger.info("Evaluating condition: " + condition + " with token: " + (token != null));

        // Remove any whitespace and optional parentheses
        condition = condition.replaceAll("\\s+", "");
        if (condition.endsWith("()")) {
            condition = condition.substring(0, condition.length() - 2);
        }

        return switch (condition) {
            case "isLoggedIn" -> token != null && Tyr.isTokenValid(token);
            case "isGuest" -> token == null || !Tyr.isTokenValid(token);
            default -> {
                Logger.warn("Unknown condition: " + condition);
                yield false;
            }
        };
    }
}
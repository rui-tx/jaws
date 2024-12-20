package org.ruitx.server.commands;

import org.ruitx.server.interfaces.Command;

import java.util.HashMap;
import java.util.Map;

public class CommandList {
    public static final Map<String, Command> LIST = new HashMap<>();

    // TODO:
    // Fix bug that if a command needs parameters, the parentheses cannot be used
    static {
        LIST.put("getPathFor", new GetPathForCommand());
        LIST.put("getServerPort", new GetServerPortCommand());
        LIST.put("getCurrentConnections", new GetCurrentConnectionsCommand());
        LIST.put("renderPartial", new RenderPartialCommand());
        LIST.put("if", new IfCommand());
    }
}

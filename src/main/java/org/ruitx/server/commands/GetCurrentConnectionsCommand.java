package org.ruitx.server.commands;

import org.ruitx.server.components.Yggdrasill;

public class GetCurrentConnectionsCommand implements Command {

    private static final String COMMAND = "getServerPort";

    @Override
    public String execute(String command) {
        return Yggdrasill.currentConnections + "";
    }
}

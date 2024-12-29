package org.ruitx.jaws.commands;

import org.ruitx.jaws.components.Yggdrasill;
import org.ruitx.jaws.interfaces.Command;

public class GetCurrentConnectionsCommand implements Command {

    private static final String COMMAND = "getCurrentConnections";

    @Override
    public String execute(String command) {
        return Yggdrasill.currentConnections + "";
    }
}

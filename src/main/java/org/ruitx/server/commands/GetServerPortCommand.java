package org.ruitx.server.commands;

import org.ruitx.server.configs.Constants;

public class GetServerPortCommand implements Command {

    private static final String COMMAND = "getServerPort";

    @Override
    public String execute(String command) {
        return Constants.DEFAULT_PORT + "";
    }
}

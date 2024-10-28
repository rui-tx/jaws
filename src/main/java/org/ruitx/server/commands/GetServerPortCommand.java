package org.ruitx.server.commands;

import org.ruitx.server.configs.ApplicationConfig;

public class GetServerPortCommand implements Command {

    private static final String COMMAND = "getServerPort";

    @Override
    public String execute(String command) {
        return ApplicationConfig.PORT + "";
    }
}

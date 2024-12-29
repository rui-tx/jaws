package org.ruitx.jaws.commands;

import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.interfaces.Command;

public class GetServerPortCommand implements Command {

    private static final String COMMAND = "getServerPort";

    @Override
    public String execute(String command) {
        return ApplicationConfig.PORT + "";
    }
}

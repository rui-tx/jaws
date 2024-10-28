package org.ruitx.server.strings;

import org.ruitx.server.util.TerminalColors;

public class Messages {

    public static final String SERVER_LOG = "[SERVER]" + TerminalColors.ANSI_WHITE + "[%d]" + TerminalColors.ANSI_RESET
            + "-> %s";

    public static final String SERVER_STARTED = TerminalColors.ANSI_GREEN + "JAWS started" + TerminalColors.ANSI_RESET;
    public static final String REQUEST_ERROR = "Error: Request not recognized";
    public static final String INVALID_REQUEST = "Error: Invalid request";
    public static final String ENDPOINT_NOT_FOUND = "Endpoint not found";
    public static final String INTERNAL_SERVER_ERROR = "Error: Internal server error";
    public static final String CLIENT_LOG = TerminalColors.ANSI_WHITE + "[%d]" + TerminalColors.ANSI_RESET
            + TerminalColors.ANSI_BLUE + "[%s:%d]" + TerminalColors.ANSI_RESET
            + "-> %s";

    public static final String CLIENT_CONNECTED = TerminalColors.ANSI_WHITE + "Connect" + TerminalColors.ANSI_RESET;
    public static final String CLIENT_DISCONNECTED = TerminalColors.ANSI_WHITE + "Disconnect" + TerminalColors.ANSI_RESET;
    public static final String CLIENT_TIMEOUT = TerminalColors.ANSI_RED + "Disconnect by timeout" + TerminalColors.ANSI_RESET;

}
/*

public enum Messages {
    SERVER_LOG("[SERVER]" + TerminalColors.ANSI_WHITE + "[%d]" + TerminalColors.ANSI_RESET
            + "-> %s"),
    SERVER_STARTED(TerminalColors.ANSI_GREEN + "Server started" + TerminalColors.ANSI_RESET + "\n"),
    REQUEST_ERROR("Error: Request not recognized\n"),
    INVALID_REQUEST("Error: Invalid request\n"),
    ENDPOINT_NOT_FOUND("Endpoint not found"),
    INTERNAL_SERVER_ERROR("Error: Internal server error\n"),

    CLIENT_LOG(TerminalColors.ANSI_WHITE + "[%d]" + TerminalColors.ANSI_RESET
            + TerminalColors.ANSI_BLUE + "[%s:%d]" + TerminalColors.ANSI_RESET
            + "-> %s\n"),

    CLIENT_CONNECTED(TerminalColors.ANSI_WHITE + "Connect" + TerminalColors.ANSI_RESET),
    CLIENT_DISCONNECTED(TerminalColors.ANSI_WHITE + "Disconnect" + TerminalColors.ANSI_RESET),
    CLIENT_TIMEOUT(TerminalColors.ANSI_RED + "Disconnect by timeout" + TerminalColors.ANSI_RESET);

    private final String message;

    Messages(String message) {
        this.message = message;
    }

    public static void print(Messages message) {
        System.out.printf(message.getMessage());
    }

    public String getMessage() {
        return message;
    }
}

 */

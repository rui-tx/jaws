package org.ruitx.server.configs;

import org.tinylog.Logger;

public class ApplicationConfig {
    public static final int DEFAULT_PORT = 15000;
    public static final String URL = "http://localhost:" + DEFAULT_PORT + "/";
    public static final String DEFAULT_WWW_PATH = "src/main/resources/";
    public static final long TIMEOUT = 1000L * 10L; // 10 seconds

    // Static fields for configuration
    public static final int PORT;
    public static final String WWW_PATH;

    static {
        PORT = getPort();
        WWW_PATH = getWwwPath();
    }

    private static int getPort() {
        String portEnv = System.getenv("PORT");
        if (portEnv != null) {
            try {
                return Integer.parseInt(portEnv);
            } catch (NumberFormatException e) {
                Logger.warn("Invalid PORT environment variable, using default: " + DEFAULT_PORT);
            }
        }
        return DEFAULT_PORT;
    }

    private static String getWwwPath() {
        String wwwPathEnv = System.getenv("WWWPATH");
        return wwwPathEnv != null ? wwwPathEnv : DEFAULT_WWW_PATH;
    }
}


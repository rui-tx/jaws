package org.ruitx.server.configs;

import org.tinylog.Logger;

public class ApplicationConfig {
    public static final int DEFAULT_PORT = 15000;
    public static final String URL = "http://localhost:" + DEFAULT_PORT + "/";
    public static final String DEFAULT_WWW_PATH = "src/main/resources/";
    public static final long TIMEOUT = 1000L * 10L; // 10 seconds
    public static final String DEFAULT_CUSTOM_PAGE_PATH_404 = "src/main/resources/404.html";

    // Static fields for configuration
    public static final int PORT;
    public static final String WWW_PATH;
    public static final String CUSTOM_PAGE_PATH_404;

    static {
        PORT = getPort();
        WWW_PATH = getWwwPath();
        CUSTOM_PAGE_PATH_404 = getCustomPagePath404();
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

    private static String getCustomPagePath404() {
        String customPagePath404Env = System.getenv("CUSTOM_PAGE_PATH_404");
        return customPagePath404Env != null ? customPagePath404Env : DEFAULT_CUSTOM_PAGE_PATH_404;
    }
}

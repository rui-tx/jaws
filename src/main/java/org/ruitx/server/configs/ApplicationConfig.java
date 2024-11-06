package org.ruitx.server.configs;

import org.ruitx.server.components.Tyr;
import org.tinylog.Logger;

public class ApplicationConfig {
    public static final String APPLICATION_NAME = "JAWS";
    public static final int DEFAULT_PORT = 15000;
    public static final String DEFAULT_URL = "http://localhost:" + DEFAULT_PORT + "/";
    public static final String DEFAULT_WWW_PATH = "src/main/resources/www/";
    public static final long TIMEOUT = 1000L * 10L; // 10 seconds
    public static final String DEFAULT_CUSTOM_PAGE_PATH_404 = DEFAULT_WWW_PATH + "/404.html";
    public static final String DEFAULT_DATABASE_PATH = "src/main/resources/db.db";
    public static final String DEFAULT_DATABASE_SCHEMA_PATH = "src/main/resources/sql/create_schema_v1.sql";
    public static final String DEFAULT_JWT_SECRET = "";

    // Static fields for configuration
    public static final String URL;
    public static final int PORT;
    public static final String WWW_PATH;
    public static final String CUSTOM_PAGE_PATH_404;
    public static final String DATABASE_PATH;
    public static final String DATABASE_SCHEMA_PATH;
    public static final String JWT_SECRET;

    static {
        URL = getUrl();
        PORT = getPort();
        WWW_PATH = getWwwPath();
        CUSTOM_PAGE_PATH_404 = getCustomPagePath404();
        DATABASE_PATH = getDatabasePath();
        DATABASE_SCHEMA_PATH = getDatabaseSchemaPath();
        JWT_SECRET = getJWTSecret();
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

    private static String getUrl() {
        String urlEnv = System.getenv("URL");
        return urlEnv != null ? urlEnv : DEFAULT_URL;
    }

    private static String getWwwPath() {
        String wwwPathEnv = System.getenv("WWWPATH");
        return wwwPathEnv != null ? wwwPathEnv : DEFAULT_WWW_PATH;
    }

    private static String getCustomPagePath404() {
        String customPagePath404Env = System.getenv("CUSTOM_PAGE_PATH_404");
        return customPagePath404Env != null ? customPagePath404Env : DEFAULT_CUSTOM_PAGE_PATH_404;
    }

    private static String getDatabasePath() {
        String databasePathEnv = System.getenv("DBPATH");
        return databasePathEnv != null ? databasePathEnv : DEFAULT_DATABASE_PATH;
    }

    private static String getDatabaseSchemaPath() {
        String databaseSchemaPathEnv = System.getenv("DBSCHEMAPATH");
        return databaseSchemaPathEnv != null ? databaseSchemaPathEnv : DEFAULT_DATABASE_SCHEMA_PATH;
    }

    private static String getJWTSecret() {
        String jwtTokenEnv = System.getenv("JWTTOKEN");
        if (jwtTokenEnv == null) {
            Logger.warn("JWT Token not found, generating a new one");
            String jwtToken = Tyr.createSecreteKey();
            Logger.info("JWT Token: " + jwtToken);
            Logger.warn("Please save this token in a safe place, it will not be shown again");
            return jwtToken;

        }
        return jwtTokenEnv;
    }
}

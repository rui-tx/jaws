package org.ruitx.jaws.configs;

import org.ruitx.jaws.components.Tyr;
import org.tinylog.Logger;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class ApplicationConfig {
    // Default constants
    public static final String APPLICATION_NAME = "JAWS";
    public static final int DEFAULT_PORT = 15000;
    public static final String DEFAULT_URL = "http://localhost:" + DEFAULT_PORT + "/";
    public static final String DEFAULT_WWW_PATH = "src/main/resources/www/";
    public static final long TIMEOUT = 1000L * 10L; // 10 seconds
    public static final String DEFAULT_CUSTOM_PAGE_PATH_404 = DEFAULT_WWW_PATH + "/404.html";
    public static final String DEFAULT_DATABASE_PATH = "src/main/resources/db.db";
    public static final String DEFAULT_DATABASE_SCHEMA_PATH = "src/main/resources/sql/create_schema_v1.sql";

    // Static fields for configuration
    public static final String URL;
    public static final int PORT;
    public static final String WWW_PATH;
    public static final String CUSTOM_PAGE_PATH_404;
    public static final String DATABASE_PATH;
    public static final String DATABASE_SCHEMA_PATH;
    public static final String JWT_SECRET;

    private static final Properties properties = new Properties();

    static {
        // Load properties file
        try (FileInputStream fis = new FileInputStream("src/main/resources/application.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            Logger.warn("Could not load application.properties, will use environment variables or defaults");
        }

        // Initialize all static fields
        PORT = getPortValue();
        URL = getUrlValue();
        WWW_PATH = getWwwPathValue();
        CUSTOM_PAGE_PATH_404 = getCustomPagePath404Value();
        DATABASE_PATH = getDatabasePathValue();
        DATABASE_SCHEMA_PATH = getDatabaseSchemaPathValue();
        JWT_SECRET = getJWTSecretValue();
    }

    private ApplicationConfig() {
    }

    private static int getPortValue() {
        String envValue = System.getenv("PORT");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                Logger.warn("Invalid PORT environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("port");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                Logger.warn("Invalid port in properties file: " + propValue);
            }
        }

        return DEFAULT_PORT;
    }

    private static String getUrlValue() {
        return getConfigValue("URL", "url", DEFAULT_URL);
    }

    private static String getWwwPathValue() {
        return getConfigValue("WWWPATH", "www.path", DEFAULT_WWW_PATH);
    }

    private static String getCustomPagePath404Value() {
        return getConfigValue(
                "CUSTOM_PAGE_PATH_404",
                "custom.page.path.404",
                DEFAULT_CUSTOM_PAGE_PATH_404);
    }

    private static String getDatabasePathValue() {
        return getConfigValue("DBPATH", "database.path", DEFAULT_DATABASE_PATH);
    }

    private static String getDatabaseSchemaPathValue() {
        return getConfigValue("DBSCHEMAPATH", "database.schema.path", DEFAULT_DATABASE_SCHEMA_PATH);
    }

    private static String getJWTSecretValue() {
        String jwtSecret = getConfigValue("JWTTOKEN", "jwt.secret", null);
        if (jwtSecret == null || jwtSecret.isEmpty()) {
            Logger.warn("JWT Token not found, generating a new one");
            String jwtToken = Tyr.createSecreteKey();
            Logger.info("JWT Token: " + jwtToken);
            Logger.warn("Please save this token in a safe place, it will not be shown again");
            return jwtToken;
        }
        return jwtSecret;
    }

    private static String getConfigValue(String envKey, String propKey, String defaultValue) {
        // First, check environment variables
        String envValue = System.getenv(envKey);
        if (envValue != null && !envValue.isEmpty()) {
            return envValue;
        }

        // Then check the properties file
        String propValue = properties.getProperty(propKey);
        if (propValue != null && !propValue.isEmpty()) {
            return propValue;
        }

        // Finally, uses the default value
        return defaultValue;
    }
}
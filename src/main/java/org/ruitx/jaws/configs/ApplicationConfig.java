package org.ruitx.jaws.configs;

import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.utils.JawsLogger;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.DriverManager;
import java.util.Properties;

public class ApplicationConfig {
    // Default constants
    public static final String APPLICATION_NAME = "JAWS";
    public static final int DEFAULT_PORT = 15000;
    public static final String DEFAULT_URL = "http://localhost:" + DEFAULT_PORT + "/";
    public static final String DEFAULT_WWW_PATH = "src/main/resources/www/";
    public static final long TIMEOUT = 1000L * 10L; // 10 seconds
    public static final String DEFAULT_CUSTOM_PAGE_PATH_401 = "";
    public static final String DEFAULT_CUSTOM_PAGE_PATH_404 = "";
    public static final String DEFAULT_DATABASE_PATH = "src/main/resources/db.db";
    public static final String DEFAULT_DATABASE_SCHEMA_PATH = "src/main/resources/sql/create_schema_v1.sql";

    // Freyr
    public static final int DEFAULT_WORKER_THREADS =  Runtime.getRuntime().availableProcessors();
    public static final int DEFAULT_QUEUE_CAPACITY = 10000;
    public static final long DEFAULT_CLEANUP_INTERVAL_MS = 300000; // 5 minutes

    // JawsLogger 
    public static final String DEFAULT_DB_LEVEL = "INFO";
    public static final int DEFAULT_BATCH_SIZE = 1000;
    public static final long DEFAULT_FLUSH_INTERVAL_MS = 5000;
    public static final int DEFAULT_BUFFER_CAPACITY = 10000;

    // RateLimiter
    public static final int DEFAULT_RATE_LIMIT_MAX_REQUESTS = 100;
    public static final int DEFAULT_RATE_LIMIT_WINDOW_MS = 60000;

    // Static fields for configuration
    public static final String URL;
    public static final int PORT;
    public static final String WWW_PATH;
    public static final String CUSTOM_PAGE_PATH_401;
    public static final String CUSTOM_PAGE_PATH_404;
    public static final String DATABASE_PATH;
    public static final String DATABASE_SCHEMA_PATH;
    public static final String JWT_SECRET;
    public static final boolean HERMOD_DEVELOPMENT_MODE;
    public static final long HERMOD_TEMPLATE_CACHE_TTL;

    // Freyr
    public static final int WORKER_THREADS;
    public static final int QUEUE_CAPACITY;
    public static final long CLEANUP_INTERVAL_MS;

    // JawsLogger
    public static final String DB_LEVEL;
    public static final int BATCH_SIZE;
    public static final long FLUSH_INTERVAL_MS;
    public static final int BUFFER_CAPACITY;

    // RateLimiter
    public static final int RATE_LIMIT_MAX_REQUESTS;
    public static final int RATE_LIMIT_WINDOW_MS;

    private static final Properties properties = new Properties();

    static {
        // Load properties file
        try (FileInputStream fis = new FileInputStream("src/main/resources/application.properties")) {
            properties.load(fis);
        } catch (IOException e) {
            JawsLogger.warn("Could not load application.properties, will use environment variables or defaults");
        }

        // Initialize all static fields
        URL = getUrlValue();
        PORT = getPortValue();
        WWW_PATH = getWwwPathValue();
        CUSTOM_PAGE_PATH_401 = getCustomPagePath401Value();
        CUSTOM_PAGE_PATH_404 = getCustomPagePath404Value();
        DATABASE_PATH = getDatabasePathValue();
        DATABASE_SCHEMA_PATH = getDatabaseSchemaPathValue();
        JWT_SECRET = getJWTSecretValue();
        HERMOD_DEVELOPMENT_MODE = getHermodDevelopmentModeValue();
        HERMOD_TEMPLATE_CACHE_TTL = getHermodTemplateCacheTtlValue();
        
        // Initialize Freyr configuration
        WORKER_THREADS = getWorkerThreadsValue();
        QUEUE_CAPACITY = getQueueCapacityValue();
        CLEANUP_INTERVAL_MS = getCleanupIntervalMsValue();
        
        // Initialize JawsLogger configuration
        DB_LEVEL = getDbLevelValue();
        BATCH_SIZE = getBatchSizeValue();
        FLUSH_INTERVAL_MS = getFlushIntervalMsValue();
        BUFFER_CAPACITY = getBufferCapacityValue();
        
        // Initialize RateLimiter configuration
        RATE_LIMIT_MAX_REQUESTS = getRateLimitMaxRequestsValue();
        RATE_LIMIT_WINDOW_MS = getRateLimitWindowMsValue();
        
        JawsLogger.info("JAWS Configuration");
        JawsLogger.info("--------------------------------");
        JawsLogger.info("URL: " + URL);
        JawsLogger.info("PORT: " + PORT);
        JawsLogger.info("WWW_PATH: " + WWW_PATH);
        JawsLogger.info("CUSTOM_PAGE_PATH_401: " + CUSTOM_PAGE_PATH_401);
        JawsLogger.info("CUSTOM_PAGE_PATH_404: " + CUSTOM_PAGE_PATH_404);
        JawsLogger.info("DATABASE_PATH: " + DATABASE_PATH);
        JawsLogger.info("DATABASE_SCHEMA_PATH: " + DATABASE_SCHEMA_PATH);
        JawsLogger.info("JWT_SECRET: [REDACTED]");
        JawsLogger.info("HERMOD_DEVELOPMENT_MODE: " + HERMOD_DEVELOPMENT_MODE);
        JawsLogger.info("HERMOD_TEMPLATE_CACHE_TTL: " + HERMOD_TEMPLATE_CACHE_TTL);
        JawsLogger.info("WORKER_THREADS: " + WORKER_THREADS);
        JawsLogger.info("QUEUE_CAPACITY: " + QUEUE_CAPACITY);
        JawsLogger.info("CLEANUP_INTERVAL_MS: " + CLEANUP_INTERVAL_MS);
        JawsLogger.info("DB_LEVEL: " + DB_LEVEL);
        JawsLogger.info("BATCH_SIZE: " + BATCH_SIZE);
        JawsLogger.info("FLUSH_INTERVAL_MS: " + FLUSH_INTERVAL_MS);
        JawsLogger.info("BUFFER_CAPACITY: " + BUFFER_CAPACITY);
        JawsLogger.info("RATE_LIMIT_MAX_REQUESTS: " + RATE_LIMIT_MAX_REQUESTS);
        JawsLogger.info("RATE_LIMIT_WINDOW_MS: " + RATE_LIMIT_WINDOW_MS);
        JawsLogger.info("--------------------------------");
    }

    private ApplicationConfig() {
    }

    private static int getPortValue() {
        String envValue = System.getenv("PORT");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid PORT environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("port");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid port in properties file: " + propValue);
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

    private static String getCustomPagePath401Value() {
        return getConfigValue(
                "CUSTOM_PAGE_PATH_401",
                "custom.page.path.401",
                DEFAULT_CUSTOM_PAGE_PATH_401);
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
            JawsLogger.warn("JWT Token not found, generating a new one");
            String jwtToken = Tyr.createSecreteKey();
            JawsLogger.info("JWT Token: " + jwtToken);
            JawsLogger.warn("Please save this token in a safe place, it will not be shown again");
            return jwtToken;
        }
        return jwtSecret;
    }

    private static boolean getHermodDevelopmentModeValue() {
        String devMode = getConfigValue("HERMOD_DEVELOPMENT_MODE", "hermod.development.mode", "false");
        return Boolean.parseBoolean(devMode);
    }

    private static long getHermodTemplateCacheTtlValue() {
        String ttl = getConfigValue("HERMOD_TEMPLATE_CACHE_TTL", "hermod.template.cache.ttl", "3600000");
        return Long.parseLong(ttl);
    }

    private static int getWorkerThreadsValue() {
        String envValue = System.getenv("WORKER_THREADS");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid WORKER_THREADS environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("freyr.queue.workers");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid freyr.queue.workers in properties file: " + propValue);
            }
        }

        return DEFAULT_WORKER_THREADS;
    }

    private static int getQueueCapacityValue() {
        String envValue = System.getenv("QUEUE_CAPACITY");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid QUEUE_CAPACITY environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("freyr.queue.size");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid freyr.queue.size in properties file: " + propValue);
            }
        }

        return DEFAULT_QUEUE_CAPACITY;
    }

    private static long getCleanupIntervalMsValue() {
        String envValue = System.getenv("CLEANUP_INTERVAL_MS");
        if (envValue != null) {
            try {
                return Long.parseLong(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid CLEANUP_INTERVAL_MS environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("freyr.queue.interval_cleanup");
        if (propValue != null) {
            try {
                return Long.parseLong(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid freyr.queue.interval_cleanup in properties file: " + propValue);
            }
        }

        return DEFAULT_CLEANUP_INTERVAL_MS;
    }

    private static String getDbLevelValue() {
        String envValue = System.getenv("DB_LEVEL");
        if (envValue != null) {
            return envValue;
        }

        String propValue = properties.getProperty("jawsLogger.db.level");
        if (propValue != null) {
            return propValue;
        }

        return DEFAULT_DB_LEVEL;
    }

    private static int getBatchSizeValue() {
        String envValue = System.getenv("BATCH_SIZE");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid BATCH_SIZE environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("jawslogger.batchsize");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid jawslogger.batchsize in properties file: " + propValue);
            }
        }

        return DEFAULT_BATCH_SIZE;
    }

    private static long getFlushIntervalMsValue() {
        String envValue = System.getenv("FLUSH_INTERVAL_MS");
        if (envValue != null) {
            try {
                return Long.parseLong(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid FLUSH_INTERVAL_MS environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("jawslogger.flush_interval");
        if (propValue != null) {
            try {
                return Long.parseLong(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid jawslogger.flush_interval in properties file: " + propValue);
            }
        }

        return DEFAULT_FLUSH_INTERVAL_MS;
    }

    private static int getBufferCapacityValue() {
        String envValue = System.getenv("BUFFER_CAPACITY");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid BUFFER_CAPACITY environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("jawsLogger.buffer_capacity");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid jawsLogger.buffer_capacity in properties file: " + propValue);
            }
        }

        return DEFAULT_BUFFER_CAPACITY;
    }

    private static int getRateLimitMaxRequestsValue() {
        String envValue = System.getenv("RATE_LIMIT_MAX_REQUESTS");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid RATE_LIMIT_MAX_REQUESTS environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("ratelimiter.max.requests");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid ratelimiter.max.requests in properties file: " + propValue);
            }
        }

        return DEFAULT_RATE_LIMIT_MAX_REQUESTS;
    }

    private static int getRateLimitWindowMsValue() {
        String envValue = System.getenv("RATE_LIMIT_WINDOW_MS");
        if (envValue != null) {
            try {
                return Integer.parseInt(envValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid RATE_LIMIT_WINDOW_MS environment variable value: " + envValue);
            }
        }

        String propValue = properties.getProperty("ratelimiter.window.ms");
        if (propValue != null) {
            try {
                return Integer.parseInt(propValue);
            } catch (NumberFormatException e) {
                JawsLogger.warn("Invalid ratelimiter.window.ms in properties file: " + propValue);
            }
        }

        return DEFAULT_RATE_LIMIT_WINDOW_MS;
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
package org.ruitx.jaws.utils;

import org.ruitx.jaws.configs.ApplicationConfig;
import org.ruitx.jaws.utils.JawsLogger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class that provides functions for Thymeleaf templates.
 * This replaces the previous command system with Thymeleaf-compatible utility objects.
 */
public class ThymeleafUtils {

    /**
     * Get the full path for a given file path, including the application URL.
     * This replaces the GetPathForCommand.
     *
     * @param filePath the file path to get the full URL for
     * @return the complete URL path
     */
    public String getPathFor(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return ApplicationConfig.URL;
        }
        
        // Ensure path starts with /
        if (!filePath.startsWith("/")) {
            filePath = "/" + filePath;
        }
        
        return ApplicationConfig.URL + filePath;
    }

    /**
     * Render a partial HTML file and return its content.
     * This replaces the RenderPartialCommand.
     *
     * @param partialPath the path to the partial HTML file
     * @return the rendered HTML content
     */
    public String renderPartial(String partialPath) {
        if (partialPath == null || partialPath.isEmpty() || 
            partialPath.equals("/") || partialPath.equals(".") || partialPath.equals("..")) {
            return "";
        }

        Path path = Paths.get(ApplicationConfig.WWW_PATH + partialPath);
        
        if (!Files.exists(path) || Files.isDirectory(path)) {
            JawsLogger.warn("Partial template not found: {}", partialPath);
            return "";
        }

        try {
            String content = new String(Files.readAllBytes(path));
            // For now, return raw content. Later we can make this recursive with Thymeleaf processing
            return content;
        } catch (IOException e) {
            JawsLogger.error("Error reading partial template {}: {}", partialPath, e.getMessage());
            return "";
        }
    }

    /**
     * Get application configuration values.
     *
     * @param configKey the configuration key
     * @return the configuration value
     */
    public String getConfig(String configKey) {
        switch (configKey.toLowerCase()) {
            case "url":
                return ApplicationConfig.URL;
            case "wwwpath":
                return ApplicationConfig.WWW_PATH;
            case "databasepath":
                return ApplicationConfig.DATABASE_PATH;
            default:
                return "";
        }
    }
} 
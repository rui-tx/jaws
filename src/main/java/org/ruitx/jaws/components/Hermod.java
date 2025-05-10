package org.ruitx.jaws.components;

import org.ruitx.jaws.commands.CommandList;
import org.ruitx.jaws.interfaces.Command;
import org.tinylog.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.ruitx.jaws.configs.ApplicationConfig.WWW_PATH;

/**
 * Hermes is a utility class that handles template processing and page assembly.
 * It provides methods for processing templates with variables, assembling full pages,
 * and rendering template files.
 */
public final class Hermod {

    private static final String DEFAULT_BODY_PATH = "_body.html";
    private static final ThreadLocal<String> BODY_PATH = ThreadLocal.withInitial(() -> DEFAULT_BODY_PATH);

    // Global template variables that persist across requests
    private static final ThreadLocal<Map<String, String>> TEMPLATE_VARIABLES =
            ThreadLocal.withInitial(HashMap::new);


    private Hermod() {
    }

    /**
     * Set a template variable for the current request.
     *
     * @param name  the variable name
     * @param value the variable value
     */
    public static void setTemplateVariable(String name, String value) {
        if (name != null && !name.isEmpty()) {
            TEMPLATE_VARIABLES.get().put(name, value);
        }
    }

    /**
     * Get a template variable for the current request.
     *
     * @param name the variable name
     * @return the variable value or null if not found
     */
    public static String getTemplateVariable(String name) {
        return TEMPLATE_VARIABLES.get().get(name);
    }

    /**
     * Remove a template variable for the current request.
     *
     * @param name the variable name
     */
    public static void removeTemplateVariable(String name) {
        TEMPLATE_VARIABLES.get().remove(name);
    }

    /**
     * Clear all template variables for the current request.
     * Should be called at the end of request processing to prevent memory leaks.
     */
    public static void clearTemplateVariables() {
        TEMPLATE_VARIABLES.get().clear();
        // Important to prevent memory leaks in thread pools
        TEMPLATE_VARIABLES.remove();
    }


    /**
     * Get the body path for the default body template.
     * This method is synchronized to prevent concurrent access.
     *
     * @return the path to the default template.
     */
    public static synchronized String getBodyPath() {
        return BODY_PATH.get();
    }

    /**
     * Set the body path for the default body template.
     * This method is synchronized to prevent concurrent access.
     *
     * @param path the path to the default template.
     */
    public static synchronized void setBodyPath(String path) {
        if (path != null && !path.isEmpty()) {
            BODY_PATH.set(path);
            return;
        }
        BODY_PATH.set(DEFAULT_BODY_PATH);
    }

    /**
     * Process a template file, replacing placeholders with actual values.
     *
     * @param templateFile The template file to process
     * @return the processed template
     * @throws IOException if there's an error reading the template file
     */
    public static String processTemplate(File templateFile) throws IOException {
        String template = new String(Files.readAllBytes(Path.of(templateFile.getPath())));
        return processTemplate(template);
    }

    /**
     * Process a template string, replacing placeholders with actual values.
     *
     * @param template The template string to process
     * @return the processed template
     * @throws IOException if there's an error processing the template
     */
    public static String processTemplate(String template) throws IOException {
        return processTemplate(template, new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    /**
     * Process a template string with parameters, replacing placeholders with actual values.
     *
     * @param template    The template string to process
     * @param queryParams The query parameters map
     * @param bodyParams  The body parameters map
     * @return the processed template
     * @throws IOException if there's an error processing the template
     */
    public static String processTemplate(String template, Map<String, String> queryParams, Map<String, String> bodyParams) throws IOException {
        if (queryParams == null) {
            queryParams = new LinkedHashMap<>();
        }
        if (bodyParams == null) {
            bodyParams = new LinkedHashMap<>();
        }
        return processTemplateWithParams(template, queryParams, bodyParams);
    }

    /**
     * Core template processing method that handles variable substitution and command execution.
     */
    private static String processTemplateWithParams(String template, Map<String, String> queryParams, Map<String, String> bodyParams) {
        String regex = "\\{\\{([^}]*)}}";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(template);
        StringBuilder result = new StringBuilder();

        Map<String, String> requestParams = new LinkedHashMap<>(queryParams);
        requestParams.putAll(bodyParams);

        while (matcher.find()) {
            String placeholderContent = matcher.group(1).trim();
            if (placeholderContent.isEmpty()) {
                matcher.appendReplacement(result, "{{}}");
                continue;
            }

            String[] parts = placeholderContent.split("\\s*\\(", 2);
            String commandName = parts[0].trim();
            Command commandExecutor = CommandList.LIST.get(commandName);

            String replacement;
            if (commandExecutor != null) {
                replacement = commandExecutor.execute(placeholderContent);
            } else {
                // First check request parameters
                String paramValue = requestParams.get(placeholderContent);
                if (paramValue != null) {
                    replacement = paramValue;
                }
                // Then check thread-local template variables
                else if (TEMPLATE_VARIABLES.get().containsKey(placeholderContent)) {
                    replacement = TEMPLATE_VARIABLES.get().get(placeholderContent);
                } else {
                    if (placeholderContent.equals("_BODY_CONTENT_")) {
                        try {
                            synchronized (Hermod.class) {
                                if (getBodyPath() == null || getBodyPath().isEmpty()) {
                                    setBodyPath(DEFAULT_BODY_PATH);
                                }
                                replacement = processTemplate(new String(Files.readAllBytes(Path.of(WWW_PATH + getBodyPath()))));
                            }
                        } catch (IOException e) {
                            Logger.error("Error reading default body content: " + e.getMessage());
                            replacement = "{{" + placeholderContent + "}}";
                        }
                    } else {
                        replacement = "{{" + placeholderContent + "}}";
                    }
                }
            }

            // Use a safe replacement method that doesn't interpret $ as group references
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }


    /**
     * Escape dollar signs in the input string for regex processing.
     * This method now also handles potential regex group references.
     */
    private static String escapeDollarSigns(String input) {
        if (input == null) {
            return "";
        }
        String escaped = input.replaceAll("\\$(\\d+)", "\\\\\\$$1");
        return escaped.replaceAll("\\$", "\\\\\\$");
    }

    /**
     * Restore escaped dollar signs in the output string.
     * This method is now only used for command execution results.
     */
    private static String restoreDollarSigns(String input) {
        if (input == null) {
            return "";
        }
        return input.replaceAll("\\\\\\$", "\\$");
    }

    /**
     * Assemble a full page by combining a base template with a partial template.
     *
     * @param baseTemplatePath    the path to the base template file
     * @param partialTemplatePath the path to the partial template file
     * @return the assembled page
     * @throws IOException if there's an error reading or processing the templates
     */
    public static String assemblePage(String baseTemplatePath, String partialTemplatePath) throws IOException {
        HashMap<String, String> params = new HashMap<>();
        params.put("_BODY_CONTENT_", "{{renderPartial(\"" + partialTemplatePath + "\")}}");
        return processTemplate(new String(Files.readAllBytes(Path.of(WWW_PATH + baseTemplatePath))), params, null);
    }

    /**
     * Assemble a full page by combining a base template with raw content.
     *
     * @param baseTemplatePath the path to the base template file
     * @param content          the raw content to insert
     * @return the assembled page
     * @throws IOException if there's an error reading or processing the template
     */
    public static String assemblePageWithContent(String baseTemplatePath, String content) throws IOException {
        HashMap<String, String> params = new HashMap<>();
        params.put("_BODY_CONTENT_", content);
        return processTemplate(new String(Files.readAllBytes(Path.of(WWW_PATH + baseTemplatePath))), params, null);
    }

    /**
     * Render a template file.
     *
     * @param templatePath the path to the template file
     * @return the rendered template
     * @throws IOException if there's an error reading or processing the template
     */
    public static String renderTemplate(String templatePath) throws IOException {
        return "{{renderPartial(\"" + templatePath + "\")}}";
    }
}
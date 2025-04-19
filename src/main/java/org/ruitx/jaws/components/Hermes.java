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
public final class Hermes {

    private static final String DEFAULT_BODY_PATH = "_body.html";
    private static String BODY_PATH;

    /**
     * Get the body path for the default body template.
     * This method is synchronized to prevent concurrent access.
     *
     * @return the path to the default template.
     */
    public static synchronized String getBodyPath() {
        return BODY_PATH;
    }

    /**
     * Set the body path for the default body template.
     * This method is synchronized to prevent concurrent access.
     *
     * @param path the path to the default template.
     */
    public static synchronized void setBodyPath(String path) {
        if (path != null && !path.isEmpty()) {
            BODY_PATH = path;
            return;
        }
        BODY_PATH = DEFAULT_BODY_PATH;
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
            String escapedContent = escapeDollarSigns(placeholderContent);
            String commandName = escapedContent.split("\\s*\\(")[0];
            Command commandExecutor = CommandList.LIST.get(commandName);

            if (commandExecutor != null) {
                String commandResult = commandExecutor.execute(escapedContent);
                matcher.appendReplacement(result, Matcher.quoteReplacement(restoreDollarSigns(commandResult)));
            } else {
                String paramValue = requestParams.get(escapedContent);
                if (paramValue != null) {
                    matcher.appendReplacement(result, Matcher.quoteReplacement(paramValue));
                } else {
                    if (escapedContent.equals("_BODY_CONTENT_")) {
                        try {
                            synchronized (Hermes.class) {
                                if (getBodyPath() == null || getBodyPath().isEmpty()) {
                                    setBodyPath(DEFAULT_BODY_PATH);
                                }
                                paramValue = processTemplate(new String(Files.readAllBytes(Path.of(WWW_PATH + getBodyPath()))));
                            }
                        } catch (IOException e) {
                            Logger.error("Error reading default body content: " + e.getMessage());
                            matcher.appendReplacement(result, "{{" + placeholderContent + "}}");
                        }
                        matcher.appendReplacement(result, Matcher.quoteReplacement(restoreDollarSigns(paramValue)));
                    } else {
                        matcher.appendReplacement(result, "{{" + placeholderContent + "}}");
                    }
                }
            }
        }

        matcher.appendTail(result);
        return result.toString();
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

    /**
     * Escape dollar signs in the input string for regex processing.
     */
    private static String escapeDollarSigns(String input) {
        return input.replaceAll("\\$", "\\\\\\$");
    }

    /**
     * Restore escaped dollar signs in the output string.
     */
    private static String restoreDollarSigns(String input) {
        return input.replaceAll("\\\\\\$", "\\$");
    }
}

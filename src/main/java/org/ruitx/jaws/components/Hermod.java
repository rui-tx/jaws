package org.ruitx.jaws.components;

import org.ruitx.jaws.utils.ThymeleafUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.ruitx.jaws.types.Context;
import org.ruitx.jaws.utils.JawsLogger;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.ruitx.jaws.configs.ApplicationConfig.WWW_PATH;
import static org.ruitx.jaws.configs.ApplicationConfig.HERMOD_DEVELOPMENT_MODE;
import static org.ruitx.jaws.configs.ApplicationConfig.HERMOD_TEMPLATE_CACHE_TTL;

/**
 * Hermod is a utility class that handles template processing and page assembly using Thymeleaf.
 * It provides methods for processing templates with variables, assembling full pages, 
 * and rendering template files.
 */
public final class Hermod {

    private static final String DEFAULT_BODY_PATH = "_body.html";
    private static final ThreadLocal<String> BODY_PATH = ThreadLocal.withInitial(() -> DEFAULT_BODY_PATH);

    // Global template variables that persist across requests
    private static final ThreadLocal<Map<String, Object>> TEMPLATE_VARIABLES =
            ThreadLocal.withInitial(HashMap::new);

    // Thymeleaf template engine - configured once and reused
    private static final TemplateEngine templateEngine = createTemplateEngine();

    // Utility objects for templates
    private static final ThymeleafUtils utils = new ThymeleafUtils();

    private Hermod() {
    }

    /**
     * Create and configure the Thymeleaf template engine.
     */
    private static TemplateEngine createTemplateEngine() {
        TemplateEngine engine = new TemplateEngine();
        
        // Configure file template resolver for loading templates from the file system
        FileTemplateResolver fileResolver = new FileTemplateResolver();
        fileResolver.setPrefix(WWW_PATH);
        fileResolver.setSuffix("");
        fileResolver.setTemplateMode(TemplateMode.HTML);
        
        // Configure caching based on development mode
        if (HERMOD_DEVELOPMENT_MODE) {
            fileResolver.setCacheable(false);
            fileResolver.setCacheTTLMs(0L);
            JawsLogger.trace("Hermod template caching disabled for live reload");
        } else {
            fileResolver.setCacheable(true);
            fileResolver.setCacheTTLMs(HERMOD_TEMPLATE_CACHE_TTL);
            JawsLogger.trace("Hermod template caching enabled (TTL: " + HERMOD_TEMPLATE_CACHE_TTL + "ms)");
        }
        
        fileResolver.setOrder(1);
        
        engine.addTemplateResolver(fileResolver);
        
        return engine;
    }

    /**
     * Set a template variable for the current request.
     *
     * @param name  the variable name
     * @param value the variable value
     */
    public static void setTemplateVariable(String name, Object value) {
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
    public static Object getTemplateVariable(String name) {
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
     * Process a template file using Thymeleaf.
     *
     * @param templateFile The template file to process
     * @param request      The HTTP servlet request
     * @param response     The HTTP servlet response
     * @return the processed template
     * @throws IOException if there's an error reading the template file
     */
    public static String processTemplate(File templateFile, HttpServletRequest request, HttpServletResponse response) throws IOException {
        String templatePath = templateFile.getName();
        return processTemplate(templatePath, new LinkedHashMap<>(), new LinkedHashMap<>(), request, response, null);
    }

    /**
     * Process a template using the template path.
     *
     * @param templatePath The template path to process
     * @param request      The HTTP servlet request
     * @param response     The HTTP servlet response
     * @return the processed template
     * @throws IOException if there's an error processing the template
     */
    public static String processTemplate(String templatePath, HttpServletRequest request, HttpServletResponse response, Context context) throws IOException {
        return processTemplate(templatePath, new LinkedHashMap<>(), new LinkedHashMap<>(), request, response, context);
    }

    public static String processTemplate(String template, Map<String, String> queryParams, Map<String, String> bodyParams, HttpServletRequest request, HttpServletResponse response) throws IOException {
        return processTemplate(template, queryParams, bodyParams, request, response, null);
    }

    /**
     * Process a template string with parameters using Thymeleaf.
     * If the template parameter looks like a file path, treat it as such.
     * Otherwise, fall back to reading it as a file path.
     *
     * @param template    The template path or content to process
     * @param queryParams The query parameters map
     * @param bodyParams  The body parameters map
     * @param request     The HTTP servlet request
     * @param response    The HTTP servlet response
     * @return the processed template
     * @throws IOException if there's an error processing the template
     */
    public static String processTemplate(String template, Map<String, String> queryParams, Map<String, String> bodyParams, HttpServletRequest request, HttpServletResponse response, Context templateContext) throws IOException {
        if (queryParams == null) {
            queryParams = new LinkedHashMap<>();
        }
        if (bodyParams == null) {
            bodyParams = new LinkedHashMap<>();
        }
        
        // If template looks like a file path (doesn't contain HTML tags), use it as a template path
        if (!template.contains("<") && !template.contains(">")) {
            return processThymeleafTemplate(template, queryParams, bodyParams, request, response, templateContext);
        }
        
        // Otherwise, just return content
        JawsLogger.trace("Received template content. Template: {}", template);
        return template;
    }

    /**
     * Process a template using Thymeleaf engine.
     */
    private static String processThymeleafTemplate(String templatePath, Map<String, String> queryParams, Map<String, String> bodyParams, HttpServletRequest request, HttpServletResponse response, Context templateContext) {
        try {
            // Create Thymeleaf web context
            WebContext context = createThymeleafWebContext(queryParams, bodyParams, request, response);

            // Add template context variables
            if (templateContext != null) {  
                context.setVariables(templateContext.context());
            }
            
            // Process the template using the file path
            return templateEngine.process(templatePath, context);
            
        } catch (Exception e) {
            JawsLogger.error("Error processing Thymeleaf template '{}': {}", templatePath, e.getMessage());
            return "Error processing template: " + templatePath;
        } finally {
            // Clean up template variables
            clearTemplateVariables();
        }
    }

    /**
     * Create a Thymeleaf web context with all available variables.
     */
    private static WebContext createThymeleafWebContext(Map<String, String> queryParams, Map<String, String> bodyParams, HttpServletRequest request, HttpServletResponse response) {
        // Create the web application instance
        JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(request.getServletContext());
        
        // Create web context with proper servlet request/response
        WebContext context = new WebContext(application.buildExchange(request, response));
        
        // Add template variables
        context.setVariables(TEMPLATE_VARIABLES.get());
        
        // Add request parameters
        context.setVariable("queryParams", queryParams);
        context.setVariable("bodyParams", bodyParams);
        
        // Add utility objects
        context.setVariable("utils", utils);
        
        // Add individual parameters to root context for easy access
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : bodyParams.entrySet()) {
            context.setVariable(entry.getKey(), entry.getValue());
        }
        
        return context;
    }

    /**
     * Assemble a full page by combining a base template with a partial template using Thymeleaf.
     *
     * @param baseTemplatePath    the path to the base template file
     * @param partialTemplatePath the path to the partial template file
     * @param request            The HTTP servlet request
     * @param response           The HTTP servlet response
     * @return the assembled page
     * @throws IOException if there's an error reading or processing the templates
     */
    public static String assemblePage(String baseTemplatePath, String partialTemplatePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            WebContext context = createThymeleafWebContext(new HashMap<>(), new HashMap<>(), request, response);
            context.setVariable("bodyContent", partialTemplatePath);
            
            return templateEngine.process(baseTemplatePath, context);
        } catch (Exception e) {
            JawsLogger.error("Error assembling page: " + e.getMessage(), e);
            throw new IOException("Failed to assemble page", e);
        }
    }

    /**
     * Assemble a full page by combining a base template with raw content using Thymeleaf.
     *
     * @param baseTemplatePath the path to the base template file
     * @param content          the raw content to insert
     * @param request         The HTTP servlet request
     * @param response        The HTTP servlet response
     * @return the assembled page
     * @throws IOException if there's an error reading or processing the template
     */
    public static String assemblePageWithContent(String baseTemplatePath, String content, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            WebContext context = createThymeleafWebContext(new HashMap<>(), new HashMap<>(), request, response);
            context.setVariable("bodyContent", content);
            
            return templateEngine.process(baseTemplatePath, context);
        } catch (Exception e) {
            JawsLogger.error("Error assembling page with content: " + e.getMessage(), e);
            throw new IOException("Failed to assemble page with content", e);
        }
    }

    /**
     * Render a template file using Thymeleaf.
     *
     * @param templatePath the path to the template file
     * @param request     The HTTP servlet request
     * @param response    The HTTP servlet response
     * @return the rendered template
     * @throws IOException if there's an error reading or processing the template
     */
    public static String renderTemplate(String templatePath, HttpServletRequest request, HttpServletResponse response) throws IOException {
        try {
            WebContext context = createThymeleafWebContext(new HashMap<>(), new HashMap<>(), request, response);
            return templateEngine.process(templatePath, context);
        } catch (Exception e) {
            JawsLogger.error("Error rendering template: " + e.getMessage(), e);
            throw new IOException("Failed to render template", e);
        }
    }

}
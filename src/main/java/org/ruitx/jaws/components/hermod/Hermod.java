package org.ruitx.jaws.components.hermod;

import static org.ruitx.jaws.configs.ApplicationConfig.HERMOD_DEVELOPMENT_MODE;
import static org.ruitx.jaws.configs.ApplicationConfig.HERMOD_TEMPLATE_CACHE_TTL;
import static org.ruitx.jaws.configs.ApplicationConfig.WWW_PATH;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import nz.net.ultraq.thymeleaf.layoutdialect.LayoutDialect;
import org.ruitx.jaws.utils.ThymeleafUtils;
import org.ruitx.jaws.utils.logger.JawsLogger;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.TemplateSpec;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.FileTemplateResolver;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Hermod is a utility class that handles template processing and page assembly using Thymeleaf. It
 * provides methods for rendering templates with variables, composing full pages, and managing
 * template variables across requests.
 * <p>
 *
 * <p>Main methods:</p>
 * <ul>
 *   <li>{@link #render(String, Map, Map, HttpServletRequest, HttpServletResponse, Context)} - Render template with full context</li>
 *   <li>{@link #render(String, HttpServletRequest, HttpServletResponse)} - Render template without parameters</li>
 *   <li>{@link #composePage(String, String, HttpServletRequest, HttpServletResponse)} - Compose page from base + partial</li>
 * </ul>
 */
public final class Hermod {

  private static final String DEFAULT_BODY_PATH = "_body.html";
  private static final ThreadLocal<String> BODY_PATH = ThreadLocal.withInitial(
      () -> DEFAULT_BODY_PATH);

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
      JawsLogger.trace(
          "Hermod template caching enabled (TTL: " + HERMOD_TEMPLATE_CACHE_TTL + "ms)");
    }

    fileResolver.setOrder(1);

    engine.addTemplateResolver(fileResolver);

    // Add layout dialect for template inheritance
    engine.addDialect(new LayoutDialect());

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
   * Clear all template variables for the current request. Should be called at the end of request
   * processing to prevent memory leaks.
   */
  public static void clearTemplateVariables() {
    TEMPLATE_VARIABLES.get().clear();
    // Important to prevent memory leaks in thread pools
    TEMPLATE_VARIABLES.remove();
  }

  /**
   * Get the body path for the default body template. This method is synchronized to prevent
   * concurrent access.
   *
   * @return the path to the default template.
   */
  public static synchronized String getBodyPath() {
    return BODY_PATH.get();
  }

  /**
   * Set the body path for the default body template. This method is synchronized to prevent
   * concurrent access.
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
   * Render a template with full context including parameters and variables.
   *
   * @param templatePath    The template path to render
   * @param queryParams     The query parameters map
   * @param bodyParams      The body parameters map
   * @param request         The HTTP servlet request
   * @param response        The HTTP servlet response
   * @param templateContext Additional context variables for the template
   * @return the rendered template
   * @throws IOException if there's an error rendering the template
   */
  public static String render(String templatePath,
      Map<String, String> queryParams,
      Map<String, String> bodyParams,
      HttpServletRequest request,
      HttpServletResponse response,
      Context templateContext) throws IOException {
    if (queryParams == null) {
      queryParams = new LinkedHashMap<>();
    }
    if (bodyParams == null) {
      bodyParams = new LinkedHashMap<>();
    }

    // If template looks like a file path (doesn't contain HTML tags), use it as a template path
    if (!templatePath.contains("<") && !templatePath.contains(">")) {
      return processTemplateInternal(templatePath, queryParams, bodyParams, request, response,
          templateContext);
    }

    // Otherwise, just return content
    JawsLogger.trace("Received template content. Template: {}", templatePath);
    return templatePath;
  }

  /**
   * Render a template without parameters (simple overload).
   *
   * @param templatePath The template path to render
   * @param request      The HTTP servlet request
   * @param response     The HTTP servlet response
   * @return the rendered template
   * @throws IOException if there's an error rendering the template
   */
  public static String render(String templatePath,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    return render(templatePath, new LinkedHashMap<>(), new LinkedHashMap<>(), request, response,
        null);
  }

  /**
   * Render a template with parameters but no additional context.
   *
   * @param templatePath The template path to render
   * @param queryParams  The query parameters map
   * @param bodyParams   The body parameters map
   * @param request      The HTTP servlet request
   * @param response     The HTTP servlet response
   * @return the rendered template
   * @throws IOException if there's an error rendering the template
   */
  public static String render(String templatePath,
      Map<String, String> queryParams,
      Map<String, String> bodyParams,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    return render(templatePath, queryParams, bodyParams, request, response, null);
  }

  /**
   * Render a specific fragment from a template using Thymeleaf TemplateSpec.
   *
   * @param templatePath    the path to the template file (relative to WWW_PATH)
   * @param fragmentName    the fragment selector name defined via th:fragment
   * @param queryParams     the query parameters map
   * @param bodyParams      the body parameters map
   * @param request         the HTTP servlet request
   * @param response        the HTTP servlet response
   * @param templateContext additional context variables
   * @return the processed fragment HTML
   * @throws IOException if there's an error rendering the fragment
   */
  public static String renderFragment(String templatePath,
      String fragmentName,
      Map<String, String> queryParams,
      Map<String, String> bodyParams,
      HttpServletRequest request,
      HttpServletResponse response,
      Context templateContext) throws IOException {
    if (queryParams == null) {
      queryParams = new LinkedHashMap<>();
    }
    if (bodyParams == null) {
      bodyParams = new LinkedHashMap<>();
    }

    try {
      // Build Thymeleaf web context identical to page rendering
      WebContext context = createThymeleafWebContext(queryParams, bodyParams, request, response);

      // Add additional variables if provided
      if (templateContext != null) {
        context.setVariables(templateContext.context());
      }

      // Build a TemplateSpec targeting the fragment
      TemplateSpec spec =
          new TemplateSpec(templatePath, Set.of(fragmentName), TemplateMode.HTML, null);

      // Process only the fragment
      return templateEngine.process(spec, context);

    } catch (Exception e) {
      JawsLogger.error("Error processing Thymeleaf fragment '{} :: {}': {}", templatePath,
          fragmentName, e.getMessage());
      return "Error processing fragment: " + templatePath + " :: " + fragmentName;
    } finally {
      // Clean up template variables
      clearTemplateVariables();
    }
  }

  /**
   * Convenience overload to render a fragment without extra context.
   */
  public static String renderFragment(String templatePath,
      String fragmentName,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    return renderFragment(templatePath, fragmentName, new LinkedHashMap<>(), new LinkedHashMap<>(),
        request, response, null);
  }

  /**
   * Compose a full page by combining a base template with a partial template.
   *
   * @param baseTemplatePath    The path to the base template file
   * @param partialTemplatePath The path to the partial template file
   * @param request             The HTTP servlet request
   * @param response            The HTTP servlet response
   * @return the composed page
   * @throws IOException if there's an error composing the page
   */
  public static String composePage(String baseTemplatePath,
      String partialTemplatePath,
      HttpServletRequest request,
      HttpServletResponse response) throws IOException {
    try {
      WebContext context = createThymeleafWebContext(new HashMap<>(), new HashMap<>(), request,
          response);
      context.setVariable("bodyContent", partialTemplatePath);

      return templateEngine.process(baseTemplatePath, context);
    } catch (Exception e) {
      JawsLogger.error("Error composing page: " + e.getMessage(), e);
      throw new IOException("Failed to compose page", e);
    }
  }

  /**
   * Process a Thymeleaf template file with parameters.
   *
   * @param templatePath    The path to the template file
   * @param queryParams     The query parameters map
   * @param bodyParams      The body parameters map
   * @param request         The HTTP servlet request
   * @param response        The HTTP servlet response
   * @param templateContext Additional context variables for the template
   * @return the processed template as a string
   */
  private static String processTemplateInternal(String templatePath,
      Map<String, String> queryParams,
      Map<String, String> bodyParams,
      HttpServletRequest request,
      HttpServletResponse response,
      Context templateContext) {
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
      JawsLogger.error("Error processing Thymeleaf template '{}': {}", templatePath,
          e.getMessage());
      return "Error processing template: " + templatePath;
    } finally {
      // Clean up template variables
      clearTemplateVariables();
    }
  }

  /**
   * Create a Thymeleaf web context with all available variables. This method initializes the web
   * context with the request and response, and sets up the template variables, query parameters,
   * body parameters, and utility objects.
   *
   * @param queryParams the query parameters map
   * @param bodyParams  the body parameters map
   * @param request     the HTTP servlet request
   * @param response    the HTTP servlet response
   */
  private static WebContext createThymeleafWebContext(Map<String, String> queryParams,
      Map<String, String> bodyParams,
      HttpServletRequest request,
      HttpServletResponse response) {
    // Create the web application instance
    JakartaServletWebApplication application = JakartaServletWebApplication.buildApplication(
        request.getServletContext());

    // Create web context with proper servlet request/response
    WebContext context = new WebContext(application.buildExchange(request, response));

    // Populate context
    context.setVariables(TEMPLATE_VARIABLES.get());
    context.setVariable("queryParams", queryParams);
    context.setVariable("bodyParams", bodyParams);
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
   * Render a specific fragment headlessly (without servlet request/response). Useful for background
   * jobs or other non-HTTP contexts.
   *
   * @param templatePath the template path
   * @param fragmentName the fragment name defined via th:fragment
   * @param variables    map of variables to expose to the fragment
   * @return rendered HTML string (empty on error)
   */
  public static String renderFragmentHeadless(String templatePath,
      String fragmentName,
      Map<String, Object> variables) {
    try {
      org.thymeleaf.context.Context thymeCtx = new org.thymeleaf.context.Context();
      if (variables != null) {
        for (Map.Entry<String, Object> e : variables.entrySet()) {
          thymeCtx.setVariable(e.getKey(), e.getValue());
        }
      }

      TemplateSpec spec = new TemplateSpec(templatePath, Set.of(fragmentName), TemplateMode.HTML,
          null);
      return templateEngine.process(spec, thymeCtx);
    } catch (Exception e) {
      JawsLogger.error("Error processing headless fragment '{} :: {}': {}", templatePath,
          fragmentName, e.getMessage());
      return "";
    }
  }
}
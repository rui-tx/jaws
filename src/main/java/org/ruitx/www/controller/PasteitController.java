package org.ruitx.www.controller;

import org.ruitx.jaws.components.Bragi;
import org.ruitx.jaws.components.Tyr;
import org.ruitx.jaws.interfaces.Route;
import org.ruitx.jaws.types.APIResponse;
import org.ruitx.www.dto.PasteCreateRequest;
import org.ruitx.www.model.Paste;
import org.ruitx.www.service.PasteService;

import java.util.HashMap;
import java.util.Map;

import static org.ruitx.jaws.strings.RequestType.GET;
import static org.ruitx.jaws.strings.RequestType.POST;
import static org.ruitx.jaws.strings.ResponseCode.OK;
import static org.ruitx.jaws.strings.ResponseType.JSON;

public class PasteitController extends Bragi {

    private static final String BASE_HTML_PATH = "pasteit/index.html";
    private static final String BODY_HTML_PATH = "pasteit/_body.html";
    private static final String VIEW_HTML_PATH = "pasteit/view.html";
    
    private final PasteService pasteService;

    public PasteitController() {
        bodyHtmlPath = BODY_HTML_PATH;
        this.pasteService = new PasteService();
    }

    @Route(endpoint = "/pasteit", method = GET)
    public void renderIndex() {
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, BODY_HTML_PATH));
    }

    @Route(endpoint = "/pasteit/:id", method = GET)
    public void viewPaste() {
        String pasteId = getPathParam("id");
        String password = getQueryParam("password");
        
        APIResponse<Paste> response = pasteService.getPaste(pasteId, password);
        
        Map<String, String> context = new HashMap<>();
        
        if (!response.success()) {
            // Handle error cases - build error HTML
            String errorMessage = switch (response.code()) {
                case "404 NOT FOUND" -> "Paste not found";
                case "410 GONE" -> "This paste has expired";
                case "401 UNAUTHORIZED" -> "This paste is password protected";
                default -> "Error loading paste";
            };
            
            String errorHtml = String.format("""
                <div class="error-container">
                    <h2>%s</h2>
                    <p>The paste you're looking for might not exist, be private, or have expired.</p>
                    <a href="/pasteit">← Create a new paste</a>
                </div>
                """, errorMessage);
            
            context.put("viewContent", errorHtml);
        } else {
            // Handle success case - build paste view HTML
            Paste paste = response.data();
            
            String languageSpan = paste.language() != null ? 
                String.format("<span>Language: %s</span>", paste.language()) : "";
            
            String pasteHtml = String.format("""
                <div class="paste-view">
                    <div class="paste-header">
                        <h1>%s</h1>
                        <div class="paste-meta">
                            <span>ID: %s</span>
                            <span>Views: %s</span>
                            <span>Created: %s</span>
                            %s
                        </div>
                    </div>
                    
                    <div class="paste-content">
                        <pre><code>%s</code></pre>
                    </div>
                    
                    <div class="paste-actions">
                        <button onclick="copyContent()" class="copy-btn">copy content</button>
                        <button onclick="copyUrl()" class="copy-url-btn">copy link</button>
                        <a href="/pasteit">new paste</a>
                    </div>
                </div>
                """, 
                paste.title() != null ? escapeHtml(paste.title()) : "Untitled",
                escapeHtml(paste.id()),
                paste.viewCount(),
                paste.createdAt(),
                languageSpan,
                escapeHtml(paste.content())
            );
            
            context.put("viewContent", pasteHtml);
        }
        
        setContext(context);
        sendHTMLResponse(OK, assemblePage(BASE_HTML_PATH, VIEW_HTML_PATH));
    }

    @Route(endpoint = "/api/v1/pasteit", method = POST, responseType = JSON)
    public void createPaste(PasteCreateRequest request) {
        // Get user ID from JWT token if available
        Integer userId = null;
        String token = getCurrentToken();
        if (token != null && !token.isEmpty()) {
            try {
                String userIdStr = Tyr.getUserIdFromJWT(token);
                userId = Integer.parseInt(userIdStr);
            } catch (Exception e) {
                // Token invalid or expired, continue as anonymous
            }
        }
        
        // Get client info
        String ipAddress = getClientIpAddress();
        String userAgent = getHeaders().get("User-Agent");
        
        APIResponse<String> response = pasteService.createPaste(request, userId, ipAddress, userAgent);
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), response.data());
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }

    @Route(endpoint = "/api/v1/pasteit/:id", method = GET, responseType = JSON)
    public void getPasteAPI() {
        String pasteId = getPathParam("id");
        String password = getQueryParam("password");
        
        APIResponse<Paste> response = pasteService.getPaste(pasteId, password);
        
        if (response.success()) {
            sendSucessfulResponse(response.code(), response.data());
        } else {
            sendErrorResponse(response.code(), response.info());
        }
    }
    
    /**
     * Simple HTML escaping to prevent XSS
     */
    private String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#x27;");
    }
} 
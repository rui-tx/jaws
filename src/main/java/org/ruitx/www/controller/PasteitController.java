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
                case "401 UNAUTHORIZED" -> {
                    // Special handling for password-protected pastes
                    if (response.info().contains("Password required")) {
                        yield createPasswordForm(pasteId);
                    } else {
                        yield "Invalid password provided";
                    }
                }
                default -> "Error loading paste";
            };
            
            if (!response.code().equals("401 UNAUTHORIZED") || !response.info().contains("Password required")) {
                String errorHtml = String.format("""
                    <div class="error-container">
                        <h2>%s</h2>
                        <p>The paste you're looking for might not exist, be private, or have expired.</p>
                        <a href="/pasteit">← Create a new paste</a>
                    </div>
                    """, errorMessage);
                context.put("viewContent", errorHtml);
            } else {
                context.put("viewContent", errorMessage);
            }
        } else {
            // Handle success case - build paste view HTML
            Paste paste = response.data();
            
            String languageSpan = paste.language() != null ? 
                String.format("<span>Language: %s</span>", paste.language()) : "";
            
            // Format timestamp
            String formattedDate = paste.createdAt() != null ? 
                java.time.Instant.ofEpochSecond(paste.createdAt()).toString().replace("T", " ").replace("Z", " UTC") : 
                "Unknown";
            
            // Determine language class for Prism.js
            String languageClass = paste.language() != null ? 
                "language-" + mapLanguageToPrism(paste.language()) : "language-plaintext";
            
            // Handle privacy indicator
            String privacyIndicator = paste.isPrivate() != null && paste.isPrivate() ? 
                "<span class=\"privacy-badge private\">🔒 Private</span>" : 
                "<span class=\"privacy-badge public\">🌐 Public</span>";
            
            // Handle password protection indicator  
            String passwordIndicator = paste.hasPassword() ? 
                "<span class=\"password-badge\">🔐 Password Protected</span>" : "";
            
            // Handle expiration info
            String expirationInfo = "";
            if (paste.expiresAt() != null) {
                String expirationDate = java.time.Instant.ofEpochSecond(paste.expiresAt()).toString().replace("T", " ").replace("Z", " UTC");
                expirationInfo = String.format("<span class=\"expiration-badge\">⏰ Expires: %s</span>", expirationDate);
            }
            
            String pasteHtml = String.format("""
                <div class="paste-view">
                    <div class="paste-header">
                        <h1>%s</h1>
                        <div class="paste-meta">
                            <span class="paste-id">ID: %s</span>
                            <span class="view-count">👁 %s views</span>
                            <span class="created-date">📅 %s</span>
                            %s
                            %s
                            %s
                            %s
                        </div>
                    </div>
                    
                    <div class="paste-content">
                        <pre class="line-numbers"><code class="%s">%s</code></pre>
                    </div>
                    
                    <div class="paste-actions">
                        <button onclick="copyContent()" class="copy-btn">📋 copy content</button>
                        <button onclick="copyUrl()" class="copy-url-btn">🔗 copy link</button>
                        <a href="/pasteit">✨ new paste</a>
                    </div>
                </div>
                
                <script>
                // Initialize Prism.js highlighting
                if (typeof Prism !== 'undefined') {
                    Prism.highlightAll();
                }
                </script>
                """, 
                paste.title() != null ? escapeHtml(paste.title()) : "Untitled Paste",
                escapeHtml(paste.id()),
                paste.viewCount(),
                formattedDate,
                languageSpan,
                privacyIndicator,
                passwordIndicator,
                expirationInfo,
                languageClass,
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
    
    /**
     * Creates a password form for protected pastes
     */
    private String createPasswordForm(String pasteId) {
        return String.format("""
            <div class="password-form-container">
                <div class="password-form">
                    <h2>🔐 Password Required</h2>
                    <p>This paste is password protected. Please enter the password to view it.</p>
                    
                    <form id="password-form" onsubmit="submitPassword(event)">
                        <div class="form-group">
                            <input type="password" id="paste-password" placeholder="Enter password..." required autofocus>
                            <button type="submit">Unlock</button>
                        </div>
                        <div id="password-error" class="error-message"></div>
                    </form>
                    
                    <a href="/pasteit" class="back-link">← Create a new paste</a>
                </div>
            </div>
            
            <script>
            function submitPassword(event) {
                event.preventDefault();
                const password = document.getElementById('paste-password').value;
                const errorDiv = document.getElementById('password-error');
                
                if (!password.trim()) {
                    errorDiv.textContent = 'Please enter a password';
                    return;
                }
                
                // Redirect with password parameter
                const currentUrl = new URL(window.location);
                currentUrl.searchParams.set('password', password);
                window.location.href = currentUrl.toString();
            }
            </script>
            
            <style>
            .password-form-container {
                display: flex;
                justify-content: center;
                align-items: center;
                min-height: 400px;
                padding: 2rem;
            }
            
            .password-form {
                background: #fff;
                padding: 2rem;
                border-radius: 8px;
                box-shadow: 0 2px 10px rgba(0,0,0,0.1);
                text-align: center;
                max-width: 400px;
                width: 100%%;
            }
            
            .password-form h2 {
                margin-bottom: 1rem;
                color: #333;
            }
            
            .password-form p {
                margin-bottom: 1.5rem;
                color: #666;
            }
            
            .form-group {
                display: flex;
                gap: 0.5rem;
                margin-bottom: 1rem;
            }
            
            .form-group input {
                flex: 1;
                padding: 0.75rem;
                border: 1px solid #ddd;
                border-radius: 4px;
                font-size: 14px;
            }
            
            .form-group button {
                padding: 0.75rem 1.5rem;
                background: #007bff;
                color: white;
                border: none;
                border-radius: 4px;
                cursor: pointer;
                font-size: 14px;
            }
            
            .form-group button:hover {
                background: #0056b3;
            }
            
            .error-message {
                color: #dc3545;
                font-size: 14px;
                margin-top: 0.5rem;
            }
            
            .back-link {
                color: #666;
                text-decoration: none;
                font-size: 14px;
            }
            
            .back-link:hover {
                text-decoration: underline;
            }
            </style>
            """, escapeHtml(pasteId));
    }
    
    /**
     * Maps our language identifiers to Prism.js language classes
     */
    private String mapLanguageToPrism(String language) {
        if (language == null) return "plaintext";
        
        return switch (language.toLowerCase()) {
            case "javascript", "js" -> "javascript";
            case "typescript", "ts" -> "typescript";
            case "python", "py" -> "python";
            case "java" -> "java";
            case "cpp", "c++" -> "cpp";
            case "c" -> "c";
            case "csharp", "c#" -> "csharp";
            case "php" -> "php";
            case "ruby", "rb" -> "ruby";
            case "go" -> "go";
            case "rust", "rs" -> "rust";
            case "kotlin", "kt" -> "kotlin";
            case "swift" -> "swift";
            case "html" -> "html";
            case "css" -> "css";
            case "sql" -> "sql";
            case "bash", "sh" -> "bash";
            case "powershell", "ps1" -> "powershell";
            case "json" -> "json";
            case "xml" -> "xml";
            case "yaml", "yml" -> "yaml";
            case "markdown", "md" -> "markdown";
            case "dockerfile" -> "dockerfile";
            case "plaintext", "text" -> "plaintext";
            default -> "plaintext";
        };
    }
} 
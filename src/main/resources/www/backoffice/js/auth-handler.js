import { refreshAccessToken } from './auth.js';

// Token validation and expiration handling
export function validateTokenAndRedirect() {
    const authToken = localStorage.getItem('auth_token');
    
    if (!authToken) {
        console.log('No auth token found, redirecting to login');
        window.location.href = '/backoffice/login.html';
        return false;
    }
    
    try {
        const tokenPayload = JSON.parse(atob(authToken.split('.')[1]));
        const currentTime = Math.floor(Date.now() / 1000);
        
        if (tokenPayload.exp && tokenPayload.exp < currentTime) {
            console.log('Token expired, attempting refresh...');
            return refreshAccessToken()
                .then(() => {
                    console.log('Token refreshed successfully');
                    window.location.reload();
                    return false;
                })
                .catch((error) => {
                    console.error('Token refresh failed:', error);
                    clearTokens();
                    window.location.href = '/backoffice/login.html';
                    return false;
                });
        }
    } catch (error) {
        console.error('Error validating token:', error);
        clearTokens();
        window.location.href = '/backoffice/login.html';
        return false;
    }
    
    return true;
}

// Clear all authentication tokens
export function clearTokens() {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('refresh_token');
    document.cookie = "auth_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
}

// Initialize HTMX authentication handling
export function initializeHtmxAuth() {
    if (!window.htmx) return;

    // Configure HTMX to include auth headers
    htmx.on('configRequest.auth', (event) => {
        const authToken = localStorage.getItem('auth_token');
        if (authToken) {
            event.detail.headers['Authorization'] = 'Bearer ' + authToken;
        }
    });

    // Handle HTMX authentication errors
    htmx.on('responseError.auth', (event) => {
        if (event.detail.xhr.status === 401) {
            console.log('HTMX request got 401, redirecting to login...');
            clearTokens();
            window.location.href = '/backoffice/login.html';
        }
    });

    // Handle HTMX errors gracefully
    htmx.on('error.auth', (event) => {
        console.error('HTMX error:', event.detail);
        event.preventDefault();
    });
}

// Initialize authentication on page load
export function initializeAuth() {
    // Skip validation if we're on the login page
    if (!window.location.pathname.endsWith('/login.html') && !window.location.pathname.endsWith('/login')) {
        validateTokenAndRedirect();
    }
    
    initializeHtmxAuth();
} 
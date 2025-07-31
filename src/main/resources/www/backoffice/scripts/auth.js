/**
 * Authentication Interceptor for Backoffice
 * Handles adding Authorization header to all outgoing requests
 * and handles 401 Unauthorized responses
 */

document.addEventListener('DOMContentLoaded', function() {
  // Intercept all fetch requests
  const originalFetch = window.fetch;
  
  window.fetch = async function(resource, init = {}) {
    // Get the access token from localStorage
    const token = localStorage.getItem('accessToken');
    
    // Clone the init object to avoid mutating the original
    const newInit = { ...init };
    
    // Set up headers if they don't exist
    newInit.headers = newInit.headers || {};
    
    // Convert headers to a Headers instance if needed
    if (!(newInit.headers instanceof Headers)) {
      newInit.headers = new Headers(newInit.headers);
    }
    
    // Add Authorization header if we have a token and it's not already set
    if (token && !newInit.headers.has('Authorization')) {
      newInit.headers.set('Authorization', `Bearer ${token}`);
    }
    
    // Make the request
    return originalFetch(resource, newInit)
      .then(response => {
        // Handle 401 Unauthorized responses
        if (response.status === 401) {
          // Clear stored tokens
          localStorage.removeItem('accessToken');
          localStorage.removeItem('refreshToken');
          
          // Redirect to login page with a return URL
          const returnUrl = window.location.pathname + window.location.search;
          window.location.href = `/backoffice/login?returnUrl=${encodeURIComponent(returnUrl)}`;
          
          // Return a rejected promise to stop the original request chain
          return Promise.reject(new Error('Unauthorized'));
        }
        return response;
      });
  };
  
  // Also handle XMLHttpRequest for compatibility
  const originalXHROpen = XMLHttpRequest.prototype.open;
  
  XMLHttpRequest.prototype.open = function(method, url, ...args) {
    this._authUrl = url;
    return originalXHROpen.call(this, method, url, ...args);
  };
  
  const originalXHRSend = XMLHttpRequest.prototype.send;
  
  XMLHttpRequest.prototype.send = function(body) {
    const token = localStorage.getItem('accessToken');
    
    if (token && !this._authUrl.includes('/auth/')) {
      this.setRequestHeader('Authorization', `Bearer ${token}`);
    }
    
    // Add error handler for 401
    this.addEventListener('readystatechange', () => {
      if (this.readyState === 4 && this.status === 401) {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        const returnUrl = window.location.pathname + window.location.search;
        window.location.href = `/backoffice/login?returnUrl=${encodeURIComponent(returnUrl)}`;
      }
    });
    
    return originalXHRSend.call(this, body);
  };
});

// Function to check if user is authenticated
export function isAuthenticated() {
  return !!localStorage.getItem('accessToken');
}

// Function to log out
export function logout() {
  const refreshToken = localStorage.getItem('refreshToken');
  
  // Clear tokens immediately
  localStorage.removeItem('accessToken');
  localStorage.removeItem('refreshToken');
  
  // Call logout API if we have a refresh token
  if (refreshToken) {
    fetch('/api/v1/auth/logout', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ refreshToken }),
    }).catch(console.error); // Don't block on logout API call
  }
  
  // Redirect to login page
  window.location.href = '/backoffice/login';
}

// Add logout function to window for global access
window.auth = { isAuthenticated, logout };

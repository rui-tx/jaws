// auth.js
const originalFetch = window.fetch;

function logout() {
    const refreshToken = localStorage.getItem('refresh_token');
    console.log('Initiating logout with refresh token:', refreshToken); // Debug log

    originalFetch('/api/v1/auth/logout', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'Authorization': 'Bearer ' + localStorage.getItem('auth_token')
        },
        body: JSON.stringify({
            refreshToken: refreshToken
        })
    })
        .then(response => {
            console.log('Logout response status:', response.status); // Debug log
            if (!response.ok) { // Check if response is not OK
                // Even if logout API fails, proceed to clear client-side session
                console.warn('Logout API call failed with status:', response.status);
            }
            return response.json().catch(() => ({})); // Attempt to parse JSON, or return empty object on failure
        })
        .then(data => {
            console.log('Logout response data:', data); // Debug log
        })
        .catch(error => {
            console.error('Logout error:', error);
        })
        .finally(() => {
            console.log('Cleaning up session...'); // Debug log
            localStorage.removeItem('auth_token');
            localStorage.removeItem('refresh_token');
            // Ensure the cookie is cleared for all relevant paths if necessary,
            // but for a specific path like /backoffice, this is fine.
            document.cookie = "auth_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
            // It's good practice to also clear any other cookies related to auth if they exist.
            window.location.href = '/backoffice/login.html';
        });
}


let refreshing = false;

async function refreshAccessToken() {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) {
        console.log('No refresh token available for refreshAccessToken.'); // Debug log
        throw new Error('No refresh token available');
    }

    console.log('Attempting to refresh access token.'); // Debug log
    return originalFetch('/api/v1/auth/refresh', {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json'
        },
        body: JSON.stringify({
            refresh_token: refreshToken
        })
    })
        .then(response => {
            console.log('Refresh token response status:', response.status); // Debug log
            if (!response.ok) {
                console.error('Token refresh failed with status:', response.status); // Debug log
                throw new Error('Token refresh failed');
            }
            return response.json();
        })
        .then(data => {
            console.log('Refresh token response data:', data); // Debug log
            if (data.success && data.data && data.data.access_token && data.data.refresh_token) {
                localStorage.setItem('auth_token', data.data.access_token);
                localStorage.setItem('refresh_token', data.data.refresh_token);
                document.cookie = "auth_token=" + data.data.access_token + "; path=/; SameSite=Strict; Secure"; // Added Secure flag
                console.log('Access token refreshed and stored.'); // Debug log
                return data.data.access_token;
            }
            console.error('Invalid refresh response structure:', data); // Debug log
            throw new Error('Invalid refresh response');
        });
}

window.fetch = async function (url, options = {}) {
    let token = localStorage.getItem('auth_token');

    // Add auth header if token exists and it's not a request to the auth server itself
    // to prevent sending tokens to login/refresh endpoints unnecessarily or causing issues.
    const isAuthEndpoint = url.startsWith('/api/v1/auth/');

    if (token && !isAuthEndpoint) {
        options.headers = {
            ...options.headers,
            'Authorization': `Bearer ${token}`
        };
    }

    // Use originalFetch for the actual request
    let response = await originalFetch(url, options);

    // Handle 401 responses, but not for auth endpoints (to avoid loops on failed refresh)
    // and not if we are already trying to refresh the token.
    if (response.status === 401 && !refreshing && !isAuthEndpoint) {
        refreshing = true;
        console.log('Received 401, attempting to refresh token.'); // Debug log
        try {
            const newToken = await refreshAccessToken();
            // Retry the original request with new token
            const newOptions = {
                ...options,
                headers: {
                    ...options.headers,
                    'Authorization': `Bearer ${newToken}`
                }
            };
            console.log('Retrying original request with new token.'); // Debug log
            response = await originalFetch(url, newOptions);
        } catch (error) {
            console.error('Error during token refresh or retrying request:', error); // Debug log
            // If refresh fails, logout
            logout(); // This will redirect to login
            throw error; // Re-throw error to be caught by the original fetch's caller
        } finally {
            refreshing = false;
        }
    }

    return response;
};

// Add click handlers for logout buttons and login page redirect logic
document.addEventListener('DOMContentLoaded', () => {
    console.log('DOM fully loaded and parsed.'); // Debug log

    // --- LOGIN PAGE REDIRECT LOGIC ---
    // Check if the current page is the login page
    if (window.location.pathname.endsWith('/login.html') || window.location.pathname.endsWith('/login')) {
        console.log('Currently on login page.'); // Debug log
        const authToken = localStorage.getItem('auth_token');
        if (authToken) {
            console.log('User is already logged in. Redirecting to dashboard.'); // Debug log
            // You can also add a quick validation ping to your server here to ensure the token is still valid
            // e.g., fetch('/api/v1/auth/validate-token').then(res => if (res.ok) { redirect } else { localStorage.clear(); })
            window.location.href = '/backoffice'; // Or your desired authenticated page
            return; // Stop further execution on this page if redirecting
        } else {
            console.log('User is not logged in. Displaying login page.'); // Debug log
        }
    }
    // --- END LOGIN PAGE REDIRECT LOGIC ---


    console.log('Setting up logout handlers...'); // Debug log

    // Handle navbar logout button
    const logoutButtons = document.querySelectorAll('a[title="Log out"]');
    console.log('Found logout buttons:', logoutButtons.length); // Debug log

    logoutButtons.forEach(button => {
        button.addEventListener('click', function (e) {
            console.log('Logout button clicked'); // Debug log
            e.preventDefault();
            logout();
        });
    });

    // Handle dropdown logout button
    // A more robust selector might be needed if the structure is complex or changes.
    // Example: querySelector('.navbar-dropdown .navbar-item[href*="logout"]') or give it a specific ID/class.
    const dropdownLogoutButton = document.querySelector('.navbar-dropdown a.navbar-item:last-child'); // Assuming this is the logout
    if (dropdownLogoutButton && (dropdownLogoutButton.textContent.toLowerCase().includes('log out') || dropdownLogoutButton.href.includes('logout'))) { // Added a check
        console.log('Found dropdown logout button'); // Debug log
        dropdownLogoutButton.addEventListener('click', function (e) {
            console.log('Dropdown logout clicked'); // Debug log
            e.preventDefault();
            logout();
        });
    } else if (dropdownLogoutButton) {
        console.log('Found a dropdown item, but it might not be the logout button based on current selector/content.');
    }
});
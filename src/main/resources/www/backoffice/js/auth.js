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
            return response.json();
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
            document.cookie = "auth_token=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;";
            window.location.href = '/backoffice/login.html';
        });
}


let refreshing = false;

async function refreshAccessToken() {
    const refreshToken = localStorage.getItem('refresh_token');
    if (!refreshToken) {
        throw new Error('No refresh token available');
    }

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
            if (!response.ok) throw new Error('Token refresh failed');
            return response.json();
        })
        .then(data => {
            if (data.success && data.data) {
                localStorage.setItem('auth_token', data.data.access_token);
                localStorage.setItem('refresh_token', data.data.refresh_token);
                document.cookie = "auth_token=" + data.data.access_token + "; path=/; SameSite=Strict";
                return data.data.access_token;
            }
            throw new Error('Invalid refresh response');
        });
}

window.fetch = async function (url, options = {}) {
    // Add auth header if token exists
    const token = localStorage.getItem('auth_token');
    if (token) {
        options.headers = {
            ...options.headers,
            'Authorization': `Bearer ${token}`
        };
    }

    // Use originalFetch for the actual request
    let response = await originalFetch(url, options);

    // Handle 401 responses
    if (response.status === 401 && !refreshing) {
        refreshing = true;
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
            response = await originalFetch(url, newOptions);
        } catch (error) {
            // If refresh fails, logout
            logout();
            throw error;
        } finally {
            refreshing = false;
        }
    }

    return response;
};

// Add click handlers for logout buttons
document.addEventListener('DOMContentLoaded', () => {
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
    const dropdownLogoutButton = document.querySelector('.navbar-dropdown a.navbar-item:last-child');
    if (dropdownLogoutButton) {
        console.log('Found dropdown logout button'); // Debug log
        dropdownLogoutButton.addEventListener('click', function (e) {
            console.log('Dropdown logout clicked'); // Debug log
            e.preventDefault();
            logout();
        });
    }
});

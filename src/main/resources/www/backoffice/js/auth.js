// Add this as auth.js in your backoffice/js folder
document.addEventListener('DOMContentLoaded', function () {
    // Check if user is logged in
    const token = localStorage.getItem('auth_token');
    if (!token) {
        window.location.href = '/backoffice/login.html';
        return;
    }

    // Add the Authorization header to all HTMX requests
    document.body.addEventListener('htmx:configRequest', function (evt) {
        evt.detail.headers['Authorization'] = 'Bearer ' + token;
    });

    // Handle 401 Unauthorized responses
    document.body.addEventListener('htmx:responseError', function (evt) {
        if (evt.detail.xhr.status === 401) {
            localStorage.removeItem('auth_token');
            window.location.href = '/backoffice/login.html';
        }
    });

    // Add logout functionality
    const logoutButtons = document.querySelectorAll('a[title="Log out"]');
    logoutButtons.forEach(button => {
        button.addEventListener('click', function (e) {
            e.preventDefault();
            localStorage.removeItem('auth_token');
            window.location.href = '/backoffice/login.html';
        });
    });
});
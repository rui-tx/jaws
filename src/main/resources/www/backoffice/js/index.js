import { initializeLayout } from './layout.js';
import { initializeAuth } from './auth-handler.js';

const pageModules = {
    '/backoffice/users': () => import('./partials/users.js'),
    '/backoffice/logs': () => import('./partials/logs.js'),
    '/backoffice/jobs': () => import('./partials/jobs.js'),
    '/backoffice/roles': () => import('./partials/roles.js'),
    '/backoffice/profile': () => import('./partials/profile.js'), // matches /backoffice/profile/{id}
};

function resolveModule(path) {
    // Get the base route (e.g., /backoffice/logs from /backoffice/logs/123)
    const baseRoute = path.split('/').slice(0, 3).join('/');
    
    if (pageModules[baseRoute]) return pageModules[baseRoute];
    if (baseRoute.startsWith('/backoffice/profile')) return pageModules['/backoffice/profile'];
    if (baseRoute.startsWith('/backoffice/jobs')) return pageModules['/backoffice/jobs'];
    if (baseRoute.startsWith('/backoffice/logs')) return pageModules['/backoffice/logs'];
    return null;
}

async function initPage() {
    const resolver = resolveModule(window.location.pathname);
    if (!resolver) return; // no module for this route
    try {
        const mod = await resolver();
        if (typeof mod.default === 'function') {
            mod.default();
        }
    } catch (err) {
        console.error('Failed to load page module', err);
    }
}

// Initial page load
function initialize() {
    initializeAuth();
    initializeLayout();
    initPage();
}

// Initialize on page load
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initialize);
} else {
    initialize();
}

// Handle HTMX page transitions
if (window.htmx) {
    htmx.on('htmx:afterSettle', () => {
        initPage();
    });
} 
const pageModules = {
    '/backoffice/users': () => import('./partials/users.js'),
    '/backoffice/logs': () => import('./partials/logs.js'),
    '/backoffice/jobs': () => import('./partials/jobs.js'),
    '/backoffice/roles': () => import('./partials/roles.js'),
    '/backoffice/profile': () => import('./partials/profile.js'), // matches /backoffice/profile/{id}
};

function resolveModule(path) {
    if (pageModules[path]) return pageModules[path];
    // wildcard match for profile/id paths
    if (path.startsWith('/backoffice/profile')) return pageModules['/backoffice/profile'];
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
if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', initPage);
} else {
    initPage();
}

// Re-run after HTMX swaps main content
if (window.htmx) {
    htmx.on('htmx:afterSwap', evt => {
        // Only react when the #bodytemplate container was replaced
        const target = evt.detail && evt.detail.target;
        if (target && target.id === 'bodytemplate') {
            initPage();
        }
    });
} 
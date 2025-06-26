// Sidebar state management
let hoverTimeout;

function initializeSidebar() {
    const sidebar = document.getElementById('sidebar');
    const mainContent = document.getElementById('main-content');
    const sidebarOverlay = document.getElementById('sidebar-overlay');
    const mobileMenuButton = document.getElementById('mobile-menu-button');
    const sidebarToggleButton = document.getElementById('sidebar-toggle-button');

    if (!sidebar || !mainContent) return;

    // Restore sidebar state from localStorage
    const isCollapsed = localStorage.getItem('sidebar-collapsed') === 'true';
    if (isCollapsed) {
        sidebar.classList.remove('sidebar-expanded');
        sidebar.classList.add('sidebar-collapsed');
        mainContent.classList.add('main-content-collapsed');
    }

    // Mobile menu toggle
    mobileMenuButton?.addEventListener('click', () => {
        sidebar.classList.toggle('-translate-x-full');
        sidebarOverlay?.classList.toggle('hidden');
    });

    // Desktop sidebar toggle
    sidebarToggleButton?.addEventListener('click', () => {
        sidebar.classList.toggle('sidebar-collapsed');
        sidebar.classList.toggle('sidebar-expanded');
        mainContent.classList.toggle('main-content-collapsed');
        
        // Save state to localStorage
        const isCollapsed = sidebar.classList.contains('sidebar-collapsed');
        localStorage.setItem('sidebar-collapsed', isCollapsed.toString());
    });

    // Sidebar hover functionality
    const handleMouseEnter = () => {
        clearTimeout(hoverTimeout);
        if (sidebar.classList.contains('sidebar-collapsed')) {
            sidebar.classList.add('sidebar-hover-expanded');
        }
    };

    const handleMouseLeave = () => {
        hoverTimeout = setTimeout(() => {
            sidebar.classList.remove('sidebar-hover-expanded');
        }, 300);
    };

    sidebar.addEventListener('mouseenter', handleMouseEnter);
    sidebar.addEventListener('mouseleave', handleMouseLeave);

    // Close sidebar when overlay is clicked
    sidebarOverlay?.addEventListener('click', () => {
        sidebar.classList.add('-translate-x-full');
        sidebarOverlay.classList.add('hidden');
    });
}

// User menu functionality
function initializeUserMenu() {
    const userMenuButton = document.getElementById('user-menu-button');
    const userMenu = document.getElementById('user-menu');

    if (!userMenuButton || !userMenu) return;

    const toggleMenu = (event) => {
        event.stopPropagation();
        userMenu.classList.toggle('hidden');
    };

    userMenuButton.addEventListener('click', toggleMenu);

    // Close user menu when clicking outside
    const handleClickOutside = (event) => {
        if (!userMenuButton.contains(event.target) && !userMenu.contains(event.target)) {
            userMenu.classList.add('hidden');
        }
    };

    document.addEventListener('click', handleClickOutside);
}

// Modal functionality
function initializeModals() {
    // These need to be global as they're called from HTML
    window.openModal = (modalId) => {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.remove('hidden');
        }
    };

    window.closeModal = (modalId) => {
        const modal = document.getElementById(modalId);
        if (modal) {
            modal.classList.add('hidden');
        }
    };
}

// Sidebar active state management
function initializeSidebarActiveState() {
    const ACTIVE_CLASSES = ['bg-primary-50','border-r-2','border-primary-500','text-primary-700'];
    const INACTIVE_TEXT_CLASSES = ['text-gray-600','hover:text-gray-900'];

    function setActive(link) {
        const sidebarLinks = document.querySelectorAll('#sidebar a[href^="/backoffice"]');
        sidebarLinks.forEach(a => {
            ACTIVE_CLASSES.forEach(c => a.classList.remove(c));
            INACTIVE_TEXT_CLASSES.forEach(c => {
                if(!a.classList.contains(c)) a.classList.add(c);
            });
            const icon = a.querySelector('svg, i');
            if(icon) {
                icon.classList.remove('text-primary-500');
                icon.classList.add('text-gray-400','group-hover:text-gray-500');
            }
        });

        if(link) {
            ACTIVE_CLASSES.forEach(c => link.classList.add(c));
            INACTIVE_TEXT_CLASSES.forEach(c => link.classList.remove(c));
            const icon = link.querySelector('svg, i');
            if(icon) {
                icon.classList.remove('text-gray-400','group-hover:text-gray-500');
                icon.classList.add('text-primary-500');
            }
        }
    }

    function updateByPath() {
        const path = window.location.pathname;
        // Match the base route for dynamic routes
        const baseRoute = path.split('/').slice(0, 3).join('/');
        const link = document.querySelector(`#sidebar a[href="${baseRoute}"]`);
        setActive(link);
    }

    updateByPath();

    // Handle sidebar link clicks
    const sidebar = document.getElementById('sidebar');
    if (sidebar) {
        const handleClick = (e) => {
            const link = e.target.closest('a[href^="/backoffice"]');
            if(link) setActive(link);
        };
        sidebar.addEventListener('click', handleClick);
    }
}

// Initialize Feather icons
function initializeFeatherIcons() {
    if (window.feather) {
        window.feather.replace();
    }
}

// Initialize all layout components
function initializeLayout() {
    initializeSidebar();
    initializeUserMenu();
    initializeModals();
    initializeSidebarActiveState();
    initializeFeatherIcons();
}

// Export for module usage
export { initializeLayout, initializeFeatherIcons };

// Expose globally for HTMX
window.initializeLayout = initializeLayout; 
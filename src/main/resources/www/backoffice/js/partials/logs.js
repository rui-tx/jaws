export default function initLogs() {
    const bound = document.body.dataset.logsBound;
    if (bound) return;
    document.body.dataset.logsBound = 'true';

    const filterButton = document.getElementById('filter-button');
    const levelFilter = document.getElementById('level-filter');

    if (!filterButton || !levelFilter) return;

    const logsTableBody = document.getElementById('logs-table-body');

    const runFilter = () => {
        const level = levelFilter.value;
        const baseUrl = '/htmx/backoffice/logs?page=0&size=25&sort=timestamp&direction=DESC';
        const url = level ? `${baseUrl}&level=${encodeURIComponent(level)}` : baseUrl;
        if (window.htmx) {
            htmx.ajax('GET', url, {
                target: '#logs-table-body',
                headers: {
                    'Authorization': 'Bearer ' + (localStorage.getItem('auth_token') || '')
                }
            });
        }
    };

    filterButton.addEventListener('click', runFilter);
    levelFilter.addEventListener('keypress', e => {
        if (e.key === 'Enter') runFilter();
    });
} 
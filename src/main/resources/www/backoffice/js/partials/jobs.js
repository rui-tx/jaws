export default function initJobs() {
    const bound = document.body.dataset.jobsBound;
    if (bound) return;
    document.body.dataset.jobsBound = 'true';

    const filterButton = document.getElementById('filter-button');
    const statusFilter = document.getElementById('status-filter');

    if (!filterButton || !statusFilter) return;

    const runFilter = () => {
        const status = statusFilter.value;
        const baseUrl = '/htmx/backoffice/jobs?page=0&size=10&sort=created_at&direction=DESC';
        const url = status ? `${baseUrl}&status=${encodeURIComponent(status)}` : baseUrl;
        if (window.htmx) {
            htmx.ajax('GET', url, {
                target: '#jobs-table-body',
                headers: {
                    'Authorization': 'Bearer ' + (localStorage.getItem('auth_token') || '')
                }
            });
        }
    };

    filterButton.addEventListener('click', runFilter);
    statusFilter.addEventListener('keypress', e => {
        if (e.key === 'Enter') runFilter();
    });
} 
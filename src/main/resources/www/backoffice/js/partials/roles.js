export default function initRoles() {
    if (document.body.dataset.rolesBound) return;
    document.body.dataset.rolesBound = 'true';

    // ---- Create Role Modal ----
    const createRoleForm = document.getElementById('create-role-form');
    const createRoleMessage = document.getElementById('create-role-message');
    const createRoleSpinner = document.getElementById('create-role-spinner');

    if (createRoleForm && !createRoleForm.dataset.bound) {
        createRoleForm.dataset.bound = '1';
        createRoleForm.addEventListener('submit', async evt => {
            evt.preventDefault();
            createRoleSpinner && createRoleSpinner.classList.remove('hidden');
            const fd = new FormData(createRoleForm);
            const data = { name: fd.get('name'), description: fd.get('description') };
            try {
                const res = await fetch('/backoffice/roles', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': localStorage.getItem('auth_token') ? 'Bearer ' + localStorage.getItem('auth_token') : ''
                    },
                    body: JSON.stringify(data)
                });
                createRoleSpinner && createRoleSpinner.classList.add('hidden');
                if (res.ok) {
                    if (typeof closeModal === 'function') closeModal('create-role-modal');
                    createRoleForm.reset();
                    if (window.htmx) htmx.trigger('#roles-table-body', 'refresh');
                    if (createRoleMessage) {
                        createRoleMessage.innerHTML = '<div class="bg-green-50 border border-green-200 rounded-md p-3"><div class="text-sm text-green-800">Role created successfully!</div></div>';
                        createRoleMessage.classList.remove('hidden');
                        setTimeout(()=>createRoleMessage.classList.add('hidden'),3000);
                    }
                } else {
                    const text = await res.text();
                    if (createRoleMessage) {
                        createRoleMessage.innerHTML = `<div class="bg-red-50 border border-red-200 rounded-md p-3"><div class="text-sm text-red-800">${text||'Error creating role'}</div></div>`;
                        createRoleMessage.classList.remove('hidden');
                    }
                }
            } catch(err) {
                console.error(err);
                createRoleSpinner && createRoleSpinner.classList.add('hidden');
                if (createRoleMessage) {
                    createRoleMessage.innerHTML = '<div class="bg-red-50 border border-red-200 rounded-md p-3"><div class="text-sm text-red-800">Network error. Please try again.</div></div>';
                    createRoleMessage.classList.remove('hidden');
                }
            }
        });
    }

    // ---- Assign Role Modal ----
    const assignRoleForm = document.getElementById('assign-role-form');
    const assignRoleMessage = document.getElementById('assign-role-message');
    const assignRoleSpinner = document.getElementById('assign-role-spinner');

    function loadAssignRoleDropdowns() {
        // Load users
        fetch('/htmx/backoffice/users-list', {
            headers: { 'Authorization': localStorage.getItem('auth_token') ? 'Bearer ' + localStorage.getItem('auth_token') : '' }
        }).then(r=>r.text()).then(html=>{
            const sel=document.getElementById('assign-user-select'); if(sel) sel.innerHTML = html;
        }).catch(err=>{
            console.error(err);
            const sel=document.getElementById('assign-user-select'); if(sel) sel.innerHTML='<option>Error loading users</option>';
        });
        // Load roles
        fetch('/htmx/backoffice/roles-list', {
            headers: { 'Authorization': localStorage.getItem('auth_token') ? 'Bearer ' + localStorage.getItem('auth_token') : '' }
        }).then(r=>r.text()).then(html=>{
            const sel=document.getElementById('assign-role-select'); if(sel) sel.innerHTML = html;
        }).catch(err=>{
            console.error(err);
            const sel=document.getElementById('assign-role-select'); if(sel) sel.innerHTML='<option>Error loading roles</option>';
        });
    }

    // patch openModal to load dropdowns when assign-role-modal opens
    const originalOpenModal = window.openModal;
    window.openModal = function(modalId) {
        originalOpenModal(modalId);
        if (modalId === 'assign-role-modal') loadAssignRoleDropdowns();
    };

    if (assignRoleForm && !assignRoleForm.dataset.bound) {
        assignRoleForm.dataset.bound = '1';
        assignRoleForm.addEventListener('submit', async evt => {
            evt.preventDefault();
            assignRoleSpinner && assignRoleSpinner.classList.remove('hidden');
            const fd = new FormData(assignRoleForm);
            const data = { userId: parseInt(fd.get('userId')), roleId: parseInt(fd.get('roleId')) };
            try {
                const res = await fetch('/backoffice/assign-role', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        'Authorization': localStorage.getItem('auth_token') ? 'Bearer ' + localStorage.getItem('auth_token') : ''
                    },
                    body: JSON.stringify(data)
                });
                assignRoleSpinner && assignRoleSpinner.classList.add('hidden');
                if (res.ok) {
                    if (typeof closeModal === 'function') closeModal('assign-role-modal');
                    assignRoleForm.reset();
                    if (window.htmx) htmx.trigger('#user-roles-table-body', 'refresh');
                    if (assignRoleMessage) {
                        assignRoleMessage.innerHTML = '<div class="bg-green-50 border border-green-200 rounded-md p-3"><div class="text-sm text-green-800">Role assigned successfully!</div></div>';
                        assignRoleMessage.classList.remove('hidden');
                        setTimeout(()=>assignRoleMessage.classList.add('hidden'),3000);
                    }
                } else {
                    const text = await res.text();
                    if (assignRoleMessage) {
                        assignRoleMessage.innerHTML = `<div class="bg-red-50 border border-red-200 rounded-md p-3"><div class="text-sm text-red-800">${text||'Error assigning role'}</div></div>`;
                        assignRoleMessage.classList.remove('hidden');
                    }
                }
            } catch(err) {
                console.error(err);
                assignRoleSpinner && assignRoleSpinner.classList.add('hidden');
                if (assignRoleMessage) {
                    assignRoleMessage.innerHTML = '<div class="bg-red-50 border border-red-200 rounded-md p-3"><div class="text-sm text-red-800">Network error. Please try again.</div></div>';
                    assignRoleMessage.classList.remove('hidden');
                }
            }
        });
    }
} 
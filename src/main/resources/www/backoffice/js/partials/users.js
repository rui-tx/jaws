export default function initUsers() {
    const form = document.getElementById('create-user-form');
    if (!form || form.dataset.bound) return; // either not on page or already initialised

    form.dataset.bound = 'true';

    const createUserMessage = document.getElementById('create-user-message');
    const createUserSpinner = document.getElementById('create-user-spinner');

    form.addEventListener('submit', async (event) => {
        event.preventDefault();

        if (createUserSpinner) createUserSpinner.classList.remove('hidden');

        const formData = new FormData(form);
        const data = {
            username: formData.get('username'),
            firstName: formData.get('firstName'),
            lastName: formData.get('lastName'),
            password: formData.get('password'),
            passwordConfirmation: formData.get('password_confirmation')
        };

        try {
            const response = await fetch('/backoffice/users', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Authorization': localStorage.getItem('auth_token') ? 'Bearer ' + localStorage.getItem('auth_token') : ''
                },
                body: JSON.stringify(data)
            });

            if (createUserSpinner) createUserSpinner.classList.add('hidden');

            if (response.ok) {
                // close modal & reset form
                if (typeof closeModal === 'function') closeModal('create-user-modal');
                form.reset();

                // refresh users table if a refresh trigger exists
                const refreshButton = document.querySelector('[hx-target="#users-table-body"]');
                if (refreshButton) refreshButton.click();

                if (createUserMessage) {
                    createUserMessage.innerHTML = '<div class="bg-green-50 border border-green-200 rounded-md p-3"><div class="text-sm text-green-800">User created successfully!</div></div>';
                    createUserMessage.classList.remove('hidden');
                    setTimeout(() => createUserMessage.classList.add('hidden'), 3000);
                }
            } else {
                const text = await response.text();
                if (createUserMessage) {
                    createUserMessage.innerHTML = '<div class="bg-red-50 border border-red-200 rounded-md p-3"><div class="text-sm text-red-800">' + (text || 'Error creating user') + '</div></div>';
                    createUserMessage.classList.remove('hidden');
                }
            }
        } catch (error) {
            console.error('Error:', error);
            if (createUserSpinner) createUserSpinner.classList.add('hidden');
            if (createUserMessage) {
                createUserMessage.innerHTML = '<div class="bg-red-50 border border-red-200 rounded-md p-3"><div class="text-sm text-red-800">Network error. Please try again.</div></div>';
                createUserMessage.classList.remove('hidden');
            }
        }
    });
} 
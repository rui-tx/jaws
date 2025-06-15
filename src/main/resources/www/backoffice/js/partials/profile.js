export default function initProfile() {
    if (document.body.dataset.profileBound) return;
    document.body.dataset.profileBound = 'true';

    const saveButton = document.getElementById('save-profile');
    const profileForm = document.getElementById('profile-form');
    const profileMessage = document.getElementById('profile-message');
    const profilePreview = document.getElementById('profile-preview');
    const profilePictureInput = document.querySelector('input[name="profilePicture"]');

    // preview
    if (profilePictureInput && profilePreview) {
        profilePictureInput.addEventListener('input', () => {
            const url = profilePictureInput.value.trim();
            profilePreview.src = url || 'https://openmoji.org/data/color/svg/1F9D9-200D-2642-FE0F.svg';
        });
    }

    if (!saveButton || !profileForm) return;

    saveButton.addEventListener('click', () => {
        const formData = new FormData(profileForm);
        const data = {
            email: formData.get('email'),
            firstName: formData.get('firstName'),
            lastName: formData.get('lastName'),
            password: formData.get('password'),
            phoneNumber: formData.get('phoneNumber'),
            birthdate: formData.get('birthdate'),
            gender: formData.get('gender'),
            location: formData.get('location'),
            website: formData.get('website'),
            bio: formData.get('bio'),
            profilePicture: formData.get('profilePicture')
        };
        if (!data.password || data.password.trim() === '') delete data.password;
        Object.keys(data).forEach(k => {
            if (data[k] === '' || (typeof data[k] === 'string' && data[k].trim() === '')) data[k] = null;
        });

        fetch(window.location.pathname, {
            method: 'PATCH',
            headers: {
                'Content-Type': 'application/json',
                'Authorization': localStorage.getItem('auth_token') ? 'Bearer ' + localStorage.getItem('auth_token') : ''
            },
            body: JSON.stringify(data)
        })
        .then(async res => {
            const text = await res.text();
            const ok = res.ok;
            const html = ok ? `<div class="bg-green-50 border border-green-200 rounded-md p-4"><div class="text-sm text-green-800">Profile updated successfully!</div></div>`
                             : `<div class="bg-red-50 border border-red-200 rounded-md p-4"><div class="text-sm text-red-800">${text || 'Error updating profile'}</div></div>`;
            profileMessage.innerHTML = html;
            profileMessage.classList.remove('hidden');
            setTimeout(() => profileMessage.classList.add('hidden'), 3000);
        })
        .catch(err => {
            console.error(err);
            profileMessage.innerHTML = '<div class="bg-red-50 border border-red-200 rounded-md p-4"><div class="text-sm text-red-800">Network error. Please try again.</div></div>';
            profileMessage.classList.remove('hidden');
        });
    });
} 
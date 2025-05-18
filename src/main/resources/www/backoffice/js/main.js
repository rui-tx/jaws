"use strict";

/* Aside: submenus toggle */
Array.from(document.getElementsByClassName('menu is-menu-main')).forEach(function (el) {
    Array.from(el.getElementsByClassName('has-dropdown-icon')).forEach(function (elA) {
        elA.addEventListener('click', function (e) {
            var dropdownIcon = e.currentTarget.getElementsByClassName('dropdown-icon')[0].getElementsByClassName('mdi')[0];
            e.currentTarget.parentNode.classList.toggle('is-active');
            dropdownIcon.classList.toggle('mdi-plus');
            dropdownIcon.classList.toggle('mdi-minus');
        });
    });
});

/* Aside Mobile toggle */
Array.from(document.getElementsByClassName('jb-aside-mobile-toggle')).forEach(function (el) {
    el.addEventListener('click', function (e) {
        var dropdownIcon = e.currentTarget.getElementsByClassName('icon')[0].getElementsByClassName('mdi')[0];
        document.documentElement.classList.toggle('has-aside-mobile-expanded');
        dropdownIcon.classList.toggle('mdi-forwardburger');
        dropdownIcon.classList.toggle('mdi-backburger');
    });
});

/* NavBar menu mobile toggle */
Array.from(document.getElementsByClassName('jb-navbar-menu-toggle')).forEach(function (el) {
    el.addEventListener('click', function (e) {
        var dropdownIcon = e.currentTarget.getElementsByClassName('icon')[0].getElementsByClassName('mdi')[0];
        document.getElementById(e.currentTarget.getAttribute('data-target')).classList.toggle('is-active');
        dropdownIcon.classList.toggle('mdi-dots-vertical');
        dropdownIcon.classList.toggle('mdi-close');
    });
});

/* Modal: open */
Array.from(document.getElementsByClassName('jb-modal')).forEach(function (el) {
    el.addEventListener('click', function (e) {
        e.preventDefault(); // Good practice to prevent default action if 'el' is an anchor
        var modalTargetId = e.currentTarget.getAttribute('data-target');
        var modalTarget = document.getElementById(modalTargetId);

        if (modalTarget) {
            modalTarget.classList.add('is-active');
            document.documentElement.classList.add('is-clipped'); // Prevent background scrolling
        }
    });
});

/* Modal: close */
Array.from(document.getElementsByClassName('jb-modal-close')).forEach(function (el) {
    el.addEventListener('click', function (e) {
        e.preventDefault(); // Good practice
        var modal = e.currentTarget.closest('.modal');

        if (modal) {
            modal.classList.remove('is-active');

            // --- MODIFICATION START ---
            // Specifically for the create-user-modal, reset form and clear messages
            if (modal.id === 'create-user-modal') {
                const form = modal.querySelector('#create-user-form');
                if (form) {
                    form.reset();
                }
                const messageDiv = modal.querySelector('#create-user-message');
                if (messageDiv) {
                    messageDiv.innerHTML = '';
                }
                // If you have a spinner specific to the form, hide it too
                const spinner = modal.querySelector('#create-user-spinner');
                if (spinner && spinner.classList) { // Check if spinner exists and has classList
                    // Assuming your htmx-indicator class hides it when not active or you manage display style
                    spinner.style.display = 'none'; // Or manage via a class
                }
            }
            // --- MODIFICATION END ---
        }

        // Check if any other modals are active before removing 'is-clipped'
        const activeModals = document.querySelectorAll('.modal.is-active').length;
        if (activeModals === 0) {
            document.documentElement.classList.remove('is-clipped');
        }
    });
});

/* Notification dismiss */
Array.from(document.getElementsByClassName('jb-notification-dismiss')).forEach(function (el) {
    el.addEventListener('click', function (e) {
        e.preventDefault();
        e.currentTarget.closest('.notification').classList.add('is-hidden');
    });
});

// Optional: Add Escape key to close modals (if not already handled)
// This was part of my previous suggestion and is generally good UX.
document.addEventListener('keydown', function (event) {
    if (event.key === "Escape" || event.key === "Esc") {
        const activeModals = document.querySelectorAll('.modal.is-active');
        activeModals.forEach(function (modal) {
            modal.classList.remove('is-active');

            // --- DUPLICATED MODIFICATION FOR ESCAPE KEY ---
            if (modal.id === 'create-user-modal') {
                const form = modal.querySelector('#create-user-form');
                if (form) {
                    form.reset();
                }
                const messageDiv = modal.querySelector('#create-user-message');
                if (messageDiv) {
                    messageDiv.innerHTML = '';
                }
                const spinner = modal.querySelector('#create-user-spinner');
                if (spinner && spinner.classList) {
                    spinner.style.display = 'none';
                }
            }
            // --- END DUPLICATED MODIFICATION ---
        });

        if (activeModals.length > 0) {
            document.documentElement.classList.remove('is-clipped');
        }
    }
});

// Optional: If you want clicks on .modal-background to also close the modal
// (and your .modal-background doesn't already have .jb-modal-close)
Array.from(document.getElementsByClassName('modal-background')).forEach(function (el) {
    el.addEventListener('click', function (e) {
        e.preventDefault();
        var modal = e.currentTarget.closest('.modal');
        if (modal) {
            modal.classList.remove('is-active');
            // --- DUPLICATED MODIFICATION FOR BACKGROUND CLICK ---
            if (modal.id === 'create-user-modal') {
                const form = modal.querySelector('#create-user-form');
                if (form) {
                    form.reset();
                }
                const messageDiv = modal.querySelector('#create-user-message');
                if (messageDiv) {
                    messageDiv.innerHTML = '';
                }
                const spinner = modal.querySelector('#create-user-spinner');
                if (spinner && spinner.classList) {
                    spinner.style.display = 'none';
                }
            }
            // --- END DUPLICATED MODIFICATION ---
        }
        const activeModals = document.querySelectorAll('.modal.is-active').length;
        if (activeModals === 0) {
            document.documentElement.classList.remove('is-clipped');
        }
    });
});
(() => {
  // Theme management
  try {
    const stored = localStorage.getItem('themeMode');
    const isDark = stored ? stored === 'dark'
                : matchMedia('(prefers-color-scheme: dark)').matches;
    if (isDark) {
      document.documentElement.classList.add('dark');
    }
        
    // Set initial icon state
    setTimeout(() => {
      const sunIcon = document.getElementById('sun-icon');
      const moonIcon = document.getElementById('moon-icon');
      if (sunIcon && moonIcon) {
        sunIcon.classList.toggle('hidden', isDark);
        moonIcon.classList.toggle('hidden', !isDark);
      }
    }, 0);
  } catch (_) {}

  const apply = dark => {
    document.documentElement.classList.toggle('dark', dark);
    try { localStorage.setItem('themeMode', dark ? 'dark' : 'light'); } catch (_) {}
    return dark;
  };

  document.addEventListener('basecoat:theme', (event) => {
    const mode = event.detail?.mode;
    const isDark = apply(mode === 'dark' ? true
          : mode === 'light' ? false
          : !document.documentElement.classList.contains('dark'));
    
    // Update theme toggle icons
    const sunIcon = document.getElementById('sun-icon');
    const moonIcon = document.getElementById('moon-icon');
    if (sunIcon && moonIcon) {
      sunIcon.classList.toggle('hidden', isDark);
      moonIcon.classList.toggle('hidden', !isDark);
    }
  });
})(); 
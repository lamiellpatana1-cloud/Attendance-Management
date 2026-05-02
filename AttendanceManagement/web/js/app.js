const API = {
  token: () => localStorage.getItem('token'),
  headers: () => ({
    'Content-Type': 'application/json',
    'Authorization': 'Bearer ' + API.token()
  }),
  timeoutMs: 15000,

  async fetchWithTimeout(url, options = {}) {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), API.timeoutMs);

    try {
      return await fetch(url, { ...options, signal: controller.signal });
    } catch (error) {
      if (error.name === 'AbortError') {
        throw new Error('Server is taking too long to respond. Please try again.');
      }
      throw error;
    } finally {
      clearTimeout(timeout);
    }
  },

  async parseResponse(response, options = {}) {
    let data = null;
    try {
      data = await response.json();
    } catch (error) {
      data = null;
    }

    if (!options.skipAuthRedirect && (response.status === 401 || (data && data.error === 'Unauthorized'))) {
      API.forceRelogin();
      return { error: 'Unauthorized' };
    }

    return data;
  },

  forceRelogin() {
    localStorage.removeItem('token');
    localStorage.removeItem('uid');
    localStorage.removeItem('profile_id');
    localStorage.removeItem('student_number');
    localStorage.removeItem('name');
    localStorage.removeItem('role');
    localStorage.removeItem('profile_picture');
    if (!window.location.pathname.endsWith('index.html')) {
      window.location.href = 'index.html?session=expired';
    }
  },

  async get(url) {
    try {
      const response = await API.fetchWithTimeout(url, { headers: API.headers() });
      return await API.parseResponse(response);
    } catch (error) {
      console.error(error);
      return null;
    }
  },

  async post(url, body, options = {}) {
    try {
      const response = await API.fetchWithTimeout(url, {
        method: 'POST',
        headers: API.headers(),
        body: JSON.stringify(body)
      });
      return await API.parseResponse(response, options);
    } catch (error) {
      return { error: error.message };
    }
  },

  async put(url, body) {
    try {
      const response = await API.fetchWithTimeout(url, {
        method: 'PUT',
        headers: API.headers(),
        body: JSON.stringify(body)
      });
      return await API.parseResponse(response);
    } catch (error) {
      return { error: error.message };
    }
  },

  async delete(url) {
    try {
      const response = await API.fetchWithTimeout(url, {
        method: 'DELETE',
        headers: API.headers()
      });
      return await API.parseResponse(response);
    } catch (error) {
      return { error: error.message };
    }
  }
};

const THEME_STORAGE_KEY = 'attendease-theme';
const BRAND_LOGO_ICON_PATH = 'img/attendease-logo-icon.png';
const BRAND_LOGO_FULL_PATH = 'img/attendease-logo-full-neon.png';
const BRAND_LOGO_ICON_MARKUP = `
  <span class="brand-mark" aria-hidden="true">
    <img class="brand-mark-image brand-mark-image-icon" src="${BRAND_LOGO_ICON_PATH}" alt="">
  </span>
`;
const BRAND_LOGO_FULL_MARKUP = `
  <span class="brand-mark brand-mark-full brand-mark-composite" aria-hidden="true">
    <img class="brand-mark-image brand-mark-image-auth" src="${BRAND_LOGO_ICON_PATH}" alt="">
    <span class="brand-wordmark">
      <span class="brand-wordmark-attend">Attend</span><span class="brand-wordmark-ease">Ease</span>
    </span>
  </span>
`;
const PASSWORD_TOGGLE_ICON_MARKUP = `
  <svg viewBox="0 0 24 24" class="toggle-pass-eye" aria-hidden="true">
    <path d="M2.2 12s3.5-6 9.8-6 9.8 6 9.8 6-3.5 6-9.8 6-9.8-6-9.8-6Z"></path>
    <circle cx="12" cy="12" r="3.2"></circle>
  </svg>
  <svg viewBox="0 0 24 24" class="toggle-pass-eye-off hidden" aria-hidden="true">
    <path d="M3 3l18 18"></path>
    <path d="M10.7 6.2A10.9 10.9 0 0 1 12 6c6.3 0 9.8 6 9.8 6a17.6 17.6 0 0 1-3.2 3.9"></path>
    <path d="M6.6 6.7C4.2 8.3 2.2 12 2.2 12s3.5 6 9.8 6c1.9 0 3.5-.5 4.9-1.2"></path>
    <path d="M9.9 9.9A3.2 3.2 0 0 0 12 15.2c.8 0 1.6-.3 2.1-.9"></path>
  </svg>
`;

function initPasswordToggleButtons() {
  document.querySelectorAll('.toggle-pass').forEach(button => {
    if (button.dataset.iconReady === 'true') return;
    button.innerHTML = PASSWORD_TOGGLE_ICON_MARKUP;
    button.setAttribute('aria-label', 'Show password');
    button.setAttribute('aria-pressed', 'false');
    button.dataset.iconReady = 'true';
  });
}

if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', initPasswordToggleButtons);
} else {
  initPasswordToggleButtons();
}

const NAV_ICON_MARKUP = {
  dashboard: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <rect x="4" y="4" width="6" height="6" rx="1.5"></rect>
      <rect x="14" y="4" width="6" height="6" rx="1.5"></rect>
      <rect x="4" y="14" width="6" height="6" rx="1.5"></rect>
      <rect x="14" y="14" width="6" height="6" rx="1.5"></rect>
    </svg>
  `,
  profile: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <circle cx="12" cy="8" r="3.2"></circle>
      <path d="M5 19c1.7-3.2 4.2-4.8 7-4.8s5.3 1.6 7 4.8"></path>
    </svg>
  `,
  students: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <circle cx="9" cy="8.5" r="2.5"></circle>
      <circle cx="16.5" cy="9.5" r="2.1"></circle>
      <path d="M4.5 18c1.2-2.8 3.2-4.2 5.7-4.2S14.7 15.2 16 18"></path>
      <path d="M13.5 17.5c.7-1.9 2-3 3.9-3 1.3 0 2.5.6 3.4 2"></path>
    </svg>
  `,
  teachers: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <path d="M4 6.5h16v9H4z"></path>
      <path d="M9 19h6"></path>
      <path d="M8 11.5h8"></path>
      <path d="M12 6.5v-2"></path>
    </svg>
  `,
  subjects: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <path d="M6 5.5h11a2 2 0 0 1 2 2v10.5H8a2 2 0 0 0-2 2"></path>
      <path d="M6 5.5a2 2 0 0 0-2 2v11"></path>
      <path d="M9.2 10h6M9.2 13h5M9.2 16h4"></path>
    </svg>
  `,
  attendance: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <circle cx="12" cy="12" r="8"></circle>
      <path d="M8.5 12.3 11 14.8l4.8-5.1"></path>
    </svg>
  `,
  marks: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <path d="M5 19.5h14"></path>
      <path d="M8 16v-4"></path>
      <path d="M12 16V8"></path>
      <path d="M16 16v-6"></path>
    </svg>
  `,
  schedule: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <rect x="4" y="5.5" width="16" height="14" rx="2"></rect>
      <path d="M8 3.8v3.4M16 3.8v3.4M4 9.5h16"></path>
      <path d="M8 13h3"></path>
    </svg>
  `,
  reports: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <path d="M6 4.5h8l4 4v11H6z"></path>
      <path d="M14 4.5v4h4"></path>
      <path d="M9 15.5h6M9 12.5h4"></path>
    </svg>
  `,
  logout: `
    <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
      <path d="M10 5H6.8A1.8 1.8 0 0 0 5 6.8v10.4A1.8 1.8 0 0 0 6.8 19H10"></path>
      <path d="M13 8.2 17 12l-4 3.8"></path>
      <path d="M9.5 12H17"></path>
    </svg>
  `
};

if (document.body) {
  const bootTheme = localStorage.getItem(THEME_STORAGE_KEY) || 'dark';
  document.body.dataset.theme = bootTheme;
  document.documentElement.dataset.theme = bootTheme;
}

function requireAuth() {
  if (!localStorage.getItem('token')) {
    window.location.href = 'index.html';
    return;
  }

  const name = localStorage.getItem('name') || 'User';
  const role = localStorage.getItem('role') || '';
  const picture = localStorage.getItem('profile_picture') || '';
  const userName = document.getElementById('userName');
  const userRole = document.getElementById('userRole');
  const userAvatar = document.getElementById('userAvatar');

  if (userName) userName.textContent = name;
  if (userRole) userRole.textContent = role.charAt(0).toUpperCase() + role.slice(1);

  if (userAvatar) {
    if (picture) {
      userAvatar.style.backgroundImage = `url(${picture})`;
      userAvatar.style.backgroundSize = 'cover';
      userAvatar.style.backgroundPosition = 'center';
      userAvatar.textContent = '';
    } else {
      userAvatar.textContent = name.charAt(0).toUpperCase();
    }
  }
}

function renderBrandLogos() {
  document.querySelectorAll('.logo-icon').forEach(icon => {
    const sidebar = icon.closest('.sidebar');
    const inSidebarHeader = !!icon.closest('.sidebar-header');
    const variant = inSidebarHeader && sidebar
      ? 'icon'
      : (icon.dataset.brandVariant === 'full' ? 'full' : 'icon');
    const desiredMarkup = variant === 'full' ? BRAND_LOGO_FULL_MARKUP : BRAND_LOGO_ICON_MARKUP;

    if (icon.dataset.brandReady === 'true' && icon.dataset.brandVariant === variant) return;
    icon.dataset.brandReady = 'true';
    icon.dataset.brandVariant = variant;
    icon.innerHTML = desiredMarkup;
    icon.setAttribute('role', 'img');
    icon.setAttribute('aria-label', 'AttendEase logo');
  });
}

async function logout() {
  const confirmed = await showConfirmModal({
    title: 'Logout',
    message: 'Do you want to logout?',
    confirmText: 'Yes',
    cancelText: 'No'
  });
  if (!confirmed) return;
  await API.post('/api/logout', {});
  localStorage.clear();
  window.location.href = 'index.html';
}

function currentProfileId() {
  return localStorage.getItem('profile_id') || localStorage.getItem('uid');
}

function requireRole(...roles) {
  requireAuth();
  const role = localStorage.getItem('role');
  if (!roles.includes(role)) window.location.href = 'dashboard.html';
}

const NAV_ITEMS = {
  admin: [
    { icon: 'dashboard', label: 'Dashboard', href: 'dashboard.html' },
    { icon: 'students', label: 'Students', href: 'students.html' },
    { icon: 'teachers', label: 'Teachers', href: 'teachers.html' },
    { icon: 'subjects', label: 'Subjects', href: 'subjects.html' },
    { icon: 'attendance', label: 'Attendance', href: 'attendance.html' },
    { icon: 'marks', label: 'Marks', href: 'marks.html' },
    { icon: 'schedule', label: 'Schedules', href: 'timetable.html' },
    { icon: 'reports', label: 'Reports', href: 'reports.html' }
  ],
  teacher: [
    { icon: 'dashboard', label: 'Dashboard', href: 'dashboard.html' },
    { icon: 'profile', label: 'My Profile', href: 'profile.html' },
    { icon: 'students', label: 'Students', href: 'students.html' },
    { icon: 'subjects', label: 'Subjects', href: 'subjects.html' },
    { icon: 'attendance', label: 'Attendance', href: 'attendance.html' },
    { icon: 'marks', label: 'Marks', href: 'marks.html' },
    { icon: 'schedule', label: 'Schedules', href: 'timetable.html' },
    { icon: 'reports', label: 'Reports', href: 'reports.html' }
  ],
  student: [
    { icon: 'dashboard', label: 'Dashboard', href: 'dashboard.html' },
    { icon: 'profile', label: 'My Profile', href: 'profile.html' },
    { icon: 'attendance', label: 'My Attendance', href: 'attendance.html' },
    { icon: 'marks', label: 'My Marks', href: 'marks.html' },
    { icon: 'schedule', label: 'My Schedule', href: 'timetable.html' }
  ]
};

function getNavIcon(icon) {
  return NAV_ICON_MARKUP[icon] || NAV_ICON_MARKUP.dashboard;
}

function getMobilePrimaryNavItems(items, role) {
  const priority = {
    admin: ['dashboard.html', 'students.html', 'attendance.html', 'reports.html'],
    teacher: ['dashboard.html', 'attendance.html', 'marks.html', 'reports.html'],
    student: ['dashboard.html', 'attendance.html', 'marks.html', 'timetable.html', 'profile.html']
  };
  const preferred = priority[role] || priority.student;
  const selected = preferred
    .map(href => items.find(item => item.href === href))
    .filter(Boolean);

  items.forEach(item => {
    if (selected.length < 4 && !selected.some(entry => entry.href === item.href)) selected.push(item);
  });

  return selected.slice(0, role === 'student' ? 5 : 4);
}

function ensureMobileNavShell() {
  if (document.getElementById('mobileBottomNav')) return;

  const shell = document.createElement('div');
  shell.className = 'mobile-nav-shell';
  shell.innerHTML = `
    <div class="mobile-menu-backdrop hidden" id="mobileMenuBackdrop" onclick="closeMobileMenu()"></div>
    <nav class="mobile-bottom-nav" id="mobileBottomNav" aria-label="Primary mobile navigation"></nav>
    <section class="mobile-menu-sheet hidden" id="mobileMenuSheet" aria-label="Mobile menu">
      <div class="mobile-menu-handle" aria-hidden="true"></div>
      <div class="mobile-menu-header">
        <div class="mobile-menu-user">
          <div class="user-avatar mobile-user-avatar" id="mobileUserAvatar">A</div>
          <div>
            <div class="mobile-user-name" id="mobileUserName">User</div>
            <div class="mobile-user-role" id="mobileUserRole">-</div>
          </div>
        </div>
        <button type="button" class="mobile-menu-close" onclick="closeMobileMenu()" aria-label="Close menu">
          <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false" aria-hidden="true">
            <path d="M6 6l12 12"></path>
            <path d="M18 6 6 18"></path>
          </svg>
        </button>
      </div>
      <div class="mobile-menu-grid" id="mobileMenuGrid"></div>
      <div class="mobile-menu-actions" id="mobileMenuActions"></div>
    </section>
  `;
  document.body.appendChild(shell);
}

function updateMobileUserSummary() {
  const name = localStorage.getItem('name') || 'User';
  const role = localStorage.getItem('role') || '';
  const picture = localStorage.getItem('profile_picture') || '';
  const userName = document.getElementById('mobileUserName');
  const userRole = document.getElementById('mobileUserRole');
  const userAvatar = document.getElementById('mobileUserAvatar');

  if (userName) userName.textContent = name;
  if (userRole) userRole.textContent = role ? role.charAt(0).toUpperCase() + role.slice(1) : '-';
  if (!userAvatar) return;

  if (picture) {
    userAvatar.style.backgroundImage = `url(${picture})`;
    userAvatar.style.backgroundSize = 'cover';
    userAvatar.style.backgroundPosition = 'center';
    userAvatar.textContent = '';
  } else {
    userAvatar.style.backgroundImage = '';
    userAvatar.textContent = name.charAt(0).toUpperCase();
  }
}

function openMobileMenu() {
  const sheet = document.getElementById('mobileMenuSheet');
  const backdrop = document.getElementById('mobileMenuBackdrop');
  if (!sheet || !backdrop) return;

  updateMobileUserSummary();
  sheet.classList.remove('hidden');
  backdrop.classList.remove('hidden');
  requestAnimationFrame(() => document.body.classList.add('mobile-menu-open'));
  document.querySelectorAll('[data-mobile-menu-toggle="true"]').forEach(button => {
    button.setAttribute('aria-expanded', 'true');
  });
}

function closeMobileMenu() {
  document.body.classList.remove('mobile-menu-open');
  document.querySelectorAll('[data-mobile-menu-toggle="true"]').forEach(button => {
    button.setAttribute('aria-expanded', 'false');
  });
  setTimeout(() => {
    if (document.body.classList.contains('mobile-menu-open')) return;
    document.getElementById('mobileMenuSheet')?.classList.add('hidden');
    document.getElementById('mobileMenuBackdrop')?.classList.add('hidden');
  }, 180);
}

function initMobileTopBar() {
  const topBar = document.querySelector('.top-bar');
  const button = topBar?.querySelector('button');
  if (!topBar || !button || button.dataset.mobileMenuToggle === 'true') return;

  button.dataset.mobileMenuToggle = 'true';
  button.setAttribute('aria-label', 'Open mobile menu');
  button.setAttribute('aria-expanded', 'false');
}

function renderMobileNavigation(items, role, current) {
  ensureMobileNavShell();
  updateMobileUserSummary();
  initMobileTopBar();

  const primaryItems = getMobilePrimaryNavItems(items, role);
  const moreItems = items.filter(item => !primaryItems.some(primary => primary.href === item.href));
  const moreIsActive = moreItems.some(item => item.href === current);
  const bottomNav = document.getElementById('mobileBottomNav');
  const menuGrid = document.getElementById('mobileMenuGrid');
  const menuActions = document.getElementById('mobileMenuActions');
  if (!bottomNav || !menuGrid || !menuActions) return;

  const bottomLinks = primaryItems.map(item => `
    <a href="${item.href}" class="mobile-nav-item ${current === item.href ? 'active' : ''}" aria-label="${item.label}">
      <span class="mobile-nav-icon" aria-hidden="true">${getNavIcon(item.icon)}</span>
      <span>${item.label.replace(/^My\s+/i, '')}</span>
    </a>
  `).join('');

  bottomNav.innerHTML = bottomLinks + (moreItems.length ? `
    <button type="button" class="mobile-nav-item mobile-more-btn ${moreIsActive ? 'active' : ''}" data-mobile-menu-toggle="true" aria-label="Open more menu" aria-expanded="false" onclick="openMobileMenu()">
      <span class="mobile-nav-icon" aria-hidden="true">
        <svg viewBox="0 0 24 24" class="nav-icon-svg" focusable="false">
          <circle cx="5" cy="12" r="1.8"></circle>
          <circle cx="12" cy="12" r="1.8"></circle>
          <circle cx="19" cy="12" r="1.8"></circle>
        </svg>
      </span>
      <span>More</span>
    </button>
  ` : '');

  menuGrid.innerHTML = items.map(item => `
    <a href="${item.href}" class="mobile-menu-link ${current === item.href ? 'active' : ''}">
      <span class="mobile-menu-icon" aria-hidden="true">${getNavIcon(item.icon)}</span>
      <span>${item.label}</span>
    </a>
  `).join('');

  menuActions.innerHTML = `
    <button type="button" class="mobile-menu-action" onclick="toggleTheme()">
      <span class="theme-toggle-dot"></span>
      <span>Switch Theme</span>
    </button>
    <button type="button" class="mobile-menu-action danger" onclick="logout()">
      <span class="mobile-menu-icon" aria-hidden="true">${getNavIcon('logout')}</span>
      <span>Logout</span>
    </button>
  `;
}

function renderNav() {
  if (!localStorage.getItem('token')) {
    window.location.href = 'index.html';
    return;
  }

  const role = localStorage.getItem('role') || 'student';
  const items = NAV_ITEMS[role] || NAV_ITEMS.student;
  const current = window.location.pathname.split('/').pop();
  const nav = document.getElementById('sidebarNav');
  if (!nav) return;

  nav.innerHTML = items.map((item, index) => `
    <a href="${item.href}" class="nav-item ${current === item.href ? 'active' : ''}" style="--nav-delay:${(index + 1) * 0.05}s">
      <span class="nav-icon" aria-hidden="true">${getNavIcon(item.icon)}</span>
      <span class="nav-label">${item.label}</span>
    </a>
  `).join('') + `
    <a href="#" class="nav-item nav-logout" style="--nav-delay:${(items.length + 1) * 0.05}s" onclick="logout();return false;">
      <span class="nav-icon" aria-hidden="true">${getNavIcon('logout')}</span>
      <span class="nav-label">Logout</span>
    </a>
  `;

  initNavigationEffects();
  renderMobileNavigation(items, role, current);
}

function initNavigationEffects() {
  const nav = document.getElementById('sidebarNav');
  if (!nav || nav.dataset.bound === 'true') return;

  nav.dataset.bound = 'true';
  nav.addEventListener('click', event => {
    const link = event.target.closest('a.nav-item[href]');
    if (!link || link.classList.contains('nav-logout')) return;

    const href = link.getAttribute('href');
    const current = window.location.pathname.split('/').pop();
    if (!href || href === '#' || href === current) return;

    event.preventDefault();
    if (document.body.classList.contains('page-leaving')) return;

    link.classList.add('is-arming');
    document.body.classList.add('page-leaving');
    setTimeout(() => {
      window.location.href = href;
    }, 220);
  });
}

function initPageMotion() {
  if (document.body.dataset.motionReady === 'true') return;
  document.body.dataset.motionReady = 'true';
  document.body.classList.add('page-shell');
  requestAnimationFrame(() => document.body.classList.add('page-entered'));
}

function toggleSidebar() {
  if (window.matchMedia('(max-width: 768px)').matches) {
    openMobileMenu();
    return;
  }

  const sidebar = document.getElementById('sidebar');
  if (sidebar) {
    sidebar.classList.toggle('collapsed');
    renderBrandLogos();
  }
}

function syncMobileTableLabels(root = document) {
  root.querySelectorAll('table.data-table').forEach(table => {
    const labels = Array.from(table.querySelectorAll('thead th')).map(th => th.textContent.trim());
    if (!labels.length) return;

    table.querySelectorAll('tbody tr').forEach(row => {
      Array.from(row.children).forEach((cell, index) => {
        if (cell.hasAttribute('colspan')) {
          cell.removeAttribute('data-label');
          return;
        }
        if (labels[index]) cell.setAttribute('data-label', labels[index]);
      });
    });
  });
}

function initMobileEnhancements() {
  document.body.classList.toggle('has-mobile-shell', !!document.querySelector('.app-layout'));
  initMobileTopBar();
  syncMobileTableLabels();

  const tableObserver = new MutationObserver(mutations => {
    const shouldSync = mutations.some(mutation => {
      const target = mutation.target;
      return target instanceof Element && (target.closest?.('table.data-table') || target.matches?.('table.data-table'));
    });
    if (shouldSync) syncMobileTableLabels();
  });
  tableObserver.observe(document.body, { childList: true, subtree: true });

  window.addEventListener('keydown', event => {
    if (event.key === 'Escape') closeMobileMenu();
  });

  window.addEventListener('resize', () => {
    if (!window.matchMedia('(max-width: 768px)').matches) closeMobileMenu();
  });
}

function getTheme() {
  return document.body?.dataset.theme === 'light' ? 'light' : 'dark';
}

function updateThemeToggleButton() {
  const button = document.getElementById('themeToggleBtn');
  if (!button) return;

  const theme = getTheme();
  const nextTheme = theme === 'dark' ? 'light' : 'dark';
  const nextLabel = nextTheme === 'light' ? 'Light Mode' : 'Dark Mode';

  button.dataset.theme = theme;
  button.setAttribute('aria-label', `Switch to ${nextLabel}`);
  button.innerHTML = `
    <span class="theme-toggle-dot"></span>
    <span class="theme-toggle-label">${nextLabel}</span>
  `;
}

function refreshThemeSensitiveViews() {
  if (typeof loadDashboard === 'function' && document.getElementById('trendChart')) {
    loadDashboard();
    return;
  }

  if (typeof reloadCurrentTab === 'function' && document.getElementById('reportTitle')) {
    reloadCurrentTab();
  }
}

function applyTheme(theme, options = {}) {
  const normalized = theme === 'light' ? 'light' : 'dark';
  const settings = {
    persist: true,
    refreshViews: true,
    announce: false,
    ...options
  };

  document.body.dataset.theme = normalized;
  document.documentElement.dataset.theme = normalized;

  if (settings.persist) localStorage.setItem(THEME_STORAGE_KEY, normalized);
  if (typeof syncChartTheme === 'function') syncChartTheme();
  updateThemeToggleButton();
  document.dispatchEvent(new CustomEvent('themechange', { detail: { theme: normalized } }));

  if (settings.refreshViews) {
    requestAnimationFrame(() => refreshThemeSensitiveViews());
  }

  if (settings.announce) {
    showToast(`${normalized === 'light' ? 'Light' : 'Dark'} mode enabled`, 'info');
  }
}

function toggleTheme() {
  applyTheme(getTheme() === 'dark' ? 'light' : 'dark', { announce: true });
}

function ensureThemeToggle() {
  if (document.getElementById('themeToggleBtn')) {
    updateThemeToggleButton();
    return;
  }

  const button = document.createElement('button');
  button.type = 'button';
  button.id = 'themeToggleBtn';
  button.className = 'btn btn-secondary btn-sm theme-toggle-btn';
  button.addEventListener('click', toggleTheme);

  const topBarRight = document.querySelector('.top-bar-right');
  if (topBarRight) {
    topBarRight.insertBefore(button, topBarRight.firstChild);
  } else {
    button.classList.add('theme-toggle-floating');
    document.body.appendChild(button);
  }

  updateThemeToggleButton();
}

function initTheme() {
  applyTheme(localStorage.getItem(THEME_STORAGE_KEY) || 'dark', {
    persist: false,
    refreshViews: false,
    announce: false
  });
  ensureThemeToggle();
}

function showToast(message, type = 'info') {
  const toast = document.createElement('div');
  toast.className = `toast toast-${type}`;
  toast.textContent = message;
  document.body.appendChild(toast);
  setTimeout(() => toast.classList.add('show'), 10);
  setTimeout(() => {
    toast.classList.remove('show');
    setTimeout(() => toast.remove(), 300);
  }, 3000);
}

function ensureConfirmModal() {
  if (document.getElementById('confirmModal')) return;
  const modal = document.createElement('div');
  modal.id = 'confirmModal';
  modal.className = 'confirm-modal hidden';
  modal.innerHTML = `
    <div class="confirm-modal-backdrop"></div>
    <div class="confirm-modal-card" role="dialog" aria-modal="true" aria-labelledby="confirmModalTitle">
      <div class="confirm-modal-badge">Confirm</div>
      <h3 id="confirmModalTitle" class="confirm-modal-title">Please Confirm</h3>
      <p id="confirmModalMessage" class="confirm-modal-message">Are you sure you want to continue?</p>
      <div class="confirm-modal-actions">
        <button type="button" id="confirmModalCancel" class="btn btn-secondary">No</button>
        <button type="button" id="confirmModalOk" class="btn btn-primary">Yes</button>
      </div>
    </div>
  `;
  document.body.appendChild(modal);
}

function showConfirmModal(options = {}) {
  ensureConfirmModal();

  const modal = document.getElementById('confirmModal');
  const title = document.getElementById('confirmModalTitle');
  const message = document.getElementById('confirmModalMessage');
  const confirmButton = document.getElementById('confirmModalOk');
  const cancelButton = document.getElementById('confirmModalCancel');
  const backdrop = modal.querySelector('.confirm-modal-backdrop');

  title.textContent = options.title || 'Please Confirm';
  message.textContent = options.message || 'Are you sure you want to continue?';
  confirmButton.textContent = options.confirmText || 'Yes';
  cancelButton.textContent = options.cancelText || 'No';

  modal.classList.remove('hidden');
  requestAnimationFrame(() => modal.classList.add('show'));
  confirmButton.focus();

  return new Promise(resolve => {
    const close = result => {
      modal.classList.remove('show');
      setTimeout(() => modal.classList.add('hidden'), 180);
      confirmButton.removeEventListener('click', onConfirm);
      cancelButton.removeEventListener('click', onCancel);
      backdrop.removeEventListener('click', onCancel);
      document.removeEventListener('keydown', onKeyDown);
      resolve(result);
    };

    const onConfirm = () => close(true);
    const onCancel = () => close(false);
    const onKeyDown = event => {
      if (event.key === 'Escape') close(false);
      if (event.key === 'Enter') close(true);
    };

    confirmButton.addEventListener('click', onConfirm);
    cancelButton.addEventListener('click', onCancel);
    backdrop.addEventListener('click', onCancel);
    document.addEventListener('keydown', onKeyDown);
  });
}

function requestSaveConfirmation(options = {}) {
  const actionText = options.actionText || 'save';
  return showConfirmModal({
    title: options.title || 'Save Changes',
    message: options.message || `Do you want to ${actionText}?`,
    confirmText: options.confirmText || 'Yes',
    cancelText: options.cancelText || 'No'
  });
}

function toNameCase(value) {
  const normalized = String(value || '').trim().replace(/\s+/g, ' ');
  if (!normalized) return '';

  let result = '';
  let capitalizeNext = true;

  for (const ch of normalized) {
    if (/[A-Za-zÀ-ÿ]/.test(ch)) {
      result += capitalizeNext ? ch.toLocaleUpperCase() : ch.toLocaleLowerCase();
      capitalizeNext = false;
    } else {
      result += ch;
      capitalizeNext = /\s/.test(ch) || ch === '-' || ch === "'";
    }
  }

  return result;
}

function applyNameCase(input) {
  if (!input) return;
  const formatted = toNameCase(input.value);
  if (formatted !== input.value) input.value = formatted;
}

function bindAutoNameCase(ids) {
  (ids || []).forEach(id => {
    const input = document.getElementById(id);
    if (!input) return;
    input.addEventListener('input', () => applyNameCase(input));
    applyNameCase(input);
  });
}

function normalizeEmailValue(value) {
  return String(value || '').trim().toLowerCase();
}

const ALLOWED_PUBLIC_EMAIL_DOMAINS = new Set([
  'gmail.com',
  'outlook.com',
  'hotmail.com',
  'yahoo.com',
  'icloud.com',
  'proton.me',
  'protonmail.com',
  'zoho.com',
  'gmx.com',
  'aol.com'
]);

const ALLOWED_GENERAL_EMAIL_EXTENSIONS = new Set(['com', 'net', 'org', 'info', 'biz']);
const ALLOWED_COUNTRY_EMAIL_EXTENSIONS = new Set(['ph', 'us', 'uk', 'au', 'ca', 'jp', 'kr', 'sg']);
const ALLOWED_INSTITUTIONAL_EMAIL_EXTENSIONS = new Set(['edu', 'gov', 'mil']);
const ALLOWED_SECOND_LEVEL_COUNTRY_EXTENSIONS = new Set(['com', 'net', 'org', 'info', 'biz', 'edu', 'gov']);

function hasAllowedInstitutionalEmailSuffix(labels) {
  if (labels.length < 3 || labels.length > 4) return false;

  const last = labels[labels.length - 1];
  const secondLast = labels[labels.length - 2] || '';

  if (ALLOWED_INSTITUTIONAL_EMAIL_EXTENSIONS.has(last)) {
    return true;
  }

  if (ALLOWED_COUNTRY_EMAIL_EXTENSIONS.has(last)) {
    return ALLOWED_SECOND_LEVEL_COUNTRY_EXTENSIONS.has(secondLast);
  }

  return ALLOWED_GENERAL_EMAIL_EXTENSIONS.has(last);
}

function isAcceptedEmailDomain(domain, labels) {
  return ALLOWED_PUBLIC_EMAIL_DOMAINS.has(domain) || hasAllowedInstitutionalEmailSuffix(labels);
}

function getEmailError(value) {
  const email = normalizeEmailValue(value);
  if (!email) return 'Email is required';
  if (email.length > 120) return 'Email is too long';
  if (email.includes('..')) return 'Email cannot contain consecutive dots';

  const atIndex = email.indexOf('@');
  if (atIndex <= 0 || atIndex !== email.lastIndexOf('@') || atIndex === email.length - 1) {
    return 'Email must contain one @ with a valid domain';
  }

  const localPart = email.slice(0, atIndex);
  const domainPart = email.slice(atIndex + 1);
  if (localPart.length > 64) return 'Email name before @ is too long';
  if (!/^[a-z0-9](?:[a-z0-9._%+-]{0,62}[a-z0-9])?$/i.test(localPart)) {
    return 'Email name before @ contains invalid characters';
  }

  const labels = domainPart.split('.');
  if (labels.length < 2 || labels.length > 4) {
    return 'Use a valid email like name@yahoo.com, name@outlook.com, or name@lu.edu.ph';
  }

  if (!labels.every(label => /^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$/i.test(label))) {
    return 'Email domain contains invalid characters';
  }

  if (!/^[a-z]{2,10}$/i.test(labels[labels.length - 1])) {
    return 'Email extension must use letters only';
  }

  if (!isAcceptedEmailDomain(domainPart, labels)) {
    return 'Use Gmail, Outlook, Hotmail, Yahoo, iCloud, Proton, Zoho, GMX, AOL, or a school email like name@campus.edu.ph';
  }

  return null;
}

function isValidEmailAddress(value) {
  return !getEmailError(value);
}

function applyEmailValidation(input) {
  if (!input) return;
  const trimmed = input.value.trim();
  const error = trimmed ? getEmailError(trimmed) : null;

  input.value = trimmed;
  input.setCustomValidity(error || '');
  input.classList.toggle('input-error', !!error && !!trimmed);
  input.classList.toggle('input-ok', !error && !!trimmed);
  input.title = error || '';
}

function bindEmailValidation(selector = 'input[type="email"]') {
  document.querySelectorAll(selector).forEach(input => {
    if (input.dataset.emailBound === 'true') return;
    input.dataset.emailBound = 'true';
    input.addEventListener('input', () => applyEmailValidation(input));
    input.addEventListener('blur', () => applyEmailValidation(input));
    applyEmailValidation(input);
  });
}

function updateAuthLineGroupState(input) {
  const group = input?.closest('.auth-line-group');
  if (!group) return;
  group.classList.toggle('has-value', Boolean(input.value.trim()));
}

function bindAuthLineInputs() {
  document.querySelectorAll('.auth-line-group input').forEach(input => {
    if (input.dataset.authLineBound === 'true') return;
    input.dataset.authLineBound = 'true';

    const group = input.closest('.auth-line-group');
    if (!group) return;

    input.addEventListener('focus', () => group.classList.add('is-focused'));
    input.addEventListener('blur', () => {
      group.classList.remove('is-focused');
      updateAuthLineGroupState(input);
    });
    input.addEventListener('input', () => updateAuthLineGroupState(input));

    updateAuthLineGroupState(input);
  });
}

function initPwaInstall() {
  if (!('serviceWorker' in navigator) || window.location.protocol === 'file:') return;

  window.addEventListener('load', () => {
    navigator.serviceWorker.register('/sw.js').catch(error => {
      console.warn('Service worker registration skipped:', error);
    });
  });
}

document.addEventListener('DOMContentLoaded', () => {
  renderBrandLogos();
  initTheme();
  initPageMotion();
  initNavigationEffects();
  initMobileEnhancements();
  ensureConfirmModal();
  bindEmailValidation();
  bindAuthLineInputs();
  initPwaInstall();
});

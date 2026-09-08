'use strict';

const YAMONE_ADMIN_ID = 'yamone';
const YAMONE_ADMIN_EMAIL = 'yamone@yamone-admin.local';

window.addEventListener('DOMContentLoaded', () => {
  const form = document.getElementById('loginForm');
  const input = document.getElementById('emailInput');
  const badge = document.getElementById('accountBadge');
  if (!form || !input) return;

  form.addEventListener('submit', () => {
    const typed = input.value.trim().toLowerCase();
    if (typed === YAMONE_ADMIN_ID) {
      input.value = YAMONE_ADMIN_EMAIL;
      setTimeout(() => {
        if (input.value === YAMONE_ADMIN_EMAIL) input.value = YAMONE_ADMIN_ID;
      }, 0);
    }
  }, true);

  if (badge) {
    const normalizeBadge = () => {
      if (badge.textContent.trim().toLowerCase() === YAMONE_ADMIN_EMAIL) {
        badge.textContent = YAMONE_ADMIN_ID;
      }
    };
    new MutationObserver(normalizeBadge).observe(badge, { childList: true, characterData: true, subtree: true });
    normalizeBadge();
  }
});

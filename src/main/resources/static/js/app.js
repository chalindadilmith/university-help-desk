// University Help Desk - shared client-side interactions.
// Kept plain/vanilla per the project's tech-stack rules (no frontend framework).

// ---------- Light / dark theme ----------
// The <head> also runs a tiny inline snippet that applies the saved theme
// before first paint (avoids a flash of the wrong theme); this just wires
// up the toggle button and keeps its icon in sync.
function applyTheme(theme) {
    document.documentElement.setAttribute('data-theme', theme);
    try { localStorage.setItem('hd-theme', theme); } catch (e) { /* private mode etc - ignore */ }
    const icon = document.getElementById('themeToggleIcon');
    if (icon) {
        icon.classList.toggle('fa-moon', theme !== 'dark');
        icon.classList.toggle('fa-sun', theme === 'dark');
    }
}

function toggleTheme() {
    const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    applyTheme(current === 'dark' ? 'light' : 'dark');
    // small delight: spin the icon on manual toggle (skipped automatically
    // under prefers-reduced-motion by the global CSS override)
    const icon = document.getElementById('themeToggleIcon');
    if (icon) {
        icon.classList.remove('spin-swap');
        void icon.offsetWidth; // restart animation
        icon.classList.add('spin-swap');
    }
}

document.addEventListener('DOMContentLoaded', function () {
    const current = document.documentElement.getAttribute('data-theme') === 'dark' ? 'dark' : 'light';
    applyTheme(current); // sync the toggle icon with whatever the inline head script already applied
});

function toggleNotifDropdown() {
    document.getElementById('notifDropdown').classList.toggle('open');
}

// Close the notification dropdown when clicking anywhere else on the page.
document.addEventListener('click', function (event) {
    const wrapper = document.querySelector('.notif-wrapper');
    const dropdown = document.getElementById('notifDropdown');
    if (wrapper && dropdown && !wrapper.contains(event.target)) {
        dropdown.classList.remove('open');
    }
});

// Generic "are you sure?" confirmation for destructive actions.
// Usage: <button onclick="return confirmAction('Deactivate this user?')">
function confirmAction(message) {
    return window.confirm(message);
}

// ---------- Chatbot widget (Knowledge Base page) ----------
function sendChatbotMessage() {
    const input = document.getElementById('chatbotInput');
    const message = input.value.trim();
    if (!message) return;

    appendChatMessage(message, 'user');
    input.value = '';

    const typingIndicator = appendTypingIndicator();

    const csrfToken = document.querySelector('meta[name="_csrf"]');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]');
    const headers = { 'Content-Type': 'application/json' };
    if (csrfToken && csrfHeader && csrfHeader.content) {
        headers[csrfHeader.content] = csrfToken.content;
    }

    fetch('/student/kb/chatbot', {
        method: 'POST',
        headers: headers,
        body: JSON.stringify({ message: message })
    })
    .then(function (response) {
        if (!response.ok) throw new Error('Network error');
        return response.json();
    })
    .then(function (data) {
        removeTypingIndicator(typingIndicator);
        appendChatMessage(data.reply, 'bot');
        if (data.offerTicket) {
            appendChatOfferTicket();
        }
    })
    .catch(function () {
        removeTypingIndicator(typingIndicator);
        appendChatMessage("Sorry, I couldn't reach the help desk chatbot service right now. Please try again or create a ticket.", 'bot');
    });
}

function appendChatMessage(text, sender) {
    const container = document.getElementById('chatbotMessages');
    if (!container) return;
    const div = document.createElement('div');
    div.className = 'chatbot-msg ' + sender;
    div.textContent = text;
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

function appendChatOfferTicket() {
    const container = document.getElementById('chatbotMessages');
    if (!container) return;
    const div = document.createElement('div');
    div.className = 'chatbot-msg bot';
    div.innerHTML = 'Still stuck? <a href="/student/tickets?create=1">Create a support ticket</a> and a staff member will help.';
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
}

// Small "..." bubble shown while we're waiting on the chatbot's reply,
// purely cosmetic - removed again as soon as a real reply (or the
// fallback error message) is appended above.
function appendTypingIndicator() {
    const container = document.getElementById('chatbotMessages');
    if (!container) return null;
    const div = document.createElement('div');
    div.className = 'chatbot-msg bot typing';
    div.setAttribute('aria-label', 'Chatbot is typing');
    div.innerHTML = '<span></span><span></span><span></span>';
    container.appendChild(div);
    container.scrollTop = container.scrollHeight;
    return div;
}

function removeTypingIndicator(el) {
    if (el && el.parentNode) el.parentNode.removeChild(el);
}

document.addEventListener('DOMContentLoaded', function () {
    const chatInput = document.getElementById('chatbotInput');
    if (chatInput) {
        chatInput.addEventListener('keydown', function (e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                sendChatbotMessage();
            }
        });
    }
});

// ==========================================================
// Purely cosmetic "next level" interactions below. None of
// this touches forms, links, or server calls above - it only
// adds motion on top of markup that already exists.
// ==========================================================

const prefersReducedMotion = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

// ---------- Slim top-of-page progress bar on navigation ----------
// Gives instant feedback the moment a real link or form submit fires,
// the way most modern web apps do. It never calls preventDefault, so
// navigation, downloads and logout all behave exactly as before.
(function setupProgressBar() {
    const bar = document.createElement('div');
    bar.id = 'hd-progress-bar';
    document.addEventListener('DOMContentLoaded', function () {
        document.body.appendChild(bar);
    });

    function start() {
        bar.style.transition = 'none';
        bar.style.width = '0%';
        bar.style.opacity = '1';
        // eslint-disable-next-line no-unused-expressions
        bar.offsetWidth; // force reflow so the transition below actually animates
        bar.style.transition = 'width 0.5s ease, opacity 0.2s ease';
        bar.style.width = '75%';
    }

    document.addEventListener('click', function (e) {
        const a = e.target.closest && e.target.closest('a[href]');
        if (!a) return;
        const href = a.getAttribute('href') || '';
        if (href.indexOf('#') === 0 || href.indexOf('javascript:') === 0) return;
        if (a.hasAttribute('target') || a.hasAttribute('download')) return;
        if (e.defaultPrevented || e.metaKey || e.ctrlKey || e.shiftKey || e.button === 1) return;
        start();
    });

    document.addEventListener('submit', function (e) {
        if (e.target && e.target.tagName === 'FORM') start();
    });
})();

document.addEventListener('DOMContentLoaded', function () {
    // ---------- Topbar gains a shadow once the page scrolls ----------
    const topbar = document.querySelector('.topbar');
    if (topbar) {
        const onScroll = function () {
            topbar.classList.toggle('scrolled', (window.scrollY || document.documentElement.scrollTop) > 4);
        };
        window.addEventListener('scroll', onScroll, { passive: true });
        onScroll();
    }

    // ---------- Animated count-up for dashboard/report stat numbers ----------
    document.querySelectorAll('.stat-value').forEach(function (el) {
        const original = el.textContent.trim();
        const match = original.match(/^-?\d+(\.\d+)?/);
        if (!match) return; // things like "Open"/"Closed"/"N/A" are left untouched
        const suffix = original.slice(match[0].length);
        const target = parseFloat(match[0]);
        const decimals = match[1] ? match[1].length - 1 : 0;
        if (prefersReducedMotion) return; // keep the final value, skip the animation only

        let startTime = null;
        const duration = 900;
        el.textContent = (0).toFixed(decimals) + suffix;
        function step(ts) {
            if (startTime === null) startTime = ts;
            const progress = Math.min((ts - startTime) / duration, 1);
            const eased = 1 - Math.pow(1 - progress, 3); // ease-out-cubic
            const value = target * eased;
            el.textContent = value.toFixed(decimals) + suffix;
            if (progress < 1) requestAnimationFrame(step);
        }
        requestAnimationFrame(step);
    });

    // ---------- Scroll-reveal for content further down the page ----------
    const revealTargets = document.querySelectorAll(
        'section > .card, section table, section .chatbot-box'
    );
    if (revealTargets.length) {
        revealTargets.forEach(function (el) { el.classList.add('reveal-ready'); });
        if ('IntersectionObserver' in window && !prefersReducedMotion) {
            const observer = new IntersectionObserver(function (entries) {
                entries.forEach(function (entry) {
                    if (entry.isIntersecting) {
                        entry.target.classList.add('in-view');
                        observer.unobserve(entry.target);
                    }
                });
            }, { threshold: 0.1, rootMargin: '0px 0px -40px 0px' });
            revealTargets.forEach(function (el) { observer.observe(el); });
        } else {
            revealTargets.forEach(function (el) { el.classList.add('in-view'); });
        }
    }

    // ---------- Subtle 3D tilt on card hover (desktop, fine pointers only) ----------
    if (!prefersReducedMotion && window.matchMedia && window.matchMedia('(pointer: fine)').matches) {
        document.querySelectorAll('.card-grid > .card, .card-grid > a.card').forEach(function (card) {
            card.addEventListener('mousemove', function (e) {
                const rect = card.getBoundingClientRect();
                const px = (e.clientX - rect.left) / rect.width;
                const py = (e.clientY - rect.top) / rect.height;
                const tiltX = (0.5 - py) * 6;
                const tiltY = (px - 0.5) * 6;
                card.style.setProperty('--tiltX', tiltX.toFixed(2) + 'deg');
                card.style.setProperty('--tiltY', tiltY.toFixed(2) + 'deg');
            });
            card.addEventListener('mouseleave', function () {
                card.style.setProperty('--tiltX', '0deg');
                card.style.setProperty('--tiltY', '0deg');
            });
        });
    }

    // ---------- Ripple feedback on button clicks ----------
    document.addEventListener('click', function (e) {
        const btn = e.target.closest && e.target.closest('.btn');
        if (!btn) return;
        const rect = btn.getBoundingClientRect();
        const size = Math.max(rect.width, rect.height);
        const ripple = document.createElement('span');
        ripple.className = 'ripple';
        ripple.style.width = ripple.style.height = size + 'px';
        ripple.style.left = (e.clientX - rect.left - size / 2) + 'px';
        ripple.style.top = (e.clientY - rect.top - size / 2) + 'px';
        btn.appendChild(ripple);
        ripple.addEventListener('animationend', function () { ripple.remove(); });
        // safety net in case animationend doesn't fire (e.g. element removed by navigation)
        setTimeout(function () { if (ripple.parentNode) ripple.remove(); }, 800);
    });
});

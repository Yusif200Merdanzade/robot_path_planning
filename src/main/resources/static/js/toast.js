// Toast bildirişləri (yalnız görünüş): ekranın küncündə qısa müddət görünüb özü itən mesaj.
//   toast({ type: 'success' | 'error' | 'info', title: '...', message: '...', duration: 2000 })
// Bildiriş ən çox MAX_DURATION qədər görünür: daha uzun duration verilsə də 2 saniyəyə endirilir.
(function () {
    const MAX_DURATION = 2000;
    const LEAVE_MS = 250;   // .toast-leave animasiyasının müddəti (bax: css/theme.css)
    const ICONS = {
        success: '<path d="M20 6 9 17l-5-5"/>',
        error: '<path d="M12 8v5M12 16.5h.01"/><circle cx="12" cy="12" r="9"/>',
        info: '<path d="M12 11v5M12 7.5h.01"/><circle cx="12" cy="12" r="9"/>'
    };
    let stack = null;

    function ensureStack() {
        if (stack) return stack;
        stack = document.createElement('div');
        stack.className = 'toast-stack';
        stack.setAttribute('aria-live', 'polite');
        document.body.appendChild(stack);
        return stack;
    }

    function dismiss(el) {
        if (el.dataset.leaving) return;
        el.dataset.leaving = '1';
        el.classList.add('toast-leave');
        setTimeout(() => el.remove(), LEAVE_MS);
    }

    window.toast = function ({ type = 'info', title = '', message = '', duration = MAX_DURATION } = {}) {
        // Çıxış animasiyası (LEAVE_MS) da bu müddətin içindədir: toast tam duration bitəndə ekrandan getmiş olur
        const visible = Math.min(duration, MAX_DURATION) - LEAVE_MS;
        const el = document.createElement('div');
        el.className = 'toast toast-' + type;
        el.setAttribute('role', type === 'error' ? 'alert' : 'status');
        el.style.setProperty('--toast-duration', visible + 'ms');
        // Mətn textContent ilə yazılır (innerHTML yox): serverdən gələn sözlər HTML kimi işlənməsin
        el.innerHTML = '<span class="toast-icon"><svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.4" '
            + 'stroke-linecap="round" stroke-linejoin="round">' + (ICONS[type] || ICONS.info) + '</svg></span>'
            + '<div class="toast-body"><div class="toast-title"></div><div class="toast-msg"></div></div>'
            + '<button type="button" class="toast-close" aria-label="Bağla">×</button>'
            + '<span class="toast-progress"></span>';
        el.querySelector('.toast-title').textContent = title;
        el.querySelector('.toast-msg').textContent = message;
        if (!message) el.querySelector('.toast-msg').remove();
        el.querySelector('.toast-close').addEventListener('click', () => dismiss(el));

        const container = ensureStack();
        container.appendChild(el);
        // Ekran dolmasın: 4-dən çox olanda ən köhnələr bağlanır (çıxmaqda olanlar sayılmır — dismiss dərhal silmir)
        const active = Array.from(container.children).filter(t => !t.dataset.leaving);
        active.slice(0, Math.max(0, active.length - 4)).forEach(dismiss);

        setTimeout(() => dismiss(el), visible);   // üstünə gələndə də dayanmır: 2 saniyədən artıq qalmasın
        return el;
    };
})();

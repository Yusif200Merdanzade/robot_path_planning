// Tema (tünd / açıq) — yalnız görünüş. <head>-də ilk yüklənir ki, səhifə bir anlıq səhv rəngdə görünməsin.
// İstifadəçinin seçimi brauzerdə yadda qalır; seçim yoxdursa sistemin temasına uyğun gəlir.
(function () {
    const KEY = 'theme';
    const media = window.matchMedia('(prefers-color-scheme: dark)');

    function saved() {
        try { return localStorage.getItem(KEY); } catch (e) { return null; }
    }

    function apply(theme) {
        document.documentElement.dataset.theme = theme;
    }

    apply(saved() || (media.matches ? 'dark' : 'light'));
    media.addEventListener('change', e => { if (!saved()) apply(e.matches ? 'dark' : 'light'); });

    window.currentTheme = () => document.documentElement.dataset.theme;

    window.toggleTheme = function () {
        const next = window.currentTheme() === 'dark' ? 'light' : 'dark';
        apply(next);
        try { localStorage.setItem(KEY, next); } catch (e) { /* gizli rejim: seçim sadəcə yadda qalmır */ }
    };
})();

// Applies the last chosen theme before Angular starts, so the page does not flash the wrong one.
// Kept in sync with ThemeStore (src/app/core/theme/theme.store.ts).
(function () {
  try {
    var theme = window.localStorage.getItem('amaterasu.theme');
    if (theme === 'light' || theme === 'dark') {
      document.documentElement.setAttribute('data-theme', theme);
    }
  } catch (e) {
    // Storage can be blocked; the system theme applies then
  }
})();

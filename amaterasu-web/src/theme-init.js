// Applies the last chosen theme and density before Angular starts, so the page does not flash the
// wrong one. Kept in sync with ThemeStore and DensityStore (src/app/core/theme/).
(function () {
  try {
    var theme = window.localStorage.getItem('amaterasu.theme');
    if (theme === 'light' || theme === 'dark') {
      document.documentElement.setAttribute('data-theme', theme);
    }
    if (window.localStorage.getItem('amaterasu.density') === 'compact') {
      document.documentElement.setAttribute('data-density', 'compact');
    }
  } catch (e) {
    // Storage can be blocked; the system theme applies then
  }
})();

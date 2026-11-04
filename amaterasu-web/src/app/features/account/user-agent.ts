/** "Chrome on macOS" from a user agent string. */
export function deviceName(userAgent: string | null | undefined): string {
  if (!userAgent) {
    return 'Unknown device';
  }
  const ua = userAgent;
  const browser =
    /Edg\//.test(ua) ? 'Edge'
      : /OPR\/|Opera/.test(ua) ? 'Opera'
        : /Firefox\//.test(ua) ? 'Firefox'
          : /Chrome\/|CriOS\//.test(ua) ? 'Chrome'
            : /Safari\//.test(ua) ? 'Safari'
              : /curl\//i.test(ua) ? 'curl'
                : /python/i.test(ua) ? 'Python script'
                  : null;
  const os =
    /iPhone/.test(ua) ? 'iPhone'
      : /iPad/.test(ua) ? 'iPad'
        : /Android/.test(ua) ? 'Android'
          : /Windows/.test(ua) ? 'Windows'
            : /Mac OS X|Macintosh/.test(ua) ? 'macOS'
              : /CrOS/.test(ua) ? 'ChromeOS'
                : /Linux/.test(ua) ? 'Linux'
                  : null;
  if (browser && os) {
    return `${browser} on ${os}`;
  }
  return browser ?? os ?? 'Unknown device';
}

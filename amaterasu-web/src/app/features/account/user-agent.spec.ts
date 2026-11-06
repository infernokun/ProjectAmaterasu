import { deviceName } from './user-agent';

describe('deviceName', () => {
  it('names common browsers and systems', () => {
    expect(deviceName('Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36'))
      .toBe('Chrome on macOS');
    expect(deviceName('Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:131.0) Gecko/20100101 Firefox/131.0')).toBe('Firefox on Windows');
    expect(deviceName('Mozilla/5.0 (iPhone; CPU iPhone OS 18_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.0 Mobile/15E148 Safari/604.1'))
      .toBe('Safari on iPhone');
    expect(deviceName('Mozilla/5.0 (Windows NT 10.0) AppleWebKit/537.36 Chrome/130.0 Safari/537.36 Edg/130.0')).toBe('Edge on Windows');
    expect(deviceName('python-requests/2.32')).toBe('Python script');
    expect(deviceName('node')).toBe('Script or command line');
    expect(deviceName(null)).toBe('Unknown device');
  });
});

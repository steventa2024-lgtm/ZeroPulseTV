// The version a device run must find installed, supplied explicitly (never assumed).
//   --expected-version=3005:3.0.0-beta.5     on the command line, anywhere among the arguments, or
//   ZP_EXPECTED_VERSION=3005:3.0.0-beta.5    in the environment (the command line wins).
// There is no default: a run that does not say which version it is testing stops before it touches a device, and the
// installed versionCode and versionName must both equal what was supplied.
import assert from 'node:assert/strict';

export const USAGE = '--expected-version=VERSION_CODE:VERSION_NAME (for example --expected-version=3005:3.0.0-beta.5) or ZP_EXPECTED_VERSION';

export function parseExpectedVersion(argv, env = {}) {
  const flag = '--expected-version=';
  const args = [];
  let text;
  for (const a of argv) {
    if (a.startsWith(flag)) text = a.slice(flag.length);
    else args.push(a);
  }
  text ??= env.ZP_EXPECTED_VERSION;
  if (!text) throw new Error('The version to verify must be supplied explicitly: ' + USAGE);
  const m = /^(\d{1,9}):([0-9A-Za-z][0-9A-Za-z.+_-]*)$/.exec(text);
  if (!m) throw new Error('Malformed expected version "' + text + '": ' + USAGE);
  return { expected: { code: Number(m[1]), name: m[2] }, args };
}

/** Asserts the dumpsys package text names exactly the expected versionCode and versionName; returns the text. */
export function assertInstalledVersion(dumpsys, expected) {
  const code = Number(/versionCode=(\d+)/.exec(dumpsys)?.[1]);
  const name = /versionName=(\S+)/.exec(dumpsys)?.[1];
  assert.equal(code, expected.code, 'Installed versionCode ' + code + ' is not the expected ' + expected.code);
  assert.equal(name, expected.name, 'Installed versionName ' + name + ' is not the expected ' + expected.name);
  return dumpsys;
}

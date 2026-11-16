import assert from 'node:assert/strict';
import test from 'node:test';
import { execFileSync, spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { parseExpectedVersion, assertInstalledVersion } from '../expected-version.mjs';

const qa = path.join(path.dirname(fileURLToPath(import.meta.url)), '..');
const dumpsys = (code, name) => `Packages:\n  Package [tv.calmhome.launcher] (abc):\n    versionCode=${code} minSdk=26 targetSdk=34\n    versionName=${name}\n`;

test('the expected version comes from the command line, wherever it is, and leaves the other arguments alone', () => {
  const r = parseExpectedVersion(['SERIAL', '--expected-version=3005:3.0.0-beta.5', 'out-dir', '--published-release'], {});
  assert.deepEqual(r.expected, { code: 3005, name: '3.0.0-beta.5' });
  assert.deepEqual(r.args, ['SERIAL', 'out-dir', '--published-release']);
});

test('the environment is used when the command line says nothing, and the command line wins over it', () => {
  assert.deepEqual(parseExpectedVersion(['S'], { ZP_EXPECTED_VERSION: '3005:3.0.0-beta.5' }).expected, { code: 3005, name: '3.0.0-beta.5' });
  assert.equal(parseExpectedVersion(['--expected-version=3006:3.0.0-beta.6', 'S'], { ZP_EXPECTED_VERSION: '3005:3.0.0-beta.5' }).expected.code, 3006);
});

test('there is no default: a run that does not say which version it tests is refused', () => {
  assert.throws(() => parseExpectedVersion(['SERIAL'], {}), /supplied explicitly/);
  assert.throws(() => parseExpectedVersion(['SERIAL', '--expected-version='], {}), /supplied explicitly/);
});

test('a malformed version is refused', () => {
  for (const bad of ['3005', 'x:3.0.0', '3005:', '3005: 3.0.0', '3005:3.0.0;rm', ':3.0.0-beta.5']) {
    assert.throws(() => parseExpectedVersion(['--expected-version=' + bad], {}), /Malformed/, bad);
  }
});

test('the installed versionCode and versionName must both equal what was supplied (the assertion is not weakened)', () => {
  const expected = { code: 3005, name: '3.0.0-beta.5' };
  assert.equal(assertInstalledVersion(dumpsys(3005, '3.0.0-beta.5'), expected).length > 0, true);
  assert.throws(() => assertInstalledVersion(dumpsys(3002, '3.0.0-beta.2'), expected), /versionCode 3002 is not the expected 3005/);
  assert.throws(() => assertInstalledVersion(dumpsys(3005, '3.0.0-beta.4'), expected), /versionName 3\.0\.0-beta\.4 is not the expected 3\.0\.0-beta\.5/);
  assert.throws(() => assertInstalledVersion(dumpsys(3004, '3.0.0-beta.5'), expected), /versionCode 3004/);
  assert.throws(() => assertInstalledVersion('nothing installed', expected), /versionCode NaN/);
});

test('both device scripts stop with a usage error, before touching any device, when no version is supplied', () => {
  for (const script of ['device-smoke.mjs', 'graphite-profile.mjs']) {
    const r = spawnSync(process.execPath, [path.join(qa, script), 'NO-SUCH-SERIAL', 'out'], { encoding: 'utf8', env: { PATH: process.env.PATH, SystemRoot: process.env.SystemRoot, ADB: 'adb-must-not-run' } });
    assert.notEqual(r.status, 0, script);
    assert.match(r.stderr, /supplied explicitly/, script + ': ' + r.stderr.slice(0, 200));
  }
});

test('the device scripts no longer carry a hard-coded version', () => {
  for (const script of ['device-smoke.mjs', 'graphite-profile.mjs']) {
    const text = execFileSync(process.execPath, ['-e', `process.stdout.write(require('node:fs').readFileSync(${JSON.stringify(path.join(qa, script))}, 'utf8'))`], { encoding: 'utf8' });
    assert.doesNotMatch(text, /versionCode=3\d{3}/, script);
    assert.doesNotMatch(text, /3\\\.0\\\.0-beta/, script);
    assert.match(text, /assertInstalledVersion\(/, script);
  }
});

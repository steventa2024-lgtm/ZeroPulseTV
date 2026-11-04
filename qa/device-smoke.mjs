// Requires Node 22+ and an already-installed v3 candidate. Use only an authorized serial.
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const serial = process.argv[2];
const displayTest = process.argv[4] === '--emulator-display';
if (!serial || process.argv.length > 5 || (process.argv[4] && !displayTest)) {
  console.error('Usage: node qa/device-smoke.mjs ADB_SERIAL [private-output-directory] [--emulator-display]');
  process.exit(2);
}
const pkg = 'tv.calmhome.launcher';
const adb = (...args) => execFileSync(process.env.ADB || 'adb', ['-s', serial, ...args],
  { timeout: 60000, maxBuffer: 32 * 1024 * 1024 });
const shell = (...args) => adb('shell', ...args).toString();
assert.equal(adb('get-state').toString().trim(), 'device', 'Device must already be authorized');
const version = shell('dumpsys', 'package', pkg);
assert.match(version, /versionName=3\.0\.0-beta\.1\b/, 'Install the approved v3 candidate before running this test');
assert.ok(Number(version.match(/versionCode=(\d+)/)?.[1]) > 2201, 'Expected a newer version than the TCL baseline');

const output = process.argv[3] || fileURLToPath(new URL('./device-evidence/' + Date.now() + '/', import.meta.url));
mkdirSync(output, { recursive: true });
const save = (name, data) => writeFileSync(path.join(output, name), data);
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
const decode = s => s.replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&gt;/g, '>').replace(/&lt;/g, '<').replace(/&amp;/g, '&');
function tree() {
  const xml = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').toString();
  assert.ok(xml.includes('</hierarchy>'), 'UIAutomator did not return a complete accessibility hierarchy');
  const nodes = [...xml.matchAll(/<node\s+[^>]*>/g)].map(([tag]) => {
    const n = Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(([, k, v]) => [k, decode(v)]));
    n.label = n['content-desc'] || n.text || '';
    const rect = (n.bounds || '').match(/-?\d+/g)?.map(Number) || [];
    n.x = (rect[0] + rect[2]) / 2;
    n.y = (rect[1] + rect[3]) / 2;
    n.id = [n.class, n.label, n.bounds].join('|');
    return n;
  }).filter(n => n.package === pkg);
  assert.ok(nodes.length, 'Launcher is not the foreground accessibility window');
  return { xml, nodes, focused: nodes.find(n => n.focused === 'true') };
}
async function key(name) {
  assert.match(shell('dumpsys', 'window'), /mCurrentFocus=[^\n]*tv\.calmhome\.launcher\//,
    'Stopped before sending input to a different app');
  shell('input', 'keyevent', name);
  await pause(260);
}
const matches = (node, target) => !!node && (typeof target === 'string' ? node.label === target : target.test(node.label));
const directions = ['DPAD_UP', 'DPAD_RIGHT', 'DPAD_DOWN', 'DPAD_LEFT'];
function priority(from, target) {
  if (!target) return ['DPAD_DOWN', 'DPAD_UP', 'DPAD_RIGHT', 'DPAD_LEFT'];
  const dx = target.x - from.x, dy = target.y - from.y;
  const scores = [-dy, dx, dy, -dx];
  return [...directions].sort((a, b) => scores[directions.indexOf(b)] - scores[directions.indexOf(a)]);
}
// Explore focus transitions actually made by Android, using the target's location as a hint.
// If a panel clamps an arrow at an edge, use the observed transitions to try another direction.
async function focus(target) {
  const edges = new Map();
  let ui = tree();
  for (let attempt = 0; attempt < 60; attempt++) {
    const current = ui.focused;
    assert.ok(current, 'No launcher control has keyboard focus');
    if (matches(current, target)) return;
    const wanted = ui.nodes.find(n => n.focusable === 'true' && matches(n, target));
    const order = priority(current, wanted);
    const queue = [{ id: current.id, first: null }], seen = new Set([current.id]);
    let next;
    for (let i = 0; i < queue.length && !next; i++) {
      const state = queue[i], visited = edges.get(state.id) || new Map();
      for (const direction of order) {
        if (!visited.has(direction)) { next = state.first || direction; break; }
        const id = visited.get(direction);
        if (!seen.has(id)) { seen.add(id); queue.push({ id, first: state.first || direction }); }
      }
    }
    assert.ok(next, 'Focus is trapped before ' + target);
    await key(next);
    ui = tree();
    const reached = ui.focused;
    assert.ok(reached, 'Focus lost after ' + next);
    if (!edges.has(current.id)) edges.set(current.id, new Map());
    edges.get(current.id).set(next, reached.id);
  }
  throw Error('D-pad could not reach ' + target);
}
async function open(target) {
  await focus(target);
  await key('DPAD_CENTER');
}
function capture(name, expected) {
  const ui = tree();
  assert.ok(ui.nodes.some(n => matches(n, expected)), 'Expected ' + expected + ' on ' + name);
  assert.ok(ui.focused, 'No focus on ' + name);
  const png = adb('exec-out', 'screencap', '-p');
  assert.equal(png.readUInt32BE(0), 0x89504e47, 'Invalid screenshot');
  const [x1,y1,x2,y2] = ui.focused.bounds.match(/-?\d+/g).map(Number);
  assert.ok(x2 > x1 && y2 > y1 && x1 >= 0 && y1 >= 0 &&
    x2 <= png.readUInt32BE(16) && y2 <= png.readUInt32BE(20), 'Focus clipped on ' + name);
  save(name + '.png', png);
  save(name + '.xml', ui.xml);
  console.log('PASS', name, '(focus:', ui.focused.label || ui.focused.class, ')');
}
async function backTo(target) {
  await key('BACK');
  assert.ok(matches(tree().focused, target), 'Back did not restore focus to ' + target);
}

async function emulatorDisplayTest() {
  assert.match(serial, /^emulator-\d+$/, 'Display overrides are restricted to an emulator serial');
  assert.equal(shell('getprop', 'ro.kernel.qemu').trim(), '1', 'Display overrides require a disposable emulator');
  const size = shell('wm', 'size');
  const density = shell('wm', 'density');
  const originalSize = size.match(/Override size: (\d+x\d+)/)?.[1] || 'reset';
  const originalDensity = density.match(/Override density: (\d+)/)?.[1] || 'reset';
  const motion = shell('settings', 'get', 'global', 'animator_duration_scale').trim();
  save('display-before.txt', size + density + 'animator_duration_scale=' + motion + '\n');
  try {
    shell('wm', 'size', '3840x2160');
    shell('wm', 'density', '640'); // 1080p/320 and 2160p/640 have the same logical TV canvas.
    shell('settings', 'put', 'global', 'animator_duration_scale', '0');
    assert.equal(shell('settings','get','global','animator_duration_scale').trim(), '0');
    await pause(750);
    await open('Downloads');
    await focus('Live Sports, browse sources');
    capture('13-4k-reduced-motion', 'Download Center');
    const png = adb('exec-out', 'screencap', '-p');
    assert.deepEqual([png.readUInt32BE(16), png.readUInt32BE(20)], [3840, 2160], 'Expected true 4K raster');
    await key('DPAD_RIGHT');
    assert.equal(tree().focused?.label, 'Movies & TV, browse sources', 'Reduced-motion D-pad movement');
    capture('14-4k-movies-focus', 'Download Center');
    await open('Home');
  } finally {
    if (motion === 'null') shell('settings', 'delete', 'global', 'animator_duration_scale');
    else shell('settings', 'put', 'global', 'animator_duration_scale', motion);
    shell('wm', 'density', originalDensity);
    shell('wm', 'size', originalSize);
    assert.equal(shell('wm','size'), size, 'Original display size was not restored');
    assert.equal(shell('wm','density'), density, 'Original display density was not restored');
    assert.equal(shell('settings','get','global','animator_duration_scale').trim(), motion, 'Animator scale was not restored');
  }
}

const home = () => shell('cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.MAIN', '-c', 'android.intent.category.HOME').trim();
const beforeHome = home();
try {
  save('package.txt', version.split('\n').filter(line => /version(Code|Name)=/.test(line)).join('\n'));
  const launch = shell('am', 'start', '-W', '-n', pkg + '/.MainActivity');
  assert.match(launch, /Status: ok/);
  save('startup.txt', launch);
  await pause(650);
  // A previous package confirmation can leave Android in touch mode. The first D-pad key exits it.
  if (!tree().focused) await key('DPAD_DOWN');
  capture('01-home', 'All Apps');
  await open('Downloads');
  await focus('Live Sports, browse sources');
  capture('02-download-center', 'Download Center');
  for (const [index, category] of ['Live Sports', 'Movies & TV', 'Anime'].entries()) {
    const card = category + ', browse sources';
    await open(card);
    capture('03-category-' + index, 'Add a trusted source');
    await open('Add a trusted source');
    capture('04-source-editor-' + index, 'Save source');
    await backTo('Add a trusted source');
    await backTo(card);
  }
  // An empty Go exercises validation without sending a network request or modifying the catalog.
  await open(/^Go\b/);
  capture('05-invalid-link', /Enter an HTTPS link/);
  assert.equal(tree().focused?.class, 'android.widget.EditText', 'Invalid entry did not restore edit focus');
  await open('Manage Downloads');
  capture('06-library', 'Manage Downloads');
  await backTo('Manage Downloads');
  await open('Apps');
  capture('07-installed-apps', 'All Apps');
  await open('Inputs');
  capture('08-inputs', /OK to switch/);
  await open('Settings');
  capture('09-settings', 'Appearance');
  await focus('Wallpapers');
  await open('Choose wallpaper');
  capture('10-wallpapers', 'Wallpapers');
  await backTo('Choose wallpaper');
  // Settings includes a category named Assistant. Select the unique navigation TextView.
  if (tree().nodes.some(n => n.class === 'android.widget.TextView' && n.label === 'Assistant' && n.clickable === 'true')) {
    await open('Home');
    await open('Assistant');
    capture('11-assistant', /Local TV commands|Connect your PC|PC paired|Gateway/i);
    await key('BACK');
  } else console.log('SKIP Assistant: disabled in owner settings');
  await open('Home');
  capture('12-home-return', 'Downloads');
  if (displayTest) await emulatorDisplayTest();
  console.log('PASS default HOME membership', home() === beforeHome);
  assert.equal(home(), beforeHome, 'Default HOME changed during test');
} catch (error) {
  try { save('failure.xml', tree().xml); } catch { /* foreground may have changed */ }
  throw error;
} finally {
  save('gfxinfo.txt', shell('dumpsys', 'gfxinfo', pkg));
  save('meminfo.txt', shell('dumpsys', 'meminfo', pkg));
  save('display.txt', shell('wm', 'size') + shell('wm', 'density'));
  console.log('Private device evidence:', output);
}

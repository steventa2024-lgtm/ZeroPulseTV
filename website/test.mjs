import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
const html = await readFile(new URL('./index.html', import.meta.url), 'utf8');
const css = await readFile(new URL('./styles.css', import.meta.url), 'utf8');
const js = await readFile(new URL('./app.js', import.meta.url), 'utf8');
const config = JSON.parse(await readFile(new URL('./release.json', import.meta.url), 'utf8'));
test('release pins exact version-specific public APK', () => {
 assert.equal(config.apk, 'https://github.com/steventa2024-lgtm/ZeroPulseTV/releases/download/v2.1.0-beta.1/ZeroPulseTV-v2.1.0-beta.1.apk');
 assert.equal(config.bytes, 5744160);
 assert.equal(config.sha256, 'ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401');
 assert.equal(config.certificate, '0d6a6f7b254e84003ddbfc274233ecdc0b8f8af5442114f072267b7146dee4cd');
});
test('all download CTA hrefs share the authoritative template', () => {
 const links = [...html.matchAll(/<a[^>]*class="[^"]*apk-link[^"]*"[^>]*href="([^"]+)"/g)];
 assert.equal(links.length, 3); links.forEach(x => assert.equal(x[1], '{{APK}}'));
 assert.ok(!html.includes('/releases/latest'));
});
test('technical fingerprint disclosure is closed by default', () => {
 assert.match(html, /<details class="technical">/);
 assert.ok(!/<details[^>]*class="technical"[^>]*\bopen\b/.test(html));
 const technical = html.slice(html.indexOf('<details class="technical">'), html.indexOf('<section id="faq"'));
 assert.ok(technical.includes('{{SHA256}}') && technical.includes('{{CERTIFICATE}}'));
});
test('three ordered setup panels with TV and computer instructions', () => {
 assert.equal((html.match(/class="step" data-step=/g)||[]).length, 3);
 assert.match(html, /data-step="1"[^>]*hidden/); assert.match(html, /data-step="2"[^>]*hidden/);
 assert.ok(html.includes('local Claude Code') && html.includes('same trusted Wi-Fi'));
});
test('clipboard failures reveal manual copy and never false-success', () => {
 assert.ok(js.includes('await navigator.clipboard.writeText(text)'));
 assert.ok(js.includes('dialog.showModal()') && js.includes("$('#fallback-text').select()"));
 assert.ok(js.includes('Automatic copy was blocked'));
});
test('prompt failure keeps copy buttons disabled', () => {
 assert.match(html, /id="copy-prompt"[^>]*disabled/);
 assert.ok(js.includes('No incomplete prompt will be copied'));
});
test('seven actual screenshot paths are declared and used', () => {
 assert.equal(Object.keys(config.screenshots).length, 7);
 for(const [file, blob] of Object.entries(config.screenshots)) { assert.ok(html.includes(`assets/screenshots/${file}`)); assert.match(blob,/^[a-f0-9]{40}$/); }
});
test('no pairing-token or connected demo screenshot is promoted', () => {
 assert.ok(!html.includes('08-jarvis-pairing.png')); assert.ok(!html.includes('09-jarvis-connected.png'));
});
test('fresh-device and experimental JARVIS limitations remain visible', () => {
 assert.ok(html.includes('Fresh installs and other devices remain unverified'));
 assert.ok(html.includes('PUBLIC PAIRING NOT AVAILABLE') && html.includes('Not paired'));
 assert.ok(html.includes('No voice or always-listening'));
});
test('no universal rollback or automatic debloat claim', () => {
 assert.ok(html.includes('not a guaranteed one-line restore'));
 assert.ok(html.includes('No general manufacturer-package disabling is authorized'));
 assert.ok(html.includes('No uninstall, factory reset'));
});
test('every local anchor has a matching id', () => {
 const ids = new Set([...html.matchAll(/\bid="([^"]+)"/g)].map(x=>x[1]));
 for (const a of html.matchAll(/href="#([^"]+)"/g)) assert.ok(ids.has(a[1]),`Missing ${a[1]}`);
});
test('header is opaque sticky and screens are not cropped', () => {
 assert.ok(css.includes('position:sticky') && css.includes('background:#080d17'));
 assert.ok(css.includes('object-fit:contain') && !css.includes('object-fit:cover'));
 assert.ok(css.includes('prefers-reduced-motion'));
});
test('no third-party scripts, inline handlers or client shell execution', () => {
 assert.ok(!/<script[^>]+src="https?:/.test(html));
 assert.ok(!/\sonclick=/.test(html));
 assert.ok(!js.includes('eval('));
});
test('one canonical prompt: fetched from the published text file, never embedded in JavaScript', () => {
 assert.ok(js.includes("fetch('claude-install-prompt.txt'"));
 for (const fragment of ['OFFICIAL RELEASE', 'PREPARE ON THIS COMPUTER', 'adb -s <verified-serial>']) assert.ok(!js.includes(fragment), fragment);
 assert.ok(html.includes('href="claude-install-prompt.txt" download'));
});
test('primary copy control and plain-text download are prominent', () => {
 assert.ok(js.includes("'Copy installation prompt'"));
 assert.ok(html.indexOf('id="copy-prompt"') < html.indexOf('Download prompt (.txt)'));
 assert.ok(html.indexOf('Download prompt (.txt)') < html.indexOf('data-step="0"'));
});
test('TV step covers pairing codes, displayed address/port and no port forwarding', () => {
 const tv = html.slice(html.indexOf('data-step="0"'), html.indexOf('data-step="1"'));
 assert.ok(tv.includes('pairing code') && tv.includes('IP address and port') && tv.includes('Never expose ADB through router port forwarding'));
});
test('computer step states local execution, no website connection, optional Claude and manual route', () => {
 const pc = html.slice(html.indexOf('data-step="1"'), html.indexOf('data-step="2"'));
 assert.ok(pc.includes('This website never connects to your TV') && pc.includes('Claude is not needed after installation') && pc.includes('Manual installation guide'));
});
test('dialogs return focus to their opener', () => {
 assert.ok(js.includes('function openDialog') && js.includes("addEventListener('close'") && js.includes('returnTarget'));
 assert.ok(!/\.showModal\(\)/.test(js.replace('dialog.showModal();\n}', '')));
});

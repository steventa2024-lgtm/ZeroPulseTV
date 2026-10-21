import { readFile, writeFile, mkdir, copyFile, rm } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { dirname, resolve, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, '..');
const out = join(here, 'dist');
const release = JSON.parse(await readFile(join(here, 'release.json'), 'utf8'));
if (!/^[a-f0-9]{64}$/.test(release.sha256) || !/^[a-f0-9]{64}$/.test(release.certificate)) throw new Error('Invalid release fingerprints');
if (release.apk !== `${release.repo}/releases/download/${release.tag}/${release.filename}`) throw new Error('APK URL mismatch');
await rm(out, { recursive: true, force: true });
await mkdir(join(out, 'assets', 'screenshots'), { recursive: true });
function gitBlobHash(bytes) {
  return createHash('sha1').update(`blob ${bytes.length}\0`).update(bytes).digest('hex');
}
for (const [name, expectedBlob] of Object.entries(release.screenshots)) {
  const source = join(repo, 'docs', 'screenshots', name);
  const bytes = await readFile(source);
  if (gitBlobHash(bytes) !== expectedBlob) throw new Error(`Screenshot differs from published release: ${name}`);
  await copyFile(source, join(out, 'assets', 'screenshots', name));
}
await copyFile(join(repo, 'branding/logo/zeropulse-tv-wordmark.svg'), join(out, 'assets/wordmark.svg'));
await copyFile(join(repo, 'branding/logo/zeropulse-pulse-mark.svg'), join(out, 'assets/pulse.svg'));
const prompt = await readFile(join(repo, 'docs/claude-install-prompt.txt'), 'utf8');
if (!prompt.startsWith('Install the official ZeroPulse TV 2.1 public beta') || !prompt.includes(release.sha256) || !prompt.includes(release.apk)) throw new Error('Installation prompt/release mismatch');
const promptBytes = await readFile(join(repo, 'docs/claude-install-prompt.txt'));
await writeFile(join(out, 'claude-install-prompt.txt'), promptBytes);
const vars = {
  APK: release.apk, REPO: release.repo, INSTALL: release.install,
  PROMPT: release.prompt, FILENAME: release.filename, NOTES: release.notes,
  CHECKSUMS: release.checksums, SHA256: release.sha256, CERTIFICATE: release.certificate,
};
let html = await readFile(join(here, 'index.html'), 'utf8');
html = html.replace(/\{\{([A-Z0-9_]+)\}\}/g, (_, key) => {
  if (!(key in vars)) throw new Error(`Unknown template field: ${key}`);
  return String(vars[key]).replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
});
if (/\{\{|\}\}/.test(html)) throw new Error('Unresolved template placeholder in index.html');
await writeFile(join(out, 'index.html'), html);
for (const name of ['styles.css', 'app.js']) await copyFile(join(here, name), join(out, name));
await writeFile(join(out, 'robots.txt'), 'User-agent: *\nAllow: /\n');
await copyFile(join(repo, 'THIRD_PARTY_NOTICES.md'), join(out, 'THIRD_PARTY_NOTICES.txt'));
const copied = await readFile(join(out, 'claude-install-prompt.txt'));
if (!copied.equals(promptBytes)) throw new Error('Published prompt differs from docs/claude-install-prompt.txt');
console.log(`Built static site: ${out}. Verified ${Object.keys(release.screenshots).length} original screenshot blobs. No APK rebuilt or repackaged.`);

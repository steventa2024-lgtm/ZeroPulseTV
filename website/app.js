// Public, static UI only. This website never runs ADB or accesses the user's LAN.
const $ = (s) => document.querySelector(s);
const $$ = (s) => [...document.querySelectorAll(s)];
let step = 0;
const panels = $$('.step');
const dots = $$('.step-dots li');
function showStep(next, focus = true) {
  step = Math.max(0, Math.min(panels.length - 1, next));
  panels.forEach((panel, index) => { panel.hidden = index !== step; });
  dots.forEach((dot, index) => { index === step ? dot.setAttribute('aria-current', 'step') : dot.removeAttribute('aria-current'); });
  $('#step-count').textContent = `${step + 1} / ${panels.length}`;
  $('#previous-step').disabled = step === 0;
  $('#next-step').textContent = step === 2 ? 'Done ✓' : 'Next →';
  if (focus) panels[step].querySelector('h3').focus({ preventScroll: true });
}
$('#previous-step').addEventListener('click', () => showStep(step - 1));
$('#next-step').addEventListener('click', () => {
  if (step < 2) showStep(step + 1);
  else { $('#copy-status').textContent = 'Guide complete — follow the prompt in your local Claude Code session. No installation was executed by this website.'; }
});
const hints = {
  windows: 'Use your local Windows terminal or IDE. Give Claude the full Windows path to the APK.',
  macos: 'Use Terminal or your local IDE. Give Claude the full macOS path to the APK.',
  linux: 'Use your local terminal or IDE. Give Claude the full Linux path to the APK.',
};
$$('[data-os]').forEach(button => button.addEventListener('click', () => {
  $$('[data-os]').forEach(other => other.setAttribute('aria-pressed', String(button === other)));
  $('#os-hint').textContent = hints[button.dataset.os];
}));
let promptText = '';
async function copyText(text, status) {
  if (!text) return false;
  try {
    if (!navigator.clipboard?.writeText) throw new Error('Clipboard unavailable');
    await navigator.clipboard.writeText(text);
    status.textContent = 'Copied. Paste into your local Claude Code session.';
    return true;
  } catch {
    status.textContent = 'Automatic copy was blocked. Select and copy the text in the dialog.';
    const dialog = $('#copy-fallback');
    $('#fallback-text').value = text;
    dialog.showModal();
    $('#fallback-text').focus();
    $('#fallback-text').select();
    return false;
  }
}
fetch('claude-install-prompt.txt', { cache: 'no-cache' }).then(async response => {
  if (!response.ok) throw new Error('Prompt not available');
  const text = await response.text();
  if (!text.startsWith('Install the official ZeroPulse TV 2.1 public beta') || !text.includes('ac6ac5a49fa1617b155d048ad17aaeea8bc74fc47ac195ca823d6bd5d52f0401')) throw new Error('Unexpected prompt content');
  promptText = text;
  $('#prompt-text').value = promptText;
  $('#copy-prompt').disabled = false;
  $('#copy-prompt').textContent = 'Copy Claude Code prompt';
  $('#copy-prompt-secondary').disabled = false;
}).catch(() => {
  $('#copy-prompt').textContent = 'Prompt unavailable';
  $('#copy-status').textContent = 'Open “View the prompt” below and use its GitHub source link. No incomplete prompt will be copied.';
});
['#copy-prompt', '#copy-prompt-secondary'].forEach(selector => $(selector).addEventListener('click', () => copyText(promptText, $('#copy-status'))));
$('#close-copy-fallback').addEventListener('click', () => $('#copy-fallback').close());
let noticeTimer;
$$('.copy-value').forEach(button => button.addEventListener('click', async () => {
  const status = $('#notification');
  const copied = await copyText(button.dataset.copy, status);
  if (copied) status.textContent = 'Verification fingerprint copied.';
  clearTimeout(noticeTimer);
  noticeTimer = setTimeout(() => { status.textContent = ''; }, 4000);
}));
const menuButton = $('.menu-toggle');
function closeMenu() { menuButton.setAttribute('aria-expanded', 'false'); menuButton.setAttribute('aria-label', 'Open navigation'); $('#mobile-nav').hidden = true; }
menuButton.addEventListener('click', () => {
  const open = menuButton.getAttribute('aria-expanded') !== 'true';
  menuButton.setAttribute('aria-expanded', String(open));
  menuButton.setAttribute('aria-label', open ? 'Close navigation' : 'Open navigation');
  $('#mobile-nav').hidden = !open;
});
$$('#mobile-nav a').forEach(a => a.addEventListener('click', closeMenu));
document.addEventListener('keydown', event => { if (event.key === 'Escape' && !$('#mobile-nav').hidden) { closeMenu(); menuButton.focus(); } });
$$('.image-open').forEach(button => button.addEventListener('click', () => {
  $('#lightbox-image').src = button.dataset.image;
  $('#lightbox-image').alt = button.dataset.caption;
  $('#lightbox-caption').textContent = button.dataset.caption;
  $('#original-image').href = button.dataset.image;
  $('#lightbox').showModal();
}));
$('#close-lightbox').addEventListener('click', () => $('#lightbox').close());
showStep(0, false);

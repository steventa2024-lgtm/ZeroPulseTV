// Real-device checks. Default mode sends only two built-in local commands; no provider request.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const [serial, mode = 'text'] = process.argv.slice(2);
const modes = ['text', '--tts-preview', '--recognizer-cancel', '--recognizer-dictate'];
if (!serial || !modes.includes(mode) || process.argv.length > 4) {
  throw Error('Usage: node qa/assistant-device-smoke.mjs ADB_SERIAL [--tts-preview|--recognizer-cancel|--recognizer-dictate]');
}
const pkg = 'tv.calmhome.launcher', assistant = pkg + '/.assistant.AssistantActivity';
const out = path.join(fileURLToPath(new URL('./device-evidence/', import.meta.url)), 'assistant-' + Date.now());
mkdirSync(out, {recursive: true});
const adb = (...args) => execFileSync(process.env.ADB || 'adb', ['-s', serial, ...args], {timeout: 30000, maxBuffer: 32 << 20});
const shell = (...args) => adb('shell', ...args).toString().trim();
const pause = ms => new Promise(resolve => setTimeout(resolve, ms));
assert.equal(adb('get-state').toString().trim(), 'device', 'Choose an authorized serial');
assert.match(shell('dumpsys','package',pkg), /versionName=3\.0\.0-beta\.2\b/, 'Install the approved Graphite candidate first');
const home = () => shell('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME');
const beforeHome = home();
const log = [];
function record(text) { log.push(text); console.log(text); }
function current() {
  const m = shell('dumpsys','window').match(/mCurrentFocus=Window\{[^\n}]*?\s([\w.]+)\/([\w.$]+)/);
  return m ? m[1] + '/' + m[2] : '';
}
function localWindow() { assert.ok(current().startsWith(pkg + '/'), 'Foreground has changed; no input sent'); }
function decode(s) { return s.replace(/&quot;/g,'"').replace(/&apos;/g,"'").replace(/&lt;/g,'<').replace(/&gt;/g,'>').replace(/&amp;/g,'&'); }
function ui() {
  const xml = adb('exec-out','uiautomator','dump','/dev/tty').toString();
  assert.ok(xml.includes('</hierarchy>'), 'No complete accessibility tree');
  return [...xml.matchAll(/<node\s+[^>]*>/g)].map(([tag]) => {
    const n = Object.fromEntries([...tag.matchAll(/([\w-]+)="([^"]*)"/g)].map(([,k,v]) => [k,decode(v)]));
    const rect = n.bounds.match(/\d+/g)?.map(Number) || [];
    n.x = Math.round((rect[0] + rect[2]) / 2);
    n.y = Math.round((rect[1] + rect[3]) / 2);
    n.label = n['content-desc'] || n.text;
    return n;
  }).filter(n => n.package === pkg);
}
function find(nodes, label) {
  return nodes.find(n => n.label === label && n.clickable === 'true') || nodes.find(n => n.label === label);
}
function tap(n) {
  assert.ok(n && Number.isFinite(n.x) && n.enabled !== 'false', 'Required enabled control unavailable');
  localWindow();
  shell('input','tap',String(n.x),String(n.y));
}
async function click(label) { tap(find(ui(), label)); await pause(300); }
async function waitText(match, seconds = 15) {
  const deadline = Date.now() + seconds * 1000;
  do {
    const nodes = ui();
    if (nodes.some(n => match(n.text))) return nodes;
    await pause(280);
  } while (Date.now() < deadline);
  throw Error('Expected UI state was not observed');
}
function evidence(name) {
  localWindow();
  writeFileSync(path.join(out,name + '.png'), adb('exec-out','screencap','-p'));
}
async function startAssistant() {
  assert.match(shell('am','start','-W','-n',pkg + '/.MainActivity','--es','zp_open','home'), /Status: ok/);
  await pause(350);
  await click('Assistant');
  const nodes = await waitText(t => t === 'Conversation');
  assert.ok(current().endsWith('.assistant.AssistantActivity'), 'Assistant did not open');
  assert.ok(nodes.find(n => n.class === 'android.widget.EditText') && find(nodes,'Send'), 'Text fallback unavailable');
  assert.ok(find(nodes,'Push to talk') && find(nodes,'Stop response'), 'Missing mic or reply control');
  return nodes;
}
function pairing(nodes) {
  const states = ['Not paired','Paired','Revoked by PC','Pairing denied','Certificate changed'];
  return states.find(s => nodes.some(n => n.text === s)) || 'not visible';
}
async function localCommand(command, expectedTitle) {
  assert.ok(['show my downloads','open apps'].includes(command));
  const draft = ui().find(n => n.class === 'android.widget.EditText');
  assert.ok(!draft.text || draft.text === 'Ask a question or type a TV command', 'An existing draft is present');
  tap(draft);
  shell('input','text',command.replaceAll(' ','%s'));
  assert.ok(ui().some(n => n.class === 'android.widget.EditText' && n.text === command), 'Typed command differs');
  if (/mInputShown=true/.test(shell('dumpsys','input_method'))) { localWindow(); shell('input','keyevent','BACK'); }
  await click('Send');
  await waitText(t => t === expectedTitle);
  assert.ok(current().endsWith('.MainActivity'), 'Local navigation did not open the launcher');
  evidence('local-' + expectedTitle.replaceAll(' ','-'));
  record('PASS local typed command opened ' + expectedTitle);
}
async function textTest() {
  let nodes = await startAssistant();
  const saved = pairing(nodes);
  record('Observed PC pairing state: ' + saved);
  evidence('assistant');
  await localCommand('show my downloads','Manage Downloads');
  localWindow(); shell('input','keyevent','BACK');
  await waitText(t => t === 'Download Center');
  nodes = await startAssistant();
  assert.equal(pairing(nodes), saved, 'Saved pairing state changed across navigation');
  await localCommand('open apps','All Apps');
  await startAssistant();
  localWindow(); shell('input','keyevent','BACK');
  await pause(400);
  assert.ok(current().endsWith('.MainActivity'), 'Back did not leave Assistant');
  record('PASS Back, preserved pairing status, editable remote text and Assistant navigation');
}

async function speechTest() {
  const nodes = await startAssistant();
  const initiallyOn = !!find(nodes, 'Speak replies on');
  try {
    if (!initiallyOn) await click('Speak replies off');
    const enabled = await waitText(t => t === 'Speak replies on' || t.includes('No speech engine'));
    if (!find(enabled,'Speak replies on')) { record('BLOCKED no TTS engine; text remains available'); return; }
    await click('Preview voice');
    await waitText(t => t.includes('Local voice preview finished.'), 22);
    evidence('tts-engine-completion');
    record('PASS TTS engine completed the local preview. Audible output requires viewer confirmation.');
    // Use the established button geometry; dumping an accessibility tree during a short sample can outlast it.
    const ready = ui(), stop = find(ready,'Stop speaking');
    tap(find(ready,'Preview voice'));
    await pause(120);
    tap({...stop, enabled:'true'});
    await waitText(t => t.includes('Speech stopped.'));
    record('PASS explicit stop of spoken preview');
  } finally {
    if (current().endsWith('.assistant.AssistantActivity') && !initiallyOn && find(ui(),'Speak replies on')) await click('Speak replies on');
  }
}

async function recognizerTest() {
  const nodes = await startAssistant(), mic = find(nodes,'Push to talk');
  if (mic.enabled !== 'true') {
    assert.ok(nodes.some(n => n.text.includes('No speech input service')));
    record('BLOCKED Android has no recognition activity. Editable input remains available.');
    return;
  }
  const resolved = shell('cmd','package','resolve-activity','--brief','-a','android.speech.action.RECOGNIZE_SPEECH')
    .split(/\r?\n/).find(l => /^[\w.]+\/[\w.$]+$/.test(l.trim()))?.trim();
  assert.ok(resolved, 'No resolved recognizer; not starting a session');
  const draft = nodes.find(n => n.class === 'android.widget.EditText').text;
  tap(mic);
  let foreign;
  for (let i = 0; i < 25; i++) {
    await pause(200);
    const activity = current();
    if (activity && !activity.startsWith(pkg + '/')) { foreign = activity; break; }
  }
  if (!foreign) {
    record('BLOCKED recognizer returned before its external window could be verified. No speech claim.');
    return;
  }
  assert.equal(foreign.split('/')[0], resolved.split('/')[0], 'Unexpected external window; input withheld');
  record('PASS handoff to the resolved Android recognition package');
  if (mode === '--recognizer-cancel') {
    assert.equal(current(),foreign, 'Recognizer changed before cancel');
    shell('input','keyevent','BACK');
    const back = await waitText(t => t.includes('Voice input canceled') || t.includes('No words'), 12);
    assert.equal(back.find(n => n.class === 'android.widget.EditText').text, draft, 'Cancellation changed the draft');
    record('PASS recognizer cancellation preserved editable draft');
  } else {
    const deadline = Date.now() + 60000;
    while (Date.now() < deadline && !current().endsWith('.assistant.AssistantActivity')) await pause(500);
    if (!current().endsWith('.assistant.AssistantActivity')) { record('BLOCKED recognition result requires viewer interaction.'); return; }
    const after = ui();
    const text = after.find(n => n.class === 'android.widget.EditText')?.text;
    if (after.some(n => n.text.includes('Transcription received')) && text && text !== draft) {
      record('PASS recognizer returned an editable draft of ' + text.length + ' characters; no send action taken.');
    } else record('BLOCKED no new recognized transcript observed. Text fallback remains available.');
  }
}
try {
  if (mode === 'text') await textTest();
  else if (mode === '--tts-preview') await speechTest();
  else await recognizerTest();
  assert.equal(home(),beforeHome,'Default HOME changed');
} catch (e) {
  record('FAIL ' + e.message);
  process.exitCode = 1;
} finally {
  writeFileSync(path.join(out,'result.txt'),log.join('\n') + '\n');
  console.log('Private evidence:',out);
}

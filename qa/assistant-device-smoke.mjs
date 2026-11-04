// Real-device checks. Default mode sends only two built-in local commands; no provider request.
import assert from 'node:assert/strict';
import {execFileSync, spawn} from 'node:child_process';
import {existsSync, mkdirSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {createInterface} from 'node:readline';
import {activityList,recognitionWindow} from './recognizer-routing.mjs';

const [serial, mode = 'text'] = process.argv.slice(2);
const modes = ['text', '--tts-preview', '--recognizer-cancel', '--recognizer-dictate', '--gateway-fixture', '--offline-home'];
if (!serial || !modes.includes(mode) || process.argv.length > 4) {
  throw Error('Usage: node qa/assistant-device-smoke.mjs ADB_SERIAL [--tts-preview|--recognizer-cancel|--recognizer-dictate|--gateway-fixture|--offline-home]');
}
const pkg = 'tv.calmhome.launcher';
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
    const rect = (n.bounds || '').match(/-?\d+/g)?.map(Number) || [];
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
async function closeKeyboard() {
  if (/mInputShown=true/.test(shell('dumpsys','input_method'))) {
    localWindow();
    shell('input','keyevent','BACK');
    await pause(400);
  }
}
let capturedEntry = false;
async function startAssistant() {
  assert.match(shell('am','start','-W','-n',pkg + '/.MainActivity','--es','zp_open','home'), /Status: ok/);
  await pause(350);
  await click('Assistant');
  await pause(500);
  if (!capturedEntry) {
    assert.ok(current().endsWith('.assistant.AssistantActivity'), 'Unexpected app after Assistant navigation');
    evidence('assistant-entry');
    record('Clean entry via Home navigation; Android keyboard reported ' +
      (/mInputShown=true/.test(shell('dumpsys','input_method')) ? 'shown' : 'hidden'));
  }
  await closeKeyboard();
  const nodes = await waitText(t => t === 'Conversation');
  if (!capturedEntry) { evidence('assistant-dashboard'); capturedEntry = true; }
  assert.ok(current().endsWith('.assistant.AssistantActivity'), 'Assistant did not open');
  assert.ok(nodes.find(n => n.class === 'android.widget.EditText') && find(nodes,'Send'), 'Text fallback unavailable');
  assert.ok(find(nodes,'Push to talk') && find(nodes,'Stop response'), 'Missing mic or reply control');
  return nodes;
}
function pairing(nodes) {
  const states = ['Not paired','Paired','Revoked by PC','Pairing denied','Certificate changed'];
  const found = states.find(s => nodes.some(n => n.text === s));
  assert.ok(found, 'No visible pairing state');
  return found;
}
function statuses(nodes) {
  const row = label => {
    const i = nodes.findIndex(n => n.text === label);
    return i >= 0 ? nodes.slice(i + 1).find(n => n.class === 'android.widget.TextView' && n.text)?.text : '';
  };
  const link = row('PC gateway'), provider = row('Claude Code on PC');
  assert.ok(['Online · pinned TLS','Connecting…','Offline','Off','Access denied','Refused (pin mismatch)'].includes(link),
    'Gateway state unavailable on screen');
  assert.ok(['Requires PC link','Not reported','Configured on PC','Unavailable','Rate limit reached',
    'Subscription relay disabled','Available'].includes(provider), 'Provider state unavailable on screen');
  record('Observed gateway: ' + link + '; provider: ' + provider);
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
  statuses(nodes);
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
    await closeKeyboard();
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
    if (current().endsWith('.assistant.AssistantActivity') && !initiallyOn && find(ui(),'Speak replies on')) {
      await closeKeyboard();
      await click('Speak replies on');
      assert.ok(find(ui(),'Speak replies off'), 'Spoken replies preference was not restored');
    }
  }
}

async function recognizerTest() {
  const nodes = await startAssistant(), mic = find(nodes,'Push to talk');
  if (mic.enabled !== 'true') {
    assert.ok(nodes.some(n => n.text.includes('No speech input service')));
    record('BLOCKED Android has no recognition activity. Editable input remains available.');
    return;
  }
  const action = 'android.speech.action.RECOGNIZE_SPEECH';
  const candidates = activityList(shell('cmd','package','query-activities','--brief','-a',action));
  if (!candidates.some(name => recognitionWindow(name,candidates) === 'recognizer')) {
    record('BLOCKED no concrete recognition handler resolved; no external activity opened.');
    return;
  }
  const draft = nodes.find(n => n.class === 'android.widget.EditText').text;
  tap(mic);
  let foreign, sawChooser = false;
  const expires = Date.now() + 45000;
  while (Date.now() < expires) {
    await pause(200);
    const activity = current();
    if (!activity || activity.startsWith(pkg + '/')) {
      if (sawChooser && activity) break;
      continue;
    }
    const kind = recognitionWindow(activity,candidates);
    if (kind === 'recognizer') { foreign = activity; break; }
    if (kind === 'chooser') {
      if (!sawChooser) record('Android showed a chooser. Waiting for the viewer to select a speech service.');
      sawChooser = true;
      continue;
    }
    record('BLOCKED Android opened a different external window. No input sent.');
    return;
  }
  if (!foreign) {
    record(sawChooser ? 'BLOCKED no selected recognizer observed after the Android chooser. No Back sent.'
      : 'BLOCKED no concrete recognizer window observed. No speech or cancellation claim.');
    return;
  }
  record('PASS handoff to an installed recognition handler: ' + foreign);
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

function requireEmulator() {
  assert.match(serial, /^emulator-\d+$/, 'Disposable emulator only');
  assert.equal(shell('getprop','ro.kernel.qemu'), '1', 'Refusing a physical device');
}
async function offlineHome() {
  requireEmulator();
  const connection = (...args) => shell('cmd','connectivity',...args);
  const chain = connection('get-chain3-enabled'), rule = connection('get-package-networking-enabled',pkg);
  assert.match(chain, /^chain:(enabled|disabled)$/);
  assert.match(rule, /^tv\.calmhome\.launcher:(allow|deny)$/);
  try {
    connection('set-package-networking-enabled','false',pkg);
    connection('set-chain3-enabled','true');
    assert.equal(connection('get-package-networking-enabled',pkg), pkg + ':deny');
    assert.equal(connection('get-chain3-enabled'), 'chain:enabled');
    shell('am','force-stop',pkg);
    const launch = shell('am','start','-W','-n',pkg + '/.MainActivity','--es','zp_open','home');
    assert.match(launch,/Status: ok/);
    const nodes = await waitText(t => t === 'All Apps');
    const tiles = nodes.filter(n => n['long-clickable'] === 'true' && n['content-desc'] && n.clickable === 'true');
    assert.ok(tiles.length > 0, 'No installed app tile visible in Home dock');
    evidence('home-package-network-denied');
    const ms = (launch.match(/TotalTime: (\d+)/) || [])[1];
    record('PASS cold Home with launcher network access denied, ' + tiles.length + ' dock tiles visible' +
      (ms ? ', Android display time ' + ms + ' ms' : '') + '. Emulator Ethernet remained connected.');
  } finally {
    connection('set-package-networking-enabled',rule.endsWith(':allow') ? 'true' : 'false',pkg);
    connection('set-chain3-enabled',chain.endsWith(':enabled') ? 'true' : 'false');
    assert.equal(connection('get-package-networking-enabled',pkg),rule,'Package network rule was not restored');
    assert.equal(connection('get-chain3-enabled'),chain,'Test firewall state was not restored');
  }
}
async function gatewayTest() {
  requireEmulator();
  const dir = process.env.ZEROPULSE_TEST_GATEWAY_DIR;
  assert.ok(dir && path.isAbsolute(dir), 'Set ZEROPULSE_TEST_GATEWAY_DIR to the local OpenPulse packages/gateway directory');
  assert.ok(existsSync(path.join(dir,'test/tvlink-harness.ts')), 'Missing disposable gateway fixture');
  const first = await startAssistant();
  assert.equal(pairing(first), 'Not paired', 'Preserve an existing device pairing');
  const harness = spawn(process.execPath, ['--import','tsx','test/tvlink-harness.ts'], {
    cwd:dir, env:{SystemRoot:process.env.SystemRoot, TEMP:process.env.TEMP, TMP:process.env.TMP, PATH:process.env.PATH},
    windowsHide:true, stdio:['pipe','pipe','inherit']
  });
  const closed = new Promise(resolve => harness.once('close', resolve));
  harness.on('error', () => record('FAIL could not start disposable gateway'));
  harness.stdin.on('error', () => {}); // A failed fixture can close its stdin early.
  const lines = createInterface({input:harness.stdout})[Symbol.asyncIterator]();
  const receive = async () => {
    let timer;
    try {
      const item = await Promise.race([lines.next(), new Promise((_, reject) => {
        timer = setTimeout(() => reject(Error('Fixture timed out')), 20000);
      })]);
      assert.ok(!item.done, 'Fixture exited early');
      const data = JSON.parse(item.value);
      assert.ok(!data.error, 'Fixture command failed');
      return data;
    } finally { clearTimeout(timer); }
  };
  const command = cmd => { harness.stdin.write(cmd + '\n'); return receive(); };
  async function enter(node, text, clear = false) {
    tap(node);
    // The address, port and code fields belong to this disposable pairing form.
    if (clear) { shell('input','keyevent','KEYCODE_MOVE_END'); for (let i = 0; i < node.text.length; i++) shell('input','keyevent','DEL'); }
    shell('input','text',text.replaceAll(' ','%s'));
    if (/mInputShown=true/.test(shell('dumpsys','input_method'))) { localWindow(); shell('input','keyevent','BACK'); }
  }
  try {
    const ready = await receive();
    assert.ok(ready.ready && ready.code && ready.port, 'Gateway did not return an invitation');
    await click('Connect your PC');
    const fields = () => ui().filter(n => n.class === 'android.widget.EditText').slice(-3);
    assert.equal(fields().length, 3, 'Unexpected pairing form');
    await enter(fields()[0], '10.0.2.2', /^\d/.test(fields()[0].text));
    await enter(fields()[1], String(ready.port), true);
    await enter(fields()[2], ready.code);
    await click('Connect');
    const pending = await waitText(t => t.includes('Approve this TV on your PC'), 20);
    assert.equal((await command('pending')).pending, 1);
    const approved = await command('approve');
    assert.ok(approved.ok && pending.some(n => n.text.replaceAll('-','').includes(approved.tvCode)),
      'Fixture and TV verification codes differ');
    const online = await waitText(t => t === 'Online · pinned TLS', 20);
    assert.equal(pairing(online), 'Paired');
    statuses(online);
    record('PASS matching device verification codes, owner fixture approval and authenticated pinned TLS');
    await enter(ui().find(n => n.class === 'android.widget.EditText'), 'Status report');
    await click('Send');
    await waitText(t => t.includes('Good evening. All systems nominal.'), 20);
    evidence('scripted-gateway-chat');
    record('PASS actual TV conversation with the isolated scripted model; no provider request');
    assert.ok((await command('stop')).ok);
    const offline = await waitText(t => t === 'Offline', 20);
    assert.equal(pairing(offline), 'Paired');
    assert.ok(offline.some(n => n.text.includes('All systems nominal.')), 'Local transcript lost on disconnect');
    evidence('gateway-offline');
    record('PASS transport offline with saved pair and transcript retained');
    assert.ok((await command('start')).ok);
    await waitText(t => t === 'Online · pinned TLS', 32);
    record('PASS emulator reconnected with its saved Keystore identity');
    assert.ok((await command('revoke')).ok);
    await waitText(t => t === 'Revoked by PC' || t === 'Not paired', 15);
    assert.equal((await command('devices')).devices, 0);
    record('PASS disposable device revoked on PC');
  } finally {
    if (harness.exitCode === null && harness.stdin.writable) harness.stdin.end('exit\n');
    const deadline = setTimeout(() => harness.kill(), 15000);
    try { await closed; } finally { clearTimeout(deadline); }
  }
}
try {
  if (mode === 'text') await textTest();
  else if (mode === '--gateway-fixture') await gatewayTest();
  else if (mode === '--offline-home') await offlineHome();
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

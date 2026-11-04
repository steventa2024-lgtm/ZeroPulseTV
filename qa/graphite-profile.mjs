// Read a comparable focus workload on an explicitly selected test target. Leaves the launcher on Home.
import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {mkdirSync, writeFileSync} from 'node:fs';
import path from 'node:path';

const [serial, output] = process.argv.slice(2);
if (!serial || !output) throw Error('Usage: node qa/graphite-profile.mjs SERIAL PRIVATE_OUTPUT_FOLDER');
const pkg = 'tv.calmhome.launcher';
const adb = (...args) => execFileSync(process.env.ADB || 'adb', ['-s', serial, ...args], {timeout: 60000, maxBuffer: 24*1024*1024});
const shell = (...args) => adb('shell', ...args).toString();
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
mkdirSync(output, {recursive:true});
const save = (file, data) => writeFileSync(path.join(output,file),data);
const version = shell('dumpsys','package',pkg);
assert.match(version,/versionCode=3002\b/);
assert.match(version,/versionName=3\.0\.0-beta\.2\b/);
const home = () => shell('cmd','package','resolve-activity','--brief','-a','android.intent.action.MAIN','-c','android.intent.category.HOME').trim();
const originalHome = home();
async function open(section) {
    assert.match(shell('am','start','-W','-n',pkg+'/.MainActivity','--es','zp_open',section), /Status: ok/);
    await sleep(500);
}
function checkWindow() {
    assert.match(shell('dumpsys','window'), /mCurrentFocus=[^\n]*tv\.calmhome\.launcher\//);
}
function focused() {
    const dump = adb('exec-out', 'uiautomator', 'dump', '/dev/tty').toString();
    return [...dump.matchAll(/<node\s+[^>]*>/g)].map(x=>x[0]).find(x=>x.includes('focused="true"')) || '';
}
try {
    await open('downloads');
    checkWindow();
    if (!focused()) {shell('input','keyevent','DPAD_DOWN'); await sleep(450);}
    // Reopening from D-pad mode makes the first category the starting position.
    await open('downloads');
    assert.match(focused(), /Apps, browse sources/);
    await sleep(2000);
    shell('dumpsys','gfxinfo',pkg,'reset');
    const start = Date.now(), times = [];
    for (let i=0; i<20; i++) {
        await sleep(Math.max(0,start+i*225-Date.now()));
        times.push(Date.now()-start);
        shell('input','keyevent',i%2 ? 'DPAD_LEFT' : 'DPAD_RIGHT');
    }
    await sleep(750);
    save('focus-gfx.txt',shell('dumpsys','gfxinfo',pkg));
    save('focus-mem.txt',shell('dumpsys','meminfo',pkg));
    assert.match(focused(), /Apps, browse sources/);
    save('focus.png',adb('exec-out','screencap','-p'));
    save('key-offsets-ms.txt',times.join(', ')+'\n');
    for (let cycle=0; cycle<3; cycle++) {
        for (const section of ['apps','inputs','settings','downloads']) await open(section);
        save('cycle-'+(cycle+1)+'-mem.txt',shell('dumpsys','meminfo',pkg));
    }
    console.log('PASS 20 alternating focus keys, three navigation cycles');
} finally {
    await open('home');
    assert.equal(home(),originalHome,'HOME component changed');
}

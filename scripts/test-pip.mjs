import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';

// Read the actual injected Java strings, including their shared selection helper.
const source = fs.readFileSync('app/src/main/java/com/lumen/browser/Scripts.java', 'utf8');
const scripts = {};
for (const match of source.matchAll(/static final String (\w+) = /g)) {
  let i = match.index + match[0].length, end = i, quoted = false, escaped = false;
  for (; end < source.length; end++) {
    const c = source[end];
    if (escaped) { escaped = false; continue; }
    if (quoted && c === '\\') { escaped = true; continue; }
    if (c === '"') quoted = !quoted;
    if (!quoted && c === ';') break;
  }
  let expr = source.slice(i, end).trim();
  if (expr.startsWith('R(')) expr = expr.slice(2, -1);
  const parts = expr.match(/"(?:[^"\\]|\\.)*"|\bPIP_SELECT\b/g);
  if (!parts || expr.includes('randomKey()')) continue;
  scripts[match[1]] = parts.map(p => p === 'PIP_SELECT' ? scripts.PIP_SELECT : JSON.parse(p)).join('');
}
for (const [name, script] of Object.entries(scripts)) if (name.endsWith('_JS')) new vm.Script(script, { filename: name });

function page() {
  let now = 100000;
  const listeners = new Map(), timers = [], styles = new Map(), reports = [];
  const classList = () => { const set = new Set(); return { set, add: (...xs) => xs.forEach(x => set.add(x)), remove: (...xs) => xs.forEach(x => set.delete(x)) }; };
  const html = { classList: classList() };
  const body = { parentElement: html, classList: classList(), appendChild: node => styles.set(node.id, node) };
  const videos = [];
  class Document { hasFocus() { return false; } }
  Object.defineProperty(Document.prototype, 'hidden', { configurable: true, get: () => true });
  Object.defineProperty(Document.prototype, 'visibilityState', { configurable: true, get: () => 'hidden' });
  const document = new Document();
  Object.assign(document, {
    documentElement: html, body, head: body,
    querySelectorAll: selector => selector === 'video' || selector === 'video,audio' ? videos : [...videos, body],
    getElementById: id => styles.get(id),
    createElement: () => ({ remove() { styles.delete(this.id); } }),
    addEventListener: (name, fn) => { if (!listeners.has(name)) listeners.set(name, []); listeners.get(name).push(fn); }
  });
  function emit(name, target) { for (const fn of listeners.get(name) || []) fn({ target }); }
  const context = vm.createContext({ document, Document, Date: { now: () => now },
    setInterval: () => 1, setTimeout: (fn, delay) => timers.push({ fn, at: now + delay }),
    LumenBridge: { media: (...args) => reports.push(args) },
    addEventListener: () => {}, __lasurKeep: true });
  context.window = context; context.top = context;
  return {
    context, reports,
    run: name => vm.runInContext(scripts[name], context),
    advance(ms) { now += ms; for (let i = 0; i < timers.length;) { if (timers[i].at <= now) { const [t] = timers.splice(i, 1); t.fn(); } else i++; } },
    video(paused, width = 640, height = 360) {
      const v = { paused, videoWidth: width, videoHeight: height, currentTime: 20, ended: false, readyState: 4,
        isConnected: true, parentElement: body, classList: classList(), plays: 0,
        getBoundingClientRect: () => ({ width, height }),
        play() { this.plays++; this.paused = false; emit('playing', this); return Promise.resolve(); },
        pause() { this.paused = true; emit('pause', this); }
      };
      videos.push(v); return v;
    }
  };
}
let checked = 0;
function test(name, fn) { fn(); checked++; console.log(`PASS ${name}`); }
test('pause survives repeated PiP preparation and fullscreen changes', () => {
  const p = page(), video = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS'); p.run('PIP_TOGGLE_JS');
  p.run('PIP_ON_JS'); p.run('PIP_FS_JS'); p.advance(100);
  assert.equal(video.paused, true); assert.equal(video.plays, 0);
});
test('resume controls the same selected video even if another video starts', () => {
  const p = page(), video = p.video(false), ad = p.video(true, 1920, 1080);
  p.run('PIP_ON_JS'); p.run('PIP_TOGGLE_JS'); ad.play(); p.run('PIP_TOGGLE_JS');
  assert.equal(video.paused, false); assert.equal(ad.paused, false); assert.equal(video.plays, 1);
});
test('fullscreen PiP does not start every paused video on a page', () => {
  const p = page(), playing = p.video(false), other = p.video(true, 1920, 1080);
  p.context.__lasurLastPlay = 100000; p.run('PIP_FS_JS');
  assert.equal(other.plays, 0); assert.equal(playing.plays, 0);
});
test('closing PiP cancels queued transition recovery and restores styles', () => {
  const p = page(), video = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS'); video.pause();
  p.run('PIP_OFF_JS'); p.run('PAUSE_JS'); p.advance(100);
  assert.equal(video.plays, 0); assert.equal(video.paused, true); assert.equal(video.classList.set.size, 0);
});
test('transition recovery is bounded and never loops during a long playback', () => {
  const p = page(), video = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  video.pause(); p.advance(100); assert.equal(video.plays, 1);
  p.advance(2000); video.pause(); p.advance(100); assert.equal(video.plays, 1);
});
console.log(`${checked} PiP behavior checks and all injected script syntax checks passed.`);

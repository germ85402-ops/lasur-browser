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
  function emit(name, target, isTrusted = false) { const e = { target, isTrusted, stopped: false, stopImmediatePropagation() { this.stopped = true; } }; for (const fn of listeners.get(name) || []) { fn(e); if (e.stopped) break; } }
  const intervals = [];
  const location = { hostname: 'm.youtube.com', pathname: '/watch', search: '?v=test', href: 'https://m.youtube.com/watch?v=test' };
  const context = vm.createContext({ document, Document, location, URLSearchParams,
    innerWidth: 1080, innerHeight: 1920, getComputedStyle: v => ({ display: v.display || 'block', visibility: 'visible' }), Date: { now: () => now },
    setInterval: fn => { intervals.push(fn); return intervals.length; }, setTimeout: (fn, delay) => timers.push({ fn, at: now + delay }),
    LumenBridge: { media: (...args) => reports.push(args) },
    addEventListener: () => {}, __lasurKeep: true });
  context.window = context; context.top = context;
  return {
    context, reports, emit,
    run: name => vm.runInContext(scripts[name], context),
    advance(ms) { now += ms; for (const fn of intervals) fn(); for (let i = 0; i < timers.length;) { if (timers[i].at <= now) { const [t] = timers.splice(i, 1); t.fn(); } else i++; } },
    video(paused, width = 640, height = 360) {
      const v = { paused, videoWidth: width, videoHeight: height, currentTime: 20, ended: false, readyState: 4,
        isConnected: true, parentElement: body, classList: classList(), plays: 0,
        muted: false, controls: true, preview: false, closest() { return this.preview ? body : null; },
        getBoundingClientRect() { return { width, height, top: this.top || 0, left: 0, right: width, bottom: (this.top || 0) + height }; },
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
  p.run('PIP_ON_JS'); p.run('PIP_FS_JS'); p.advance(150);
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
  p.run('PIP_OFF_JS'); p.run('PAUSE_JS'); p.advance(150);
  assert.equal(video.plays, 0); assert.equal(video.paused, true); assert.equal(video.classList.set.size, 0);
});
test('transition recovery is bounded and never loops during a long playback', () => {
  const p = page(), video = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  video.pause(); p.advance(150); assert.equal(video.plays, 1);
  p.advance(6000); video.pause(); p.advance(150); assert.equal(video.plays, 1);
});
test('YouTube feed previews never qualify or get PiP styles', () => {
  const p = page(), preview = p.video(false); preview.muted = true;
  p.context.location.pathname = '/'; p.context.location.search = ''; p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  assert.equal(p.reports.at(-1)[3], 0); assert.equal(p.context.__lasurPip, undefined); assert.equal(preview.classList.set.size, 0);
});
test('related preview cannot replace the main watch player', () => {
  const p = page(), main = p.video(false), preview = p.video(false, 1920, 1080); preview.preview = true;
  p.run('PIP_ON_JS'); assert.equal(p.context.__lasurPipVideo, main);
});
test('SPA return to feed disables automatic entry without a media event', () => {
  const p = page(); p.video(false); p.run('MEDIA_JS'); assert.equal(p.reports.at(-1)[3], 1);
  p.context.location.pathname = '/feed/subscriptions'; p.advance(250);
  assert.equal(p.reports.at(-1)[0], 0); assert.equal(p.reports.at(-1)[3], 0);
});
test('visibility protection lasts throughout playback, not just its first seconds', () => {
  const p = page(); p.video(false); p.run('MEDIA_JS'); p.advance(30000);
  assert.equal(p.context.document.hidden, false); assert.equal(p.context.document.visibilityState, 'visible');
});
test('late YouTube transition pause recovers after surface resize', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.advance(30000); p.run('PIP_ON_JS');
  p.advance(3000); v.pause(); p.advance(150); assert.equal(v.paused, false); assert.equal(v.plays, 1);
});
test('trusted player interaction prevents recovery from overriding user pause', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  p.emit('pointerdown', v, true); v.pause(); p.advance(150); assert.equal(v.paused, true); assert.equal(v.plays, 0);
});
test('hidden players and generic muted previews are excluded', () => {
  const p = page(), v = p.video(false); v.top = 2000; p.run('MEDIA_JS'); assert.equal(p.reports.at(-1)[3], 0);
  v.top = 0; p.context.location.hostname = 'example.org'; v.muted = true; v.controls = false; p.advance(250);
  assert.equal(p.reports.at(-1)[3], 0);
});
test('paused watch video does not report playing, muted watch and Shorts still qualify', () => {
  const p = page(), v = p.video(true); v.muted = true; p.run('MEDIA_JS');
  assert.equal(p.reports.at(-1)[0], 0); assert.equal(p.reports.at(-1)[3], 1);
  p.context.location.pathname = '/shorts/test'; v.play(); assert.equal(p.reports.at(-1)[0], 1);
});
test('early visibility listener shields the watch player but leaves the feed alone', () => {
  const p = page(); p.video(false); p.run('MEDIA_JS'); let sitePauses = 0;
  p.context.document.addEventListener('visibilitychange', () => sitePauses++);
  p.advance(30000); p.emit('visibilitychange', p.context.document); assert.equal(sitePauses, 0);
  p.context.location.pathname = '/'; p.emit('visibilitychange', p.context.document); assert.equal(sitePauses, 1);
});
test('repeated transition pause recovery stops after four attempts', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  for (let i = 0; i < 6; i++) { v.pause(); p.advance(150); }
  assert.equal(v.plays, 4); assert.equal(v.paused, true);
});
console.log(`${checked} PiP behavior checks and all injected script syntax checks passed.`);

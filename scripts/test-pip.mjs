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
  class HTMLMediaElement { pause() { this.nativePause(); } }
  class MediaSession { setActionHandler(action, handler) { this.handlers.set(action, handler); } constructor() { this.handlers = new Map(); } }
  const mediaSession = new MediaSession();
  const context = vm.createContext({ document, Document, location, URLSearchParams, HTMLMediaElement, MediaSession, navigator: { mediaSession },
    innerWidth: 1080, innerHeight: 1920, getComputedStyle: v => ({ display: v.display || 'block', visibility: 'visible' }), Date: { now: () => now },
    setInterval: fn => { intervals.push(fn); return intervals.length; }, setTimeout: (fn, delay) => timers.push({ fn, at: now + delay }),
    LumenBridge: { media: (...args) => reports.push(args) },
    addEventListener: () => {}, __lasurKeep: true, __lasurPipBackground: true });
  context.window = context; context.top = context;
  return {
    context, reports, emit,
    run: name => vm.runInContext(scripts[name], context),
    advance(ms) { now += ms; for (const fn of intervals) fn(); for (let i = 0; i < timers.length;) { if (timers[i].at <= now) { const [t] = timers.splice(i, 1); t.fn(); } else i++; } },
    video(paused, width = 640, height = 360) {
      const v = Object.assign(new HTMLMediaElement(), { paused, videoWidth: width, videoHeight: height, currentTime: 20, ended: false, readyState: 4,
        isConnected: true, parentElement: body, classList: classList(), plays: 0,
        muted: false, controls: true, preview: false, closest() { return this.preview ? body : null; },
        getBoundingClientRect() { return { width, height, top: this.top || 0, left: 0, right: width, bottom: (this.top || 0) + height }; },
        play() { this.plays++; this.paused = false; emit('playing', this); return Promise.resolve(); },
        nativePause() { this.paused = true; emit('pause', this); }
      });
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
  const p = page(), video = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS'); video.nativePause();
  p.run('PIP_OFF_JS'); p.run('PAUSE_JS'); p.advance(150);
  assert.equal(video.plays, 0); assert.equal(video.paused, true); assert.equal(video.classList.set.size, 0);
});
test('transition recovery is bounded and never loops during a long playback', () => {
  const p = page(), video = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  video.nativePause(); p.advance(150); assert.equal(video.plays, 1);
  p.advance(6000); video.nativePause(); p.advance(150); assert.equal(video.plays, 1);
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
  p.advance(3000); v.nativePause(); p.advance(150); assert.equal(v.paused, false); assert.equal(v.plays, 1);
});
test('trusted player interaction prevents recovery from overriding user pause', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  p.emit('pointerdown', v, true); v.nativePause(); p.advance(150); assert.equal(v.paused, true); assert.equal(v.plays, 0);
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
  for (let i = 0; i < 6; i++) { v.nativePause(); p.advance(150); }
  assert.equal(v.plays, 4); assert.equal(v.paused, true);
});
test('YouTube repeated script pauses are ignored for minutes without a resume loop', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS');
  const sitePause = p.context.HTMLMediaElement.prototype.pause; p.run('PIP_ON_JS');
  for (let i = 0; i < 100; i++) { p.advance(2000); sitePause.call(v); }
  assert.equal(v.paused, false); assert.equal(v.plays, 0);
});
test('manual PiP pause and repeated resume remain usable after the transition timeout', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS'); p.advance(30000);
  for (let i = 0; i < 4; i++) {
    p.run('PIP_TOGGLE_JS'); assert.equal(v.paused, true); p.advance(1000); assert.equal(v.paused, true);
    p.run('PIP_TOGGLE_JS'); v.pause(); assert.equal(v.paused, false);
  }
  assert.equal(v.plays, 4);
});
test('pause guard affects only the pinned YouTube player inside PiP', () => {
  const p = page(), v = p.video(false), other = p.video(false);
  p.run('MEDIA_JS'); v.pause(); assert.equal(v.paused, true); v.play();
  p.run('PIP_ON_JS'); other.pause(); assert.equal(other.paused, true);
  p.context.location.hostname = 'example.org'; v.pause(); assert.equal(v.paused, true);
});
test('closing PiP, navigating to the feed and disabling PiP release the pause guard', () => {
  for (const release of ['close', 'navigate', 'disable']) {
    const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
    if (release === 'close') p.run('PIP_OFF_JS');
    if (release === 'navigate') p.context.location.pathname = '/';
    if (release === 'disable') p.context.__lasurKeep = false;
    v.pause(); p.advance(150); assert.equal(v.paused, true);
  }
});
test('system media controls preserve user pause and resume intent', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS');
  const session = p.context.navigator.mediaSession;
  session.setActionHandler('pause', () => v.pause()); session.setActionHandler('play', () => v.play());
  p.run('PIP_ON_JS'); p.advance(30000);
  session.handlers.get('pause')({ action: 'pause' }); p.advance(150); assert.equal(v.paused, true);
  session.handlers.get('play')({ action: 'play' }); v.pause(); assert.equal(v.paused, false);
});
test('foreground return releases site pause even before PiP mode callback finishes', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS'); p.advance(2000);
  p.context.__lasurPipBackground = false; v.pause(); p.advance(150); assert.equal(v.paused, true);
});
test('native PiP pause bypasses a page override and remains paused after preparation', () => {
  const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
  v.pause = () => {}; p.run('PIP_TOGGLE_JS'); p.run('PIP_ON_JS'); p.advance(150);
  assert.equal(v.paused, true); assert.equal(v.plays, 0); assert.equal(p.reports.at(-1)[0], 0);
});
test('trusted click and touch controls can pause the YouTube player in PiP', () => {
  for (const event of ['click', 'touchstart']) {
    const p = page(), v = p.video(false); p.run('MEDIA_JS'); p.run('PIP_ON_JS');
    p.emit(event, v, true); v.pause(); p.advance(150); assert.equal(v.paused, true);
  }
});
test('return to fullscreen preserves both playing and paused video without restarting it', () => {
  for (const paused of [false, true]) {
    const p = page(), v = p.video(paused); p.run('PIP_REMEMBER_FULLSCREEN_JS'); p.run('PIP_ON_JS');
    p.run('PIP_OFF_JS'); assert.equal(p.run('PIP_RESTORE_FULLSCREEN_JS'), true);
    assert.equal(v.classList.set.has('__lasurFullscreenPlayer'), true);
    assert.equal(v.paused, paused); assert.equal(v.plays, 0);
    v.pause(); assert.equal(v.paused, true);
    p.run('FULLSCREEN_OFF_JS'); assert.equal(v.classList.set.has('__lasurFullscreenPlayer'), false);
  }
});
test('fullscreen return promotes the whole YouTube player so its controls remain present', () => {
  const p = page(), v = p.video(false), player = { parentElement: v.parentElement, classList: v.parentElement.classList };
  v.closest = selector => selector.includes('ytd-video-preview') ? null : player; p.run('PIP_REMEMBER_FULLSCREEN_JS'); p.run('PIP_OFF_JS'); p.run('PIP_RESTORE_FULLSCREEN_JS');
  assert.equal(player.classList.set.has('__lasurFullscreenPlayer'), true);
  assert.equal(v.classList.set.has('__lasurFullscreenPlayer'), false);
  assert.equal(v.plays, 0);
});
test('generic fullscreen restore enables controls temporarily without changing pause', () => {
  const p = page(), v = p.video(true); v.controls = false;
  p.run('PIP_REMEMBER_FULLSCREEN_JS'); p.run('PIP_RESTORE_FULLSCREEN_JS'); assert.equal(v.controls, true);
  p.run('FULLSCREEN_OFF_JS'); assert.equal(v.controls, false); assert.equal(v.paused, true);
});
console.log(`${checked} PiP behavior checks and all injected script syntax checks passed.`);

package com.lumen.browser;

import java.security.SecureRandom;

/**
 * JavaScript injected into pages.
 *
 * All window flags / helper names start with "__lumen" or "__lasur". They are rewritten at class-load time
 * with a per-process random suffix, so a page cannot pre-define them to switch our scripts off
 * (e.g. window.__lumenCss = 1 to disable cosmetic filtering) or to hijack helpers.
 */
final class Scripts {
    private Scripts() { }

    static final String KEY = randomKey();

    private static String randomKey() {
        byte[] b = new byte[6];
        new SecureRandom().nextBytes(b);
        StringBuilder sb = new StringBuilder("_");
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 15, 16)).append(Character.forDigit(x & 15, 16));
        return sb.toString();
    }

    /** Applies the per-process suffix to every private identifier in a script. */
    static String R(String js) { return js.replace("__lumen", "__lumen" + KEY).replace("__lasur", "__lasur" + KEY); }

    static final String COSMETIC_JS = R("(function(){try{if(window.__lumenCss)return;window.__lumenCss=1;var c=LumenBridge.css(location.hostname);if(!c)return;"
            + "try{var s=new CSSStyleSheet();s.replaceSync(c);document.adoptedStyleSheets=document.adoptedStyleSheets.concat([s]);}"
            + "catch(e){var st=document.createElement('style');st.textContent=c;(document.head||document.documentElement).appendChild(st);}}catch(e){}})();");

    static final String VIDEO_JS = R("(function(){try{if(window.__lumenScan)return;window.__lumenScan=1;"
            + "function sc(){try{var v=document.querySelectorAll('video,video source,audio source');for(var i=0;i<v.length;i++){"
            + "var s=v[i].currentSrc||v[i].src;if(s&&s.indexOf('http')==0)LumenBridge.onVideo(s,document.title);}}catch(e){}}"
            + "sc();setInterval(sc,2500);document.addEventListener('play',sc,true);document.addEventListener('loadedmetadata',sc,true);}catch(e){}})();");

    static final String YT_JS = R("(function(){if(window.__lasurYtp)return;window.__lasurYtp=1;var K=['adPlacements','playerAds','adSlots','adBreakHeartbeatParams','adBreakParams'];func"
            + "tion pr(o,d){try{if(!o||typeof o!='object'||d>4)return o;for(var i=0;i<K.length;i++)if(K[i] in o)delete o[K[i]];if(o.playerResponse)pr(o.playerRespons"
            + "e,d+1);if(o.response)pr(o.response,d+1);if(o.playerConfig&&o.playerConfig.daiConfig)delete o.playerConfig.daiConfig;if(Array.isArray(o)){for(var j=0;j"
            + "<o.length;j++)pr(o[j],d+1)}}catch(e){}return o}function trap(n){try{var v=window[n];if(v)pr(v,0);Object.defineProperty(window,n,{configurable:true,get"
            + ":function(){return v},set:function(x){v=pr(x,0)}})}catch(e){}}try{var jp=JSON.parse;JSON.parse=function(){var r=jp.apply(this,arguments);try{if(r&&typ"
            + "eof r=='object'&&(r.adPlacements||r.playerAds||r.adSlots||r.playerResponse||(r.response&&typeof r.response=='object')))pr(r,0)}catch(e){}return r};var"
            + " rj=Response.prototype.json;Response.prototype.json=function(){return rj.apply(this,arguments).then(function(o){return pr(o,0)})}}catch(e){}trap('ytIn"
            + "itialPlayerResponse');trap('ytInitialData');try{var yp=window.ytplayer||{};if(yp.config&&yp.config.args&&yp.config.args.raw_player_response)pr(yp.conf"
            + "ig.args.raw_player_response,0)}catch(e){}function hit(u){u=String(u||'');return u.indexOf('/youtubei/v1/player')>=0||u.indexOf('/youtubei/v1/next')>=0"
            + "||u.indexOf('/youtubei/v1/reel')>=0||u.indexOf('/youtubei/v1/browse')>=0}try{var of=window.fetch;window.fetch=function(i,o){var u=typeof i=='string'?i"
            + ":(i&&i.url);var p=of.apply(this,arguments);if(!hit(u))return p;return p.then(function(r){if(!r||!r.ok)return r;return r.clone().text().then(function(t"
            + "){try{var j=JSON.parse(t);pr(j,0);return new Response(JSON.stringify(j),{status:r.status,statusText:r.statusText,headers:r.headers})}catch(e){return r"
            + "}},function(){return r})})}}catch(e){}try{var oo=XMLHttpRequest.prototype.open;XMLHttpRequest.prototype.open=function(m,u){this.__lu=u;return oo.apply"
            + "(this,arguments)};var gd=Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype,'responseText'),gr=Object.getOwnPropertyDescriptor(XMLHttpRequest.pr"
            + "ototype,'response');function fix(x,s){if(!hit(x.__lu)||typeof s!='string'||x.readyState!=4)return s;if(x.__lc!==undefined)return x.__lc;try{var j=JSON"
            + ".parse(s);pr(j,0);x.__lc=JSON.stringify(j)}catch(e){x.__lc=s}return x.__lc}Object.defineProperty(XMLHttpRequest.prototype,'responseText',{configurable"
            + ":true,get:function(){return fix(this,gd.get.call(this))}});Object.defineProperty(XMLHttpRequest.prototype,'response',{configurable:true,get:function()"
            + "{var r=gr.get.call(this);return (this.responseType==''||this.responseType=='text')?fix(this,r):r}});}catch(e){}try{var st=document.createElement('styl"
            + "e');st.textContent='.ytp-ad-module,.ytp-ad-overlay-container,.ytp-ad-player-overlay,.ytp-ad-text,.ytp-ad-preview-container,ytm-promoted-sparkles-web-r"
            + "enderer,ytm-companion-ad-renderer,ytd-ad-slot-renderer,ad-slot-renderer,ytm-ad-slot-renderer,.video-ads,#player-ads,ytm-promoted-video-renderer{displa"
            + "y:none!important}.ytp-ad-skip-button-container,.ytp-skip-ad,.ytm-skip-ad-button{opacity:0.01!important}.ad-showing video,.ad-interrupting video{opacit"
            + "y:0!important}';(document.head||document.documentElement).appendChild(st)}catch(e){}var lastTap=0;function skip(){try{var a=document.querySelector('.a"
            + "d-showing,.ad-interrupting');if(!a){if(window.__lasurAdV){var q=window.__lasurAdV;window.__lasurAdV=null;try{q.muted=!!window.__lasurAdWasMuted;if(q.p"
            + "laybackRate>2)q.playbackRate=1}catch(e){}}return}var p=a.querySelector('video');if(p){if(!window.__lasurAdV){window.__lasurAdV=p;window.__lasurAdWasMu"
            + "ted=p.muted}p.muted=true;try{if(p.playbackRate<16)p.playbackRate=16}catch(e){}if(isFinite(p.duration)&&p.duration>0&&p.currentTime<p.duration-0.05){tr"
            + "y{p.currentTime=p.duration}catch(e){}}if(p.paused)try{p.play()}catch(e){}}var bs=document.querySelectorAll('.ytp-ad-skip-button,.ytp-ad-skip-button-mo"
            + "dern,.ytp-skip-ad-button,.ytm-skip-ad-button button,.ytm-skip-ad-button,button[class*=skip-ad],[class*=ad-skip] button');for(var i=0;i<bs.length;i++){"
            + "var b=bs[i];b.click();var r=b.getBoundingClientRect();var now=Date.now();if(r.width>0&&r.height>0&&now-lastTap>700&&window.LumenBridge&&LumenBridge.ta"
            + "p){lastTap=now;var d=window.devicePixelRatio||1;LumenBridge.tap((r.left+r.width/2)*d,(r.top+r.height/2)*d)}break}}catch(e){}}['loadedmetadata','durati"
            + "onchange','timeupdate','playing','canplay'].forEach(function(n){document.addEventListener(n,function(e){if(e.target&&e.target.tagName=='VIDEO'&&e.targ"
            + "et.closest&&e.target.closest('.ad-showing,.ad-interrupting'))skip()},true)});setInterval(skip,150);})();");

    static final String PAUSE_JS = R("(function(){try{window.__lasurUserPause=1;document.querySelectorAll('video,audio').forEach(function(m){if(!m.paused){if(window.__lasurPause)window.__lasurPause(m);else m.pause()}})}catch(e){}})();");

    static final String SCROLL_JS = R("(function(){var e=document.scrollingElement||document.documentElement;return (window.scrollX||0)+','+(window.scrollY||e.scrollTop||0)})()");

    static final String PTR_JS = R("(function(){if(window.__lumenPtr)return;window.__lumenPtr=1;"
            + "function ok(e){try{if(e.touches.length>1)return 0;var se=document.scrollingElement||document.documentElement;"
            + "if((window.scrollY||0)>0||(se&&se.scrollTop>0))return 0;if(document.fullscreenElement||document.webkitFullscreenElement)return 0;"
            + "var vh=window.innerHeight||1;for(var n=e.target;n&&n.nodeType==1&&n!=document.body&&n!=document.documentElement;n=n.parentElement){"
            + "var tg=n.tagName;if(tg=='VIDEO'||tg=='CANVAS'||tg=='IFRAME'||tg=='EMBED'||tg=='OBJECT'||tg=='SELECT'||tg=='TEXTAREA'||(tg=='INPUT'&&n.type=='range'))return 0;"
            + "if(n.isContentEditable)return 0;if(n.scrollTop>0)return 0;var cs=getComputedStyle(n),ta=cs.touchAction;"
            + "if(ta=='none'||ta=='pan-x'||ta=='pinch-zoom'||ta=='pan-left'||ta=='pan-right')return 0;"
            + "if(n.getElementsByTagName('video').length&&n.getBoundingClientRect().height<vh*0.85)return 0;"
            + "var r=n.getAttribute('role');if(r=='slider'||r=='application')return 0;"
            + "var oy=cs.overflowY,sn=cs.scrollSnapType||'';if(sn.indexOf('y')>=0||sn.indexOf('block')>=0||sn.indexOf('both')>=0)return 0;"
            + "if((oy=='auto'||oy=='scroll'||oy=='overlay')&&n.scrollHeight>n.clientHeight+4)return 0;}"
            + "var t0=e.touches[0],vs=document.getElementsByTagName('video');for(var i=0;i<vs.length;i++){var b=vs[i].getBoundingClientRect();"
            + "if(b.width*b.height>window.innerWidth*vh*0.25&&t0.clientX>=b.left&&t0.clientX<=b.right&&t0.clientY>=b.top&&t0.clientY<=b.bottom)return 0;}"
            + "var rs=getComputedStyle(document.documentElement).scrollSnapType+getComputedStyle(document.body).scrollSnapType;if(/y|block|both/.test(rs))return 0;"
            + "return 1}catch(x){return 1}}"
            + "var sent=0;window.addEventListener('touchstart',function(e){sent=0;LumenBridge.ptr(ok(e))},{capture:true,passive:true});"
            + "window.addEventListener('touchmove',function(e){if(e.defaultPrevented&&!sent){sent=1;LumenBridge.ptr(0)}},{passive:true});})();");

    static final String PW_JS = R("(function(){if(window.__lumenPw)return;window.__lumenPw=1;var B=LumenBridge;"
            + "function vis(e){return !!(e&&(e.offsetWidth||e.offsetHeight||e.getClientRects().length))}"
            + "function pws(){return Array.prototype.filter.call(document.querySelectorAll('input[type=password]'),vis)}"
            + "function txt(e){var t=(e.type||'text').toLowerCase();return e.tagName=='INPUT'&&(t=='text'||t=='email'||t=='tel')}"
            + "function isU(e){if(!e||!txt(e))return false;var a=((e.autocomplete||'')+' '+(e.name||'')+' '+(e.id||'')+' '+(e.placeholder||''));"
            + "return e.type=='email'||/user|login|email|e-mail|phone|mail|account|identifier|логин|почт|телефон/i.test(a)}"
            + "function uf(p){var root=p&&p.form?p.form:document,ins=root.querySelectorAll('input'),u=null;"
            + "for(var i=0;i<ins.length;i++){var e=ins[i];if(e==p)break;if(txt(e)&&vis(e))u=e;}return u}"
            + "function cap(){var ps=pws(),p=null;for(var i=0;i<ps.length;i++)if(ps[i].value)p=ps[i];if(!p)return false;"
            + "var u=uf(p),uv=u?u.value:'';if(!uv){try{uv=sessionStorage.getItem('__lumenU')||''}catch(x){}}B.pwPending(location.href,uv,p.value);return true}"
            + "function sub(){if(cap()){B.pwSubmit();setTimeout(function(){if(!pws().some(function(p){return p.value}))B.pwDone()},2500)}}"
            + "document.addEventListener('submit',sub,true);"
            + "document.addEventListener('keydown',function(e){if(e.key=='Enter'&&e.target&&e.target.tagName=='INPUT'&&pws().length)sub()},true);"
            + "document.addEventListener('click',function(e){var b=e.target&&e.target.closest&&e.target.closest('button,input[type=submit],input[type=button],[role=button],a');"
            + "if(b&&pws().some(function(p){return p.value}))sub()},true);"
            + "document.addEventListener('change',function(e){var t=e.target;if(t&&(isU(t)||(txt(t)&&!pws().length))){try{sessionStorage.setItem('__lumenU',t.value)}catch(x){}}},true);"
            + "document.addEventListener('focusin',function(e){var t=e.target;if(!t||t.tagName!='INPUT')return;"
            + "if(t.type=='password')B.pwFocus(1);else if(isU(t)||(pws().length&&uf(pws()[0])==t))B.pwFocus(0)},true);"
            + "document.addEventListener('focusout',function(e){if(e.target&&e.target.tagName=='INPUT')B.pwBlur()},true);"
            + "})();");


    /**
     * Self-contained form filler. It is evaluated directly (never stored on window), so page scripts
     * cannot replace it to receive the password, and it refuses to run if the document's origin changed
     * since the browser decided to fill (location.origin cannot be spoofed by the page).
     * Usage: FILL_JS + "(" + origin + "," + user + "," + pass + "," + auto + ")".
     */
    static final String FILL_JS = R("(function(o,u,p,auto){try{if(location.origin!==o||window.top!==window)return;"
            + "function vis(e){return !!(e&&(e.offsetWidth||e.offsetHeight||e.getClientRects().length))}"
            + "function pws(){return Array.prototype.filter.call(document.querySelectorAll('input[type=password]'),vis)}"
            + "function txt(e){var t=(e.type||'text').toLowerCase();return e.tagName=='INPUT'&&(t=='text'||t=='email'||t=='tel')}"
            + "function uf(p){var root=p&&p.form?p.form:document,ins=root.querySelectorAll('input'),u=null;"
            + "for(var i=0;i<ins.length;i++){var e=ins[i];if(e==p)break;if(txt(e)&&vis(e))u=e;}return u}"
            + "function set(e,v){if(!e)return;try{var d=Object.getOwnPropertyDescriptor(HTMLInputElement.prototype,'value');d.set.call(e,v)}catch(x){e.value=v}"
            + "e.dispatchEvent(new Event('input',{bubbles:true}));e.dispatchEvent(new Event('change',{bubbles:true}))}"
            + "var ps=pws(),a=document.activeElement;if(ps.length){var pf=a&&a.type=='password'&&vis(a)?a:ps[0];if(auto&&pf.value)return;"
            + "var u0=uf(pf)||(a&&a!=pf&&txt(a)?a:null);if(u0&&u&&!(auto&&u0.value))set(u0,u);set(pf,p);return}"
            + "if(!auto&&a&&txt(a)&&u)set(a,u)}catch(x){}})");

    // Ignore feed previews and keep the selected player stable during PiP transitions.
    private static final String PIP_SELECT = "function youtubePage(){return /(^|\\.)youtube\\.com$/.test(location.hostname)&&(/^\\/watch$/.test(location.pathname)&&new URLSearchParams(locat"
            + "ion.search).has('v')||/^\\/shorts\\/[^/]+/.test(location.pathname)||/^\\/embed\\/[^/]+/.test(location.pathname));}function eligible(v){if(!v||v."
            + "ended||!v.isConnected)return false;var yt=/(^|\\.)youtube\\.com$/.test(location.hostname);if(yt){if(!youtubePage())return false;if(v.closest('"
            + "ytd-video-preview,ytd-inline-preview,ytd-thumbnail,ytm-video-with-context-renderer,ytm-rich-item-renderer'))return false;}else if(v.muted&&!"
            + "v.controls&&!document.fullscreenElement)return false;var r=v.getBoundingClientRect(),c=getComputedStyle(v);return r.width>1&&r.height>1&&r.b"
            + "ottom>0&&r.right>0&&r.top<innerHeight&&r.left<innerWidth&&c.display!='none'&&c.visibility!='hidden';}function selected(){var fs=window.__las"
            + "urFullscreenVideo;if(window.__lasurFullscreenActive&&fs&&fs.isConnected)return fs;var old=window.__lasurPipVideo;if(window.__lasurPip&&old&&"
            + "old.isConnected&&!old.ended)return old;var best=null,score=-1,vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){var v=vs[i]"
            + ";if(!eligible(v))continue;var r=v.getBoundingClientRect();var a=r.width*r.height+(!v.paused?1e9:0)+(v.currentTime>0?1e8:0);if(a>score){score"
            + "=a;best=v}}return best;}function play(v){try{var p=v.play();if(p&&p.catch)p.catch(function(){})}catch(e){}}function begin(){var v=selected()"
            + ";if(!v)return null;if(!window.__lasurPip){window.__lasurPipAt=Date.now();window.__lasurUserPause=0;window.__lasurResN=0;window.__lasurWasPla"
            + "ying=!v.paused||Date.now()-(window.__lasurLastPlay||0)<2000;}window.__lasurPip=1;window.__lasurPipVideo=v;return v;}function recover(v){if(v"
            + "&&v.paused&&v.isConnected&&v===window.__lasurPipVideo&&window.__lasurKeep&&(!/(^|\\.)youtube\\.com$/.test(location.hostname)||youtubePage())&&"
            + "window.__lasurPipBackground&&window.__lasurPip&&window.__lasurWasPlaying&&!window.__lasurUserPause&&!v.ended&&Date.now()-window.__lasurPipAt"
            + "<5000&&(window.__lasurResN||0)<4){window.__lasurResN=(window.__lasurResN||0)+1;play(v);}}";
    static final String MEDIA_JS = R("(function(){if(window.__lasurMedia)return;window.__lasurMedia=1;var last='';"
            + PIP_SELECT + "try{var ms=MediaSession.prototype,originalAction=ms.setActionHandler;ms.setActionHandler=function(action,handler){var wrapped=handler;if(typ"
            + "eof handler=='function'&&(action=='pause'||action=='stop'||action=='play'))wrapped=function(){if(window.__lasurPip){window.__lasurUserPause="
            + "action=='play'?0:1;if(action=='play')window.__lasurWasPlaying=1;}return handler.apply(this,arguments)};return originalAction.call(this,actio"
            + "n,wrapped)}}catch(e){}try{var mp=HTMLMediaElement.prototype,originalPause=mp.pause;window.__lasurPause=function(v){return originalPause.call"
            + "(v)};mp.pause=function(){if(window.__lasurKeep&&window.__lasurPipBackground&&window.__lasurPip&&window.__lasurWasPlaying&&!window.__lasurUse"
            + "rPause&&this===window.__lasurPipVideo&&!this.ended&&this.isConnected&&youtubePage())return;return originalPause.apply(this,arguments)}}catch"
            + "(e){}function playing(){var v=selected();return !!(v&&!v.paused&&!v.ended);}function keep(){return window.__lasurKeep&&(window.__lasurPip||p"
            + "laying());}try{var D=Document.prototype,hd=Object.getOwnPropertyDescriptor(D,'hidden'),vd=Object.getOwnPropertyDescriptor(D,'visibilityState"
            + "');Object.defineProperty(document,'hidden',{configurable:true,get:function(){return keep()?false:hd.get.call(document)}});Object.definePrope"
            + "rty(document,'visibilityState',{configurable:true,get:function(){return keep()?'visible':vd.get.call(document)}});var hf=Document.prototype."
            + "hasFocus;document.hasFocus=function(){return keep()?true:hf.call(document)}}catch(e){}['visibilitychange','webkitvisibilitychange','blur','p"
            + "agehide','freeze'].forEach(function(n){window.addEventListener(n,function(e){if(keep())e.stopImmediatePropagation()},true);document.addEvent"
            + "Listener(n,function(e){if(keep())e.stopImmediatePropagation()},true)});function rep(){try{var v=selected(),on=!!(v&&!v.paused&&!v.ended&&v.r"
            + "eadyState>1),ok=!!(v&&eligible(v));if(on)window.__lasurLastPlay=Date.now();var s=(on?1:0)+','+(v?v.videoWidth:0)+','+(v?v.videoHeight:0)+','"
            + "+(ok?1:0)+','+location.href;if(s!=last){last=s;LumenBridge.media(on?1:0,v?v.videoWidth:0,v?v.videoHeight:0,ok?1:0)}}catch(e){}}['play','play"
            + "ing','pause','ended','emptied','loadedmetadata','volumechange'].forEach(function(n){document.addEventListener(n,function(e){if(n=='playing'&"
            + "&eligible(e.target))window.__lasurLastPlay=Date.now();if(n=='pause'&&window.__lasurPip&&e.target===window.__lasurPipVideo)setTimeout(functio"
            + "n(){recover(e.target);rep()},120);rep()},true)});['pointerdown','mousedown','touchstart','click','keydown'].forEach(function(n){document.add"
            + "EventListener(n,function(e){if(e.isTrusted&&window.__lasurPip)window.__lasurUserPause=1},true)});['yt-navigate-finish','popstate','fullscree"
            + "nchange'].forEach(function(n){window.addEventListener(n,rep,true)});var rt=0;function soon(){if(!rt)rt=setTimeout(function(){rt=0;rep()},150)}"
            + "['scroll','resize'].forEach(function(n){window.addEventListener(n,soon,{capture:true,passive:true})});setInterval(rep,400);rep();})();");

    static final String PIP_ON_JS = R("(function(){try{" + PIP_SELECT + "var best=begin();if(!best)return;"
            + "var st=document.getElementById('__lasurPip');if(!st){st=document.createElement('style');st.id='__lasurPip';"
            + "st.textContent='.__lasurPipA{transform:none!important;filter:none!important;contain:none!important;perspective:none!important;will-change:auto!important;z-index:2147483646!important}'"
            + "+'.__lasurPipV{position:fixed!important;left:0!important;top:0!important;width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;object-fit:contain!important;background:#000!important;z-index:2147483647!important;margin:0!important;transform:none!important;visibility:visible!important;opacity:1!important}'"
            + "+'html.__lasurPipH,html.__lasurPipH body{overflow:hidden!important;background:#000!important}';(document.head||document.documentElement).appendChild(st)}"
            + "best.classList.add('__lasurPipV');for(var n=best.parentElement;n&&n!=document.documentElement;n=n.parentElement)n.classList.add('__lasurPipA');"
            + "document.documentElement.classList.add('__lasurPipH');"
            + "recover(best);"
            + "}catch(e){}})();");

    static final String PIP_OFF_JS = R("(function(){try{window.__lasurPip=0;window.__lasurPipBackground=0;window.__lasurUserPause=1;window.__lasurPipVideo=null;"
            + "var a=document.querySelectorAll('.__lasurPipA,.__lasurPipV');for(var i=0;i<a.length;i++)a[i].classList.remove('__lasurPipA','__lasurPipV');"
            + "document.documentElement.classList.remove('__lasurPipH');var s=document.getElementById('__lasurPip');if(s)s.remove();}catch(e){}})();");

    static final String PIP_FS_JS = R("(function(){try{" + PIP_SELECT + "var v=begin();"
            + "recover(v);"
            + "}catch(e){}})();");

    static final String PIP_TOGGLE_JS = R("(function(){try{" + PIP_SELECT + "var v=selected();if(!v)return;"
            + "if(v.paused){window.__lasurUserPause=0;window.__lasurWasPlaying=1;play(v)}else{window.__lasurUserPause=1;window.__lasurWasPlaying=0;if(window.__lasurPause)window.__lasurPause(v);else v.pause()}"
            + "LumenBridge.media(!v.paused&&!v.ended?1:0,v.videoWidth||0,v.videoHeight||0,eligible(v)?1:0);"
            + "}catch(e){}})();");

    static final String PIP_REMEMBER_FULLSCREEN_JS = R("(function(){" + PIP_SELECT + "window.__lasurFullscreenVideo=selected();})();");
    static final String PIP_RESTORE_FULLSCREEN_JS = R("(function(){" + PIP_SELECT + "return ((function(){try{var v=window.__lasurFullscreenVideo;if(!v||!v.isConnected)v=selected();if(!v)return false;var player=v.closest('.htm"
            + "l5-video-player,#player-container-id,#movie_player,#player')||v;window.__lasurFullscreenVideo=v;window.__lasurFullscreenActive=1;if(player=="
            + "=v){window.__lasurFullscreenControls=v.controls;v.controls=true;}var st=document.createElement('style');st.id='__lasurFullscreen';st.textCon"
            + "tent='.__lasurFullscreenA{transform:none!important;filter:none!important;contain:none!important;perspective:none!important;overflow:visible!"
            + "important;will-change:auto!important;isolation:auto!important;opacity:1!important;z-index:2147483646!important}"
            + "html.__lasurFullscreenH ytm-mobile-topbar-renderer,html.__lasurFullscreenH #header-bar,html.__lasurFullscreenH ytd-masthead,html.__lasurFullscreenH #masthead-container{display:none!important}"
            + ".__lasurFullscreenPlayer .html5-video-container{position:absolute!important;inset:0!important;width:100%!important;height:100%!important;transform:none!important}.__lasurFullscreenPlayer{position:fixed!important;inset:0!important;width:100vw!important;height:100vh!important;max-width:none!im"
            + "portant;max-height:none!important;background:#000!important;z-index:2147483647!important;margin:0!important;transform:none!important}.__lasu"
            + "rFullscreenPlayer video,video.__lasurFullscreenPlayer{width:100%!important;height:100%!important;object-fit:contain!important;max-width:none"
            + "!important;max-height:none!important;margin:0!important}.__lasurFullscreenPlayer video{position:absolute!important;left:0!important;top:0!im"
            + "portant;right:auto!important;bottom:auto!important;transform:none!important}html.__lasurFullscreenH,html.__lasurFullscreenH body{overflow:hidden!important;background:#000!important}';(document.head||document."
            + "documentElement).appendChild(st);player.classList.add('__lasurFullscreenPlayer');for(var n=player.parentElement;n&&n!==document.documentElem"
            + "ent;n=n.parentElement)n.classList.add('__lasurFullscreenA');document.documentElement.classList.add('__lasurFullscreenH');return true;}catch("
            + "e){return false;}})());})();");
    /** Pauses blocked during PiP leave YouTube's own state at "paused" while the video plays; realign it after return. */
    static final String PIP_SYNC_JS = R("(function(){try{var vs=document.querySelectorAll('video');for(var i=0;i<vs.length;i++){var v=vs[i];if(v.paused||v.ended||!v.closest)continue;"
            + "var p=v.closest('#movie_player,.html5-video-player');if(p&&typeof p.getPlayerState=='function'&&p.getPlayerState()==2&&typeof p.playVideo=='function')p.playVideo();}}catch(e){}"
            + "try{window.dispatchEvent(new Event('resize'))}catch(e){}})();");
    static final String FULLSCREEN_OFF_JS = R("(function(){try{var v=window.__lasurFullscreenVideo;if(v&&window.__lasurFullscreenControls!==undefined)v.controls=window.__lasurFullscreenCo"
            + "ntrols;window.__lasurFullscreenControls=undefined;window.__lasurFullscreenVideo=null;window.__lasurFullscreenActive=0;var a=document.querySe"
            + "lectorAll('.__lasurFullscreenA,.__lasurFullscreenPlayer');for(var i=0;i<a.length;i++)a[i].classList.remove('__lasurFullscreenA','__lasurFull"
            + "screenPlayer');document.documentElement.classList.remove('__lasurFullscreenH');var st=document.getElementById('__lasurFullscreen');if(st)st."
            + "remove();}catch(e){}})();");
}

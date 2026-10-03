(function(){
if(window.__lasurReaderOpen)return;
var NEG=/(^|[\s_-])(share|sharing|social|comment|comments|related|recommend|promo|advert|ads?|sponsor|subscribe|newsletter|banner|popup|modal|cookie|consent|sidebar|breadcrumbs?|tags?|footer|masthead|navbar|nav|menu|widget|outbrain|taboola|rating|author-bio|login|signup|paywall)([\s_-]|$)/i;
var POS=/article|content|entry|post|story|text|body|main|blog/i;
var DROP={SCRIPT:1,STYLE:1,NOSCRIPT:1,IFRAME:1,FORM:1,BUTTON:1,INPUT:1,SELECT:1,TEXTAREA:1,NAV:1,ASIDE:1,FOOTER:1,SVG:1,CANVAS:1,OBJECT:1,EMBED:1,TEMPLATE:1,DIALOG:1,VIDEO:1,AUDIO:1,LINK:1,META:1};
var KEEP={P:1,H1:1,H2:1,H3:1,H4:1,H5:1,H6:1,UL:1,OL:1,LI:1,BLOCKQUOTE:1,PRE:1,CODE:1,FIGURE:1,FIGCAPTION:1,A:1,STRONG:1,B:1,EM:1,I:1,U:1,S:1,BR:1,HR:1,TABLE:1,THEAD:1,TBODY:1,TFOOT:1,TR:1,TD:1,TH:1,CAPTION:1,SUP:1,SUB:1,DL:1,DT:1,DD:1,MARK:1,SMALL:1,Q:1,CITE:1,ABBR:1,TIME:1,KBD:1,DEL:1,INS:1};
var BLOCK={DIV:1,SECTION:1,ARTICLE:1,MAIN:1,HEADER:1,HGROUP:1,CENTER:1,DETAILS:1,SUMMARY:1,ADDRESS:1};
function txt(e){return (e.textContent||'').replace(/\s+/g,' ').trim();}
function cls(e){return ((typeof e.className==='string'?e.className:'')+' '+(e.id||''));}
function linkDensity(e){var t=txt(e).length||1,l=0,a=e.getElementsByTagName('a');for(var i=0;i<a.length;i++)l+=txt(a[i]).length;return l/t;}
function hidden(e){try{var s=getComputedStyle(e);return s.display==='none'||s.visibility==='hidden';}catch(x){return false;}}
function pick(){
  var sc=new Map(),ps=document.querySelectorAll('p,pre,blockquote,td,div>br');
  ps.forEach(function(p){
    if(p.tagName==='BR')p=p.parentNode;
    var t=txt(p);if(t.length<25)return;
    var s=1+t.split(/[,،，]/).length+Math.min(3,Math.floor(t.length/100));
    var a=p.parentNode,b=a&&a.parentNode;
    if(a&&a.nodeType===1)sc.set(a,(sc.get(a)||0)+s);
    if(b&&b.nodeType===1)sc.set(b,(sc.get(b)||0)+s/2);
  });
  var best=null,bs=0;
  sc.forEach(function(s,e){
    var c=cls(e);
    if(NEG.test(c)&&!POS.test(c))s*=0.4;else if(POS.test(c))s*=1.25;
    if(e.tagName==='ARTICLE'||e.tagName==='MAIN')s*=1.3;
    s*=1-Math.min(0.9,linkDensity(e));
    if(s>bs){bs=s;best=e;}
  });
  if(best){
    // a parent holding several good siblings is the real article
    var par=best.parentNode;
    if(par&&par!==document.body&&par.nodeType===1&&sc.get(par)>bs*0.8)best=par;
  }
  return best;
}
function imgSrc(e){
  var s=e.currentSrc||e.getAttribute('data-src')||e.getAttribute('data-lazy-src')||e.getAttribute('data-original')||e.getAttribute('data-url')||e.src||'';
  if((!s||/^data:/.test(s))&&(e.getAttribute('srcset')||e.getAttribute('data-srcset'))){s=(e.getAttribute('data-srcset')||e.getAttribute('srcset')).split(',')[0].trim().split(' ')[0];}
  if(!s||/^data:/.test(s))return '';
  try{return new URL(s,location.href).href;}catch(x){return '';}
}
function copy(n,out,depth){
  if(depth>40)return;
  for(var c=n.firstChild;c;c=c.nextSibling){
    if(c.nodeType===3){out.appendChild(document.createTextNode(c.nodeValue));continue;}
    if(c.nodeType!==1)continue;
    var tg=c.tagName.toUpperCase();
    if(DROP[tg])continue;
    if(hidden(c))continue;
    var k=cls(c);
    if(k&&NEG.test(k)&&!POS.test(k)&&(txt(c).length<500||linkDensity(c)>0.35))continue;
    if(tg==='IMG'){
      var s=imgSrc(c),w=c.naturalWidth||+c.getAttribute('width')||0;
      if(!s||(w&&w<60))continue;
      var im=document.createElement('img');im.src=s;if(c.alt)im.alt=c.alt;im.loading='lazy';out.appendChild(im);continue;
    }
    if(tg==='PICTURE'){var pi=c.querySelector('img'),ps2=pi&&imgSrc(pi);if(ps2){var im2=document.createElement('img');im2.src=ps2;if(pi.alt)im2.alt=pi.alt;out.appendChild(im2);}continue;}
    if(tg==='HEADER'&&linkDensity(c)>0.3)continue;
    if(KEEP[tg]){
      if(tg==='UL'||tg==='OL'){if(linkDensity(c)>0.6)continue;}
      var e=document.createElement(tg);
      if(tg==='A'){var h=c.getAttribute('href');if(h&&!/^javascript:/i.test(h))try{e.href=new URL(h,location.href).href;}catch(x){}}
      if(tg==='TD'||tg==='TH'){if(c.colSpan>1)e.colSpan=c.colSpan;if(c.rowSpan>1)e.rowSpan=c.rowSpan;}
      copy(c,e,depth+1);
      if(tg!=='BR'&&tg!=='HR'&&!e.textContent.trim()&&!e.querySelector('img'))continue;
      out.appendChild(e);continue;
    }
    if(BLOCK[tg]){var d=document.createElement('div');copy(c,d,depth+1);if(d.textContent.trim()||d.querySelector('img'))out.appendChild(d);continue;}
    copy(c,out,depth+1); // inline wrappers (span, font, …)
  }
}
function meta(p){var m=document.querySelector('meta[property="'+p+'"],meta[name="'+p+'"]');return m&&m.content?m.content.trim():'';}
window.__lasurReader=function(o){
  if(window.__lasurReaderOpen)return 'on';
  var best=pick();
  if(!best||txt(best).length<250)return 'none';
  var body=document.createElement('div');
  copy(best,body,0);
  if(body.textContent.replace(/\s+/g,'').length<200)return 'none';
  var title=meta('og:title')||document.title||'';
  var h1=body.querySelector('h1');
  if(h1&&(!title||title.indexOf(txt(h1))>=0||txt(h1).indexOf(title)>=0)){title=txt(h1)||title;h1.remove();}
  var host=document.createElement('div');
  host.style.cssText='position:fixed!important;inset:0!important;z-index:2147483647!important;display:block!important;margin:0!important;padding:0!important;border:0!important;background:transparent!important;';
  var root=host.attachShadow?host.attachShadow({mode:'closed'}):host;
  var size=o.size||19,theme=o.theme||0,TH=[['#ffffff','#1f1f1f','#5f6368','#0b57d0','#f1f3f4'],['#f6efe0','#433422','#7a6650','#8a4b0f','#ebe0c8'],['#1b1c1e','#e3e3e3','#9aa0a6','#8ab4f8','#2b2c2f']];
  var css='*{box-sizing:border-box}.w{position:absolute;inset:0;overflow-y:auto;-webkit-overflow-scrolling:touch;overscroll-behavior:contain;font-family:Georgia,"Noto Serif",serif;transition:background .2s}'+
   '.bar{position:sticky;top:0;display:flex;align-items:center;gap:4px;padding:8px 8px;font-family:system-ui,sans-serif;z-index:2}'+
   '.bar button{border:0;background:transparent;font:500 16px system-ui,sans-serif;min-width:44px;height:40px;border-radius:20px;padding:0 10px;color:inherit}'+
   '.bar button:active{opacity:.6}.sp{flex:1}.dot{width:22px;height:22px;border-radius:50%;display:inline-block;vertical-align:middle;border:2px solid transparent}'+
   '.a{max-width:720px;margin:0 auto;padding:4px 22px 80px;line-height:1.65;word-wrap:break-word;overflow-wrap:break-word}'+
   '.h{font:13px system-ui,sans-serif;margin:8px 0 6px}.t{font:700 1.45em/1.25 system-ui,sans-serif;margin:0 0 18px}'+
   '.a img{max-width:100%;height:auto;display:block;margin:14px auto;border-radius:6px}.a figure{margin:16px 0}.a figcaption{font-size:.8em;text-align:center}'+
   '.a pre{overflow-x:auto;padding:10px;border-radius:6px;font-size:.85em}.a code{font-size:.9em}.a blockquote{margin:12px 0;padding:2px 0 2px 16px;border-left:3px solid}'+
   '.a table{border-collapse:collapse;display:block;overflow-x:auto;font-size:.85em}.a td,.a th{border:1px solid;padding:4px 8px}.a h2,.a h3,.a h4{font-family:system-ui,sans-serif;line-height:1.3}';
  var st=document.createElement('style');st.textContent=css;root.appendChild(st);
  var w=document.createElement('div');w.className='w';root.appendChild(w);
  var bar=document.createElement('div');bar.className='bar';w.appendChild(bar);
  function btn(label,aria,f){var b=document.createElement('button');b.textContent=label;b.setAttribute('aria-label',aria);b.addEventListener('click',function(e){e.preventDefault();e.stopPropagation();f();});bar.appendChild(b);return b;}
  var art=document.createElement('div');art.className='a';w.appendChild(art);
  var hh=document.createElement('div');hh.className='h';hh.textContent=location.hostname.replace(/^www\./,'');art.appendChild(hh);
  if(title){var tt=document.createElement('h1');tt.className='t';tt.textContent=title;art.appendChild(tt);}
  while(body.firstChild)art.appendChild(body.firstChild);
  var prevOv=document.documentElement.style.overflow;
  function close(){try{host.remove();}catch(x){}document.documentElement.style.overflow=prevOv;window.__lasurReaderOpen=0;window.__lasurReaderClose=null;try{LumenBridge.reader(0,size,theme);}catch(x){}}
  var cb=btn('',o.close,close);cb.innerHTML='<svg width="22" height="22" viewBox="0 0 24 24" style="vertical-align:middle"><path fill="currentColor" d="M19 6.41L17.59 5 12 10.59 6.41 5 5 6.41 10.59 12 5 17.59 6.41 19 12 13.41 17.59 19 19 17.59 13.41 12z"/></svg>';
  var sp=document.createElement('div');sp.className='sp';bar.appendChild(sp);
  btn('A\u2212',o.smaller,function(){size=Math.max(13,size-2);apply();save();});
  btn('A+',o.bigger,function(){size=Math.min(32,size+2);apply();save();});
  var tb=btn('',o.themeLabel,function(){theme=(theme+1)%3;apply();save();});
  var dot=document.createElement('span');dot.className='dot';tb.appendChild(dot);
  function save(){try{LumenBridge.reader(1,size,theme);}catch(x){}}
  function apply(){var c=TH[theme];w.style.background=c[0];w.style.color=c[1];bar.style.background=c[0];hh.style.color=c[2];art.style.fontSize=size+'px';
    dot.style.background=TH[(theme+1)%3][0];dot.style.borderColor=c[2];
    st.textContent=css+'.a a{color:'+c[3]+'}.a pre,.a code{background:'+c[4]+'}.a blockquote,.a td,.a th{border-color:'+c[4]+'}.a figcaption{color:'+c[2]+'}';}
  apply();
  document.documentElement.style.overflow='hidden';
  (document.body||document.documentElement).appendChild(host);
  window.__lasurReaderOpen=1;window.__lasurReaderClose=close;
  return 'on';
};
})();

/* 高保真原型交互脚本（纯原生 JS；GSAP 通过 CDN 可选增强，缺失时自动降级为 CSS 动画） */
(() => {
const qs = (s, el = document) => el.querySelector(s);
const qsa = (s, el = document) => [...el.querySelectorAll(s)];
const params = new URLSearchParams(location.search);
const reduce = matchMedia('(prefers-reduced-motion: reduce)').matches;
const root = document.documentElement;

/* ---------- 主题：?theme=dark | localStorage ---------- */
const savedTheme = params.get('theme') || localStorage.getItem('ct-theme');
if (savedTheme === 'dark') { root.classList.add('dark'); root.dataset.theme = 'dark'; }
if (params.has('shot')) root.classList.add('shot');

/* ---------- 图标（Lucide 风格 24px 描边） ---------- */
const I = {
  search:'<circle cx="11" cy="11" r="7"/><path d="m20 20-3.5-3.5"/>',
  heart:'<path d="M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z"/>',
  message:'<path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>',
  plus:'<path d="M12 5v14M5 12h14"/>',
  bell:'<path d="M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9"/><path d="M10.3 21a1.94 1.94 0 0 0 3.4 0"/>',
  sun:'<circle cx="12" cy="12" r="4"/><path d="M12 2v2M12 20v2M4.93 4.93l1.41 1.41M17.66 17.66l1.41 1.41M2 12h2M20 12h2M6.34 17.66l-1.41 1.41M19.07 4.93l-1.41 1.41"/>',
  moon:'<path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z"/>',
  home:'<path d="M3 10.5 12 3l9 7.5V20a1 1 0 0 1-1 1h-5v-6h-6v6H4a1 1 0 0 1-1-1z"/>',
  grid:'<rect x="3" y="3" width="7" height="7" rx="2"/><rect x="14" y="3" width="7" height="7" rx="2"/><rect x="3" y="14" width="7" height="7" rx="2"/><rect x="14" y="14" width="7" height="7" rx="2"/>',
  pin:'<path d="M20 10c0 6-8 12-8 12s-8-6-8-12a8 8 0 0 1 16 0Z"/><circle cx="12" cy="10" r="3"/>',
  shield:'<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/><path d="m9 12 2 2 4-4"/>',
  sparkle:'<path d="M12 3l1.9 5.1L19 10l-5.1 1.9L12 17l-1.9-5.1L5 10l5.1-1.9z"/><path d="M19 17l.7 1.8 1.8.7-1.8.7L19 22l-.7-1.8-1.8-.7 1.8-.7z"/>',
  clock:'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
  eye:'<path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/>',
  share:'<circle cx="18" cy="5" r="3"/><circle cx="6" cy="12" r="3"/><circle cx="18" cy="19" r="3"/><path d="m8.6 13.5 6.8 4M15.4 6.5l-6.8 4"/>',
  flag:'<path d="M4 22V4s1-1 4-1 5 2 8 2 4-1 4-1v11s-1 1-4 1-5-2-8-2-4 1-4 1"/>',
  right:'<path d="m9 18 6-6-6-6"/>', left:'<path d="m15 18-6-6 6-6"/>', down:'<path d="m6 9 6 6 6-6"/>',
  send:'<path d="m22 2-7 20-4-9-9-4Z"/><path d="M22 2 11 13"/>',
  image:'<rect x="3" y="3" width="18" height="18" rx="3"/><circle cx="9" cy="9" r="2"/><path d="m21 15-5-5L5 21"/>',
  smile:'<circle cx="12" cy="12" r="9"/><path d="M8 14s1.5 2 4 2 4-2 4-2M9 9h.01M15 9h.01"/>',
  upload:'<path d="M4 15v4a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-4"/><path d="m17 8-5-5-5 5M12 3v12"/>',
  trend:'<path d="m22 7-8.5 8.5-5-5L2 17"/><path d="M16 7h6v6"/>',
  box:'<path d="M21 8 12 3 3 8v8l9 5 9-5z"/><path d="m3 8 9 5 9-5M12 13v8"/>',
  star:'<path d="m12 2 3.1 6.3 6.9 1-5 4.9 1.2 6.8L12 17.8 5.8 21l1.2-6.8-5-4.9 6.9-1z"/>',
  wallet:'<path d="M20 7V5a2 2 0 0 0-2-2H5a2 2 0 0 0 0 4h15v13H5a2 2 0 0 1-2-2V5"/><circle cx="16" cy="13" r="1.5"/>',
  settings:'<circle cx="12" cy="12" r="3"/><path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-2.82 1.17V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 8 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 2.18 14H2a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 8a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 10 2.18V2a2 2 0 1 1 4 0v.09A1.65 1.65 0 0 0 16 4.6a1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 21.82 10H22a2 2 0 1 1 0 4h-.09A1.65 1.65 0 0 0 19.4 15z"/>',
  logout:'<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
  users:'<path d="M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2"/><circle cx="9" cy="7" r="4"/><path d="M22 21v-2a4 4 0 0 0-3-3.87M16 3.13a4 4 0 0 1 0 7.75"/>',
  check:'<path d="M20 6 9 17l-5-5"/>', x:'<path d="M18 6 6 18M6 6l12 12"/>',
  filecheck:'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"/><path d="M14 2v6h6M9 15l2 2 4-4"/>',
  alert:'<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0z"/><path d="M12 9v4M12 17h.01"/>',
  mega:'<path d="m3 11 18-5v12L3 14v-3z"/><path d="M11.6 16.8a3 3 0 1 1-5.8-1.6"/>',
  chart:'<path d="M3 3v18h18"/><path d="M18 17V9M13 17V5M8 17v-3"/>',
  tag:'<path d="M20.6 13.4 13.4 20.6a2 2 0 0 1-2.8 0L3 13V3h10l7.6 7.6a2 2 0 0 1 0 2.8z"/><circle cx="7.5" cy="7.5" r="1.5"/>',
  zap:'<path d="M13 2 3 14h9l-1 8 10-12h-9z"/>', more:'<circle cx="5" cy="12" r="1"/><circle cx="12" cy="12" r="1"/><circle cx="19" cy="12" r="1"/>',
  camera:'<path d="M14.5 4h-5L7.5 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3.5z"/><circle cx="12" cy="13" r="3.5"/>',
  filter:'<path d="M22 3H2l8 9.5V19l4 2v-8.5z"/>', book:'<path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20V3H6.5A2.5 2.5 0 0 0 4 5.5z"/><path d="M4 19.5A2.5 2.5 0 0 0 6.5 22H20v-5"/>',
  laptop:'<rect x="3" y="4" width="18" height="12" rx="2"/><path d="M1 20h22"/>', bike:'<circle cx="5.5" cy="17.5" r="3.5"/><circle cx="18.5" cy="17.5" r="3.5"/><path d="M15 6h2l3 11.5M5.5 17.5 9 9h7l-4 8.5H5.5"/>',
  shirt:'<path d="M20.4 6.6 16 4a4 4 0 0 1-8 0L3.6 6.6a1 1 0 0 0-.5 1.2l1.2 3.4a1 1 0 0 0 1.2.6L7 11v9a1 1 0 0 0 1 1h8a1 1 0 0 0 1-1v-9l1.5.8a1 1 0 0 0 1.2-.6l1.2-3.4a1 1 0 0 0-.5-1.2z"/>',
  music:'<path d="M9 18V5l12-2v13"/><circle cx="6" cy="18" r="3"/><circle cx="18" cy="16" r="3"/>', ticket:'<path d="M2 9a3 3 0 0 0 0 6v3a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-3a3 3 0 0 0 0-6V6a2 2 0 0 0-2-2H4a2 2 0 0 0-2 2z"/><path d="M13 5v2M13 17v2M13 11v2"/>',
  lamp:'<path d="M8 2h8l4 8H4zM12 10v8M8 22h8"/>', leaf:'<path d="M11 20A7 7 0 0 1 9.8 6.1C15.5 5 17 4.5 19 2c1 2 2 4.2 2 8 0 5.5-4.8 10-10 10Z"/><path d="M2 21c0-3 1.9-5.4 5.1-6"/>',
  gift:'<rect x="3" y="8" width="18" height="4" rx="1"/><path d="M12 8v13M19 12v7a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2v-7M7.5 8a2.5 2.5 0 0 1 0-5C11 3 12 8 12 8s1-5 4.5-5a2.5 2.5 0 0 1 0 5"/>',
  edit:'<path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z"/>', trash:'<path d="M3 6h18M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/>',
  move:'<path d="M5 9l-3 3 3 3M9 5l3-3 3 3M15 19l-3 3-3-3M19 9l3 3-3 3M2 12h20M12 2v20"/>', calendar:'<rect x="3" y="4" width="18" height="18" rx="2"/><path d="M16 2v4M8 2v4M3 10h18"/>',
  truck:'<path d="M10 17h4V5H2v12h3M14 9h4l4 4v4h-3"/><circle cx="7.5" cy="17.5" r="2.5"/><circle cx="16.5" cy="17.5" r="2.5"/>', handshake:'<path d="m11 17 2 2a1 1 0 1 0 3-3"/><path d="m14 14 2.5 2.5a1 1 0 1 0 3-3l-3.88-3.88a3 3 0 0 0-4.24 0l-.88.88a1 1 0 1 1-3-3l2.81-2.81a5.79 5.79 0 0 1 7.06-.87l.47.28a2 2 0 0 0 1.42.25L21 4"/><path d="m21 3 1 11h-2M3 3 2 14l6.5 6.5a1 1 0 1 0 3-3M3 4h8"/>',
};
const icon = (n, cls = '') => `<svg class="i ${cls}" viewBox="0 0 24 24" aria-hidden="true">${I[n] || ''}</svg>`;
window.ctIcon = icon;

/* ---------- 商品插画占位（示例数据，用矢量代替实拍图） ---------- */
const W = 'fill="#fff"', S = 'stroke="#fff" stroke-width="4" stroke-linecap="round" stroke-linejoin="round" fill="none"';
const A = {
  book:`<rect x="18" y="70" width="84" height="18" rx="4" ${W} opacity=".95"/><rect x="24" y="52" width="74" height="18" rx="4" ${W} opacity=".75"/><rect x="14" y="34" width="80" height="18" rx="4" ${W} opacity=".9"/><path d="M28 34v18M34 70v18M38 52v18" stroke="#000" stroke-opacity=".12" stroke-width="3"/>`,
  laptop:`<rect x="24" y="26" width="72" height="50" rx="6" ${W}/><rect x="31" y="33" width="58" height="36" rx="3" fill="#000" opacity=".14"/><path d="M12 84h96l-8 10H20z" ${W} opacity=".85"/>`,
  tablet:`<rect x="30" y="16" width="56" height="84" rx="10" ${W}/><rect x="36" y="24" width="44" height="66" rx="4" fill="#000" opacity=".12"/><rect x="88" y="30" width="7" height="64" rx="3.5" ${W} opacity=".8" transform="rotate(12 91 62)"/>`,
  bike:`<circle cx="34" cy="76" r="18" ${S}/><circle cx="88" cy="76" r="18" ${S}/><path d="M34 76l18-30h26l10 30M52 46l12 30h-30M72 34h12" ${S}/>`,
  headphones:`<path d="M26 72V58a34 34 0 0 1 68 0v14" ${S} stroke-width="7"/><rect x="18" y="62" width="20" height="34" rx="9" ${W}/><rect x="82" y="62" width="20" height="34" rx="9" ${W}/>`,
  camera:`<rect x="16" y="36" width="88" height="60" rx="12" ${W}/><rect x="40" y="26" width="28" height="14" rx="4" ${W}/><circle cx="60" cy="66" r="20" fill="#000" opacity=".16"/><circle cx="60" cy="66" r="12" ${W} opacity=".9"/><circle cx="90" cy="48" r="4" fill="#000" opacity=".2"/>`,
  keyboard:`<rect x="10" y="38" width="100" height="44" rx="8" ${W}/>${[0,1,2].map(r=>[0,1,2,3,4,5,6,7].map(c=>`<rect x="${18+c*11}" y="${46+r*10}" width="8" height="7" rx="2" fill="#000" opacity=".13"/>`).join('')).join('')}`,
  lamp:`<path d="M40 96h40" ${S} stroke-width="6"/><path d="M60 96V64l-14-22" ${S}/><path d="M30 34l30-14 12 24-34 10z" ${W}/><circle cx="46" cy="42" r="4" fill="#000" opacity=".15"/>`,
  guitar:`<path d="M66 20l12 12-24 26" ${S}/><circle cx="46" cy="76" r="22" ${W}/><circle cx="58" cy="60" r="14" ${W}/><circle cx="48" cy="72" r="6" fill="#000" opacity=".16"/><rect x="74" y="14" width="18" height="10" rx="3" ${W} transform="rotate(45 83 19)"/>`,
  sneaker:`<path d="M14 70c0-8 4-24 10-30 8 6 18 8 26 4l16 16c8 6 22 6 34 10 8 3 8 14 2 16H20c-4 0-6-6-6-16z" ${W}/><path d="M14 82h90" stroke="#000" stroke-opacity=".12" stroke-width="5"/><path d="M40 54l6 6M48 50l6 6" stroke="#000" stroke-opacity=".15" stroke-width="3" stroke-linecap="round"/>`,
  plant:`<path d="M38 70h44l-6 28H44z" ${W}/><path d="M60 70C60 50 44 40 30 40c0 16 14 28 30 30zM60 70c0-26 14-40 32-40 0 22-14 38-32 40z" ${W} opacity=".85"/>`,
  calc:`<rect x="32" y="16" width="56" height="88" rx="10" ${W}/><rect x="40" y="24" width="40" height="18" rx="3" fill="#000" opacity=".14"/>${[0,1,2,3].map(r=>[0,1,2].map(c=>`<rect x="${41+c*14}" y="${50+r*12}" width="10" height="8" rx="2" fill="#000" opacity=".12"/>`).join('')).join('')}`,
  kettle:`<path d="M30 46h52l6 50H24z" ${W}/><path d="M82 54c14 0 16 22 2 26" ${S}/><path d="M42 46c0-10 6-16 14-16s14 6 14 16" ${S}/><path d="M30 46 16 34" ${S}/>`,
  ticket:`<path d="M14 40h92v14a8 8 0 0 0 0 16v14H14V70a8 8 0 0 0 0-16z" ${W}/><path d="M78 44v40" stroke="#000" stroke-opacity=".15" stroke-width="3" stroke-dasharray="5 5"/>`,
};
const artSvg = n => `<svg class="art-svg" viewBox="0 0 120 120">${A[n] || A.box || ''}</svg>`;
window.ctArt = artSvg;

function hydrate(scope = document) {
  qsa('i[data-i]', scope).forEach(el => { el.outerHTML = icon(el.dataset.i, el.className); });
  qsa('[data-art]', scope).forEach(el => { if (!el.querySelector('.art-svg')) el.insertAdjacentHTML('beforeend', artSvg(el.dataset.art)); el.classList.add('art'); });
}
window.ctHydrate = hydrate;

/* ---------- 公共头部（用户端） ---------- */
const PAGES = [['index.html','首页'],['detail.html','商品详情'],['publish.html','发布'],['chat.html','聊天'],['profile.html','个人中心'],['admin.html','管理后台']];
const here = location.pathname.split('/').pop() || 'index.html';
const headerEl = qs('[data-app-header]');
if (headerEl) {
  headerEl.outerHTML = `<header class="app-header"><div class="container">
    <a class="logo" href="index.html"><span class="logo__mark">${icon('sparkle')}</span><span>校园闲置<small>CAMPUS · TRADE</small></span></a>
    <nav class="nav">
      <a href="index.html" class="${here==='index.html'?'is-active':''}">发现</a>
      <a href="index.html#feed">分类</a>
      <a href="chat.html" class="${here==='chat.html'?'is-active':''}">消息</a>
      <a href="profile.html" class="${here==='profile.html'?'is-active':''}">我的</a>
    </nav>
    <label class="search">${icon('search')}<input placeholder="搜索教材、数码、自行车…"/><span class="kbd">/</span></label>
    <a class="btn btn-primary btn-pill" href="publish.html" style="height:42px">${icon('plus')}发布闲置</a>
    <button class="icon-btn" aria-label="消息" onclick="location.href='chat.html'">${icon('bell')}<span class="dot-badge num">3</span></button>
    <button class="icon-btn theme-toggle" data-theme-toggle aria-label="切换暗色模式">${icon('sun','sun')}${icon('moon','moon')}</button>
    <a href="profile.html" class="avatar" style="--a1:#ff9a5a;--a2:#ff6b4a">张</a>
  </div></header>`;
}
if (!qs('.proto-dock')) {
  document.body.insertAdjacentHTML('beforeend', `<nav class="proto-dock" aria-label="原型页面切换">${PAGES.map(([h,t])=>`<a href="${h}" class="${h===here?'is-active':''}">${t}</a>`).join('')}</nav>
  <div class="demo-tag"><b></b>高保真原型 · 页面内容均为示例数据</div><div class="toast-wrap"></div>`);
}
hydrate();

/* 头部滚动阴影 */
const hdr = qs('.app-header');
if (hdr) addEventListener('scroll', () => hdr.classList.toggle('is-scrolled', scrollY > 8), { passive: true });

/* ---------- 暗色切换：View Transitions 圆形扩散 ---------- */
function setTheme(dark) { root.classList.toggle('dark', dark); dark ? root.dataset.theme = 'dark' : delete root.dataset.theme; localStorage.setItem('ct-theme', dark ? 'dark' : 'light'); }
document.addEventListener('click', e => {
  const t = e.target.closest('[data-theme-toggle]'); if (!t) return;
  const dark = !root.classList.contains('dark');
  if (!document.startViewTransition || reduce) return setTheme(dark);
  const r = t.getBoundingClientRect(), x = r.left + r.width / 2, y = r.top + r.height / 2;
  const end = Math.hypot(Math.max(x, innerWidth - x), Math.max(y, innerHeight - y));
  document.startViewTransition(() => setTheme(dark)).ready.then(() => {
    root.animate({ clipPath: [`circle(0 at ${x}px ${y}px)`, `circle(${end}px at ${x}px ${y}px)`] },
      { duration: 560, easing: 'cubic-bezier(.16,1,.3,1)', pseudoElement: '::view-transition-new(root)' });
  });
});

/* ---------- 页面切换（模拟 router-view transition） ---------- */
document.addEventListener('click', e => {
  const a = e.target.closest('a[href$=".html"], a[href*=".html#"]');
  if (!a || a.target || e.metaKey || e.ctrlKey || reduce) return;
  const page = qs('.page'); if (!page) return;
  e.preventDefault(); page.classList.add('is-leaving');
  setTimeout(() => (location.href = a.href), 200);
});

/* ---------- 滚动出现 ---------- */
const io = new IntersectionObserver(es => es.forEach(en => { if (en.isIntersecting) { en.target.classList.add('is-in'); io.unobserve(en.target); } }), { threshold: .12, rootMargin: '0px 0px -40px 0px' });
function observeReveal(scope = document) {
  qsa('[data-reveal-group]', scope).forEach(g => [...g.children].forEach((c, i) => { c.setAttribute('data-reveal', ''); c.style.setProperty('--i', i % 8); }));
  qsa('[data-reveal]', scope).forEach(el => io.observe(el));
}
observeReveal(); window.ctReveal = observeReveal;

/* ---------- 按钮涟漪 ---------- */
document.addEventListener('pointerdown', e => {
  const b = e.target.closest('.btn'); if (!b || reduce) return;
  const r = b.getBoundingClientRect(), s = Math.max(r.width, r.height);
  const sp = document.createElement('span'); sp.className = 'ripple';
  sp.style.cssText = `width:${s}px;height:${s}px;left:${e.clientX - r.left - s / 2}px;top:${e.clientY - r.top - s / 2}px`;
  b.appendChild(sp); setTimeout(() => sp.remove(), 650);
});

/* ---------- Toast ---------- */
window.ctToast = (msg) => {
  const w = qs('.toast-wrap'); const t = document.createElement('div'); t.className = 'toast';
  t.innerHTML = `<span class="ok">${icon('check')}</span>${msg}`; w.appendChild(t);
  setTimeout(() => { t.classList.add('out'); setTimeout(() => t.remove(), 260); }, 2200);
};

/* ---------- 收藏：心形弹跳 + 粒子 ---------- */
const COLORS = ['#ff3d6e', '#ff6b4a', '#ffc53d', '#8b5cf6', '#14c9a2'];
document.addEventListener('click', e => {
  const b = e.target.closest('[data-like]'); if (!b) return;
  e.preventDefault(); e.stopPropagation();
  const on = b.classList.toggle('is-on');
  const cnt = b.querySelector('[data-like-count]'); if (cnt) cnt.textContent = +cnt.textContent + (on ? 1 : -1);
  if (!on || reduce) return;
  const host = b.querySelector('.heart-host') || b;
  const ring = document.createElement('span'); ring.className = 'heart-ring'; host.appendChild(ring); setTimeout(() => ring.remove(), 520);
  for (let i = 0; i < 10; i++) {
    const p = document.createElement('span'); p.className = 'particle';
    const ang = (Math.PI * 2 * i) / 10, d = 22 + Math.random() * 12;
    p.style.cssText = `background:${COLORS[i % 5]};--dx:${Math.cos(ang) * d}px;--dy:${Math.sin(ang) * d}px`;
    host.appendChild(p); setTimeout(() => p.remove(), 650);
  }
  if (b.dataset.like === 'toast') ctToast('已加入收藏，降价会第一时间提醒你');
});

/* ---------- 撒花（下单成功） ---------- */
window.ctConfetti = (x, y) => {
  if (reduce) return;
  for (let i = 0; i < 36; i++) {
    const c = document.createElement('span'); c.className = 'confetti'; c.style.background = COLORS[i % 5];
    c.style.left = x + 'px'; c.style.top = y + 'px'; document.body.appendChild(c);
    const ang = -Math.PI / 2 + (Math.random() - .5) * Math.PI * 1.1, v = 160 + Math.random() * 200;
    c.animate([{ transform: 'translate(0,0) rotate(0)', opacity: 1 },
      { transform: `translate(${Math.cos(ang) * v}px, ${Math.sin(ang) * v + 260}px) rotate(${Math.random() * 720}deg)`, opacity: 0 }],
      { duration: 1100 + Math.random() * 500, easing: 'cubic-bezier(.2,.8,.2,1)' }).onfinish = () => c.remove();
  }
};

/* ---------- 分段控件滑块 ---------- */
function initSeg(seg) {
  const pill = document.createElement('span'); pill.className = 'seg__pill'; seg.prepend(pill);
  const move = b => { pill.style.width = b.offsetWidth + 'px'; pill.style.transform = `translateX(${b.offsetLeft - 0}px)`; };
  const btns = qsa('button', seg); move(btns.find(b => b.classList.contains('is-active')) || btns[0]);
  btns.forEach(b => b.addEventListener('click', () => { btns.forEach(x => x.classList.remove('is-active')); b.classList.add('is-active'); move(b); seg.dispatchEvent(new CustomEvent('change', { detail: b.dataset.v })); }));
}
qsa('.seg').forEach(initSeg);
document.fonts && document.fonts.ready.then(() => qsa('.seg').forEach(s => { const b = qs('button.is-active', s) || qs('button', s); const p = qs('.seg__pill', s); if (p && b) { p.style.width = b.offsetWidth + 'px'; p.style.transform = `translateX(${b.offsetLeft}px)`; } }));

/* chips 单选 */
document.addEventListener('click', e => { const c = e.target.closest('[data-chips] > *'); if (!c) return; const g = c.parentElement; if (g.dataset.chips === 'multi') { c.classList.toggle('is-active'); } else { [...g.children].forEach(x => x.classList.remove('is-active')); c.classList.add('is-active'); } });

/* ---------- 数字滚动 ---------- */
qsa('[data-count]').forEach(el => {
  const to = parseFloat(el.dataset.count), dec = (el.dataset.count.split('.')[1] || '').length;
  const fmt = v => v.toLocaleString('zh-CN', { minimumFractionDigits: dec, maximumFractionDigits: dec });
  if (reduce || root.classList.contains('shot')) { el.textContent = fmt(to); return; }
  const o = new IntersectionObserver(([en]) => { if (!en.isIntersecting) return; o.disconnect(); const t0 = performance.now();
    const step = t => { const k = Math.min(1, (t - t0) / 1200), e2 = 1 - Math.pow(1 - k, 4); el.textContent = fmt(to * e2); if (k < 1) requestAnimationFrame(step); }; requestAnimationFrame(step); });
  o.observe(el);
});

/* ---------- 3D 倾斜（Hero 卡片） ---------- */
qsa('[data-tilt]').forEach(el => {
  if (reduce) return;
  el.addEventListener('pointermove', e => { const r = el.getBoundingClientRect(); const px = (e.clientX - r.left) / r.width - .5, py = (e.clientY - r.top) / r.height - .5;
    el.style.setProperty('--rx', `${-py * 10}deg`); el.style.setProperty('--ry', `${px * 12}deg`); });
  el.addEventListener('pointerleave', () => { el.style.setProperty('--rx', '0deg'); el.style.setProperty('--ry', '0deg'); });
});

/* ---------- 下单按钮状态机 ---------- */
qsa('[data-order]').forEach(btn => btn.addEventListener('click', e => {
  if (btn.dataset.state) return; btn.dataset.state = 'loading';
  const label = btn.innerHTML; btn.style.width = btn.offsetWidth + 'px';
  btn.innerHTML = '<span class="spinner"></span>正在预订…';
  setTimeout(() => {
    btn.dataset.state = 'done'; btn.classList.add('is-done'); btn.innerHTML = `<span class="check-draw">${icon('check')}</span>已预订`;
    const r = btn.getBoundingClientRect(); ctConfetti(r.left + r.width / 2, r.top + 10); ctToast('预订成功，快和卖家约个面交时间吧');
    setTimeout(() => { btn.innerHTML = label; btn.classList.remove('is-done'); delete btn.dataset.state; hydrate(btn); }, 3200);
  }, 1100);
}));
})();

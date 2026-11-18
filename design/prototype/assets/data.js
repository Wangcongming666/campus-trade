/* 示例数据（虚构，仅用于原型展示） */
window.CT_GOODS = [
  { id:1, t:'iPad Air 5 64G 星光色 带原装笔 考研党自用', p:2680, o:4799, c:'95新', art:'tablet', g:['#a5b4fc','#6366f1'], loc:'东区 · 3号宿舍楼', u:'李同学', a:['#60a5fa','#6366f1'], time:'12分钟前', like:38, reason:'与你最近浏览的商品同属一类且价格接近' },
  { id:2, t:'高等数学 同济第七版 上下册 有笔记 重点已标注', p:25, o:89, c:'9成新', art:'book', g:['#fde68a','#f59e0b'], loc:'图书馆门口', u:'王学姐', a:['#f9a8d4','#ec4899'], time:'刚刚', like:12, reason:'根据你选择的兴趣分类「教材书籍」' },
  { id:3, t:'捷安特 ATX 山地车 27速 毕业转让 送锁和车筐', p:680, o:1999, c:'8成新', art:'bike', g:['#6ee7b7','#10b981'], loc:'南门自行车棚', u:'赵同学', a:['#34d399','#059669'], time:'1小时前', like:56, reason:'同校最近热门' },
  { id:4, t:'索尼 WH-1000XM4 降噪耳机 黑色 国行', p:1050, o:2299, c:'95新', art:'headphones', g:['#c4b5fd','#7c3aed'], loc:'西区食堂', u:'陈同学', a:['#a78bfa','#7c3aed'], time:'3小时前', like:41, reason:'与你最近浏览的商品同属一类且价格接近' },
  { id:5, t:'富士 X-T30 微单 + 15-45 镜头 快门 3k', p:3200, o:5299, c:'9成新', art:'camera', g:['#fdba74','#f97316'], loc:'艺术楼', u:'林学长', a:['#fb923c','#ea580c'], time:'今天', like:67, reason:'同校最近热门' },
  { id:6, t:'Keychron K2 机械键盘 茶轴 RGB 背光', p:260, o:498, c:'95新', art:'keyboard', g:['#93c5fd','#2563eb'], loc:'计算机学院', u:'周同学', a:['#38bdf8','#0284c7'], time:'昨天', like:19, reason:'根据你选择的兴趣分类「数码电子」' },
  { id:7, t:'宿舍护眼台灯 三档调光 USB 充电', p:35, o:129, c:'99新', art:'lamp', g:['#fef08a','#eab308'], loc:'北区 · 7号楼', u:'吴同学', a:['#facc15','#ca8a04'], time:'2小时前', like:8, reason:'同校最近热门' },
  { id:8, t:'雅马哈 F310 民谣吉他 41寸 送琴包变调夹', p:420, o:899, c:'9成新', art:'guitar', g:['#fca5a5','#ef4444'], loc:'音乐厅', u:'郑同学', a:['#f87171','#dc2626'], time:'5小时前', like:24, reason:'与你最近浏览的商品同属一类且价格接近' },
  { id:9, t:'Nike Air Force 1 白色 42码 只穿过两次', p:299, o:799, c:'99新', art:'sneaker', g:['#e9d5ff','#a855f7'], loc:'体育馆', u:'孙同学', a:['#c084fc','#9333ea'], time:'今天', like:33, reason:'同校最近热门' },
  { id:10, t:'卡西欧 fx-991CN X 科学计算器 考试可用', p:68, o:159, c:'95新', art:'calc', g:['#99f6e4','#14b8a6'], loc:'理学楼', u:'钱同学', a:['#2dd4bf','#0d9488'], time:'30分钟前', like:15, reason:'根据你选择的兴趣分类「教材书籍」' },
  { id:11, t:'小米电热水壶 1.5L 毕业清仓 自取', p:20, o:79, c:'8成新', art:'kettle', g:['#bae6fd','#0ea5e9'], loc:'东区 · 5号楼', u:'冯同学', a:['#7dd3fc','#0284c7'], time:'4小时前', like:5, reason:'同校最近热门' },
  { id:12, t:'绿萝 + 多肉组合 带盆 宿舍桌面绿植', p:15, o:45, c:'全新', art:'plant', g:['#bbf7d0','#22c55e'], loc:'南区 · 2号楼', u:'何学妹', a:['#86efac','#16a34a'], time:'1天前', like:21, reason:'同校最近热门' },
  { id:13, t:'MacBook Air M1 8+256 深空灰 电池 92%', p:3999, o:7999, c:'9成新', art:'laptop', g:['#cbd5e1','#64748b'], loc:'软件学院', u:'许同学', a:['#94a3b8','#475569'], time:'2天前', like:89, reason:'与你最近浏览的商品同属一类且价格接近' },
  { id:14, t:'周杰伦演唱会门票 看台 原价转 学生证验证', p:880, o:880, c:'全新', art:'ticket', g:['#fbcfe8','#db2777'], loc:'线上转让', u:'韩同学', a:['#f472b6','#be185d'], time:'1小时前', like:102, reason:'同校最近热门' },
];
window.ctGoodsCard = (g, opt = {}) => `
<a class="goods-card" href="detail.html" data-id="${g.id}">
  <div class="goods-card__cover">
    <div data-art="${g.art}" style="--g1:${g.g[0]};--g2:${g.g[1]}"></div>
    <div class="goods-card__badges"><span class="badge badge-glass">${g.c}</span>${g.p < 50 ? '<span class="badge badge-hot">捡漏</span>' : ''}</div>
    <button class="heart-btn ${opt.liked ? 'is-on' : ''}" data-like aria-label="收藏"><span class="heart-host" style="position:relative;display:grid;place-items:center"><i data-i="heart"></i></span></button>
    <span class="goods-card__loc badge badge-glass"><i data-i="pin"></i>${g.loc}</span>
    <span class="goods-card__quick"><i data-i="eye"></i>快速查看</span>
  </div>
  <div class="goods-card__body">
    <h3 class="goods-card__title">${g.t}</h3>
    ${opt.reason ? `<div class="reason"><i data-i="sparkle"></i>${g.reason}</div>` : ''}
    <div class="price"><span class="price__now num"><small>¥</small>${g.p.toLocaleString()}</span>${g.o > g.p ? `<span class="price__origin num">¥${g.o.toLocaleString()}</span>` : ''}</div>
    <div class="goods-card__meta"><span class="avatar sm" style="--a1:${g.a[0]};--a2:${g.a[1]}">${g.u[0]}</span><span class="name">${g.u}</span><span>${g.time}</span></div>
  </div>
</a>`;

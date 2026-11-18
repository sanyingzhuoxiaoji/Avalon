const names = ['看番记录', '漫画记录', '站点手册'];
const grid = document.querySelector('.grid');
const nav = document.querySelector('nav');
const count = document.querySelector('#count');
let page = 0;
function deck(titles, progress, completedFrom = Infinity) {
  return titles.map((name, art) => ({ name, art, n: progress[art], done: art >= completedFrom }));
}
const decks = [
  deck(['擅长逃跑的殿下','Re：从零开始','正相反的你和我','脑洞学生会','某暗部的少女共栖','碧蓝之海','冰之城墙','穹庐下的魔女'], [4,11,10,4,6,11,9,12], 6),
  deck(['葬送的芙莉莲','夏目友人帐','蓝色时期','跃动青春'], [36,18,12,8])
];
function el(tag, cls, text) {
  const e = document.createElement(tag);
  if (cls) e.className = cls;
  if (text != null) e.textContent = text;
  return e;
}
function button(text, fn, cls) {
  const b = el('button', cls, text);
  b.onclick = fn;
  return b;
}
function card(record, i, list) {
  const c = el('article', 'card');
  const input = el('input', 'title');
  input.value = record.name;
  input.placeholder = '输入作品名';
  input.setAttribute('aria-label', '作品名称');
  input.oninput = () => { record.name = input.value; };
  input.onkeydown = e => {
    if (e.key !== 'Enter' || e.isComposing) return;
    list.splice(i + 1, 0, { name: '', art: (record.art + 1) % 8, n: 0, done: record.done });
    render();
    grid.querySelectorAll('input')[i + 1].focus();
  };
  const art = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  art.setAttribute('class', 'plate');
  art.setAttribute('viewBox', '0 0 240 100');
  art.setAttribute('aria-hidden', 'true');
  art.innerHTML = `<use href="plates.svg#p${record.art}"/>`;
  const paper = el('div', 'paper');
  const row = el('div', 'progress');
  const number = el('span');
  const value = el('span', 'value', record.n);
  const steps = el('div', 'steps');
  const unit = page ? '话' : '集';
  for (const d of [-1, 1]) {
    const b = button(d < 0 ? '−' : '+', () => {
      record.n = Math.max(0, record.n + d);
      value.textContent = record.n;
    });
    b.setAttribute('aria-label', (d < 0 ? '减少' : '增加') + '一' + unit);
    steps.append(b);
  }
  number.append(value, el('small', 'unit', ' ' + unit));
  row.append(number, steps);
  paper.append(el('div', 'meta', page ? 'CHAPTER · 阅读进度' : 'EPISODE · 观看进度'), row);
  const status = button(record.done ? '已完成' : page ? '在读' : '在看', () => {
    list.splice(i, 1);
    record.done = !record.done;
    const firstDone = list.findIndex(r => r.done);
    list.splice(record.done || firstDone < 0 ? list.length : firstDone, 0, record);
    render();
  }, 'status');
  status.setAttribute('aria-pressed', record.done);
  c.append(input, art, paper, status);
  return c;
}
function site(name, url) {
  const c = el('article', 'web');
  const a = el('a', '', url);
  a.href = url;
  a.target = '_blank';
  a.rel = 'noopener';
  const result = el('p', '', '尚未测试');
  const b = button('测试连接', async () => {
    b.disabled = true;
    result.textContent = '连接中…';
    const start = performance.now();
    try {
      await fetch(url, { mode:'no-cors', cache:'no-store', credentials:'omit', signal:AbortSignal.timeout(8000) });
      result.textContent = `请求耗时 ${Math.round(performance.now() - start)} ms · 未验证状态码`;
    } catch {
      result.textContent = '未能连接 · 网络或浏览器限制';
    } finally { b.disabled = false; }
  });
  c.append(el('h2', '', name), a, result, b);
  return c;
}
function render() {
  nav.replaceChildren();
  grid.replaceChildren();
  names.forEach((name, i) => {
    const b = button('', () => { page = i; render(); }, 'tab');
    b.setAttribute('aria-pressed', page === i);
    b.append(el('span', '', name));
    nav.append(b);
  });
  nav.append(el('span', 'note', page === 2 ? '网页连接 · 手动测试' : '点击标题编辑 · 回车续记'));
  if (page === 2) {
    grid.append(site('211 动漫', 'https://cn.211dm.com/'), site('Skr 动漫', 'https://skr.skr3.cc:666/'));
    count.textContent = '2 站';
  } else {
    const list = decks[page];
    grid.append(...list.map(card));
    count.textContent = `${list.length} 张`;
  }
}
render();

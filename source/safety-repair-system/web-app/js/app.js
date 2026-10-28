/* ============================================================
 * app.js — 安防维修排查网页版 UI 与交互（原生 JS，无框架）
 * 含：三级权限体系 / 登录门 / 个人中心 / 人员管理 / 工单派单
 * ============================================================ */

'use strict';

/* ---------------- 全局视图状态 ---------------- */
var currentView = 'home';
var devView = 'list';
var wiz = { kind: 'camera', current: 'c_root', stack: [] };
var quickTab = 'camera';
var quickSearch = '';
var toolTab = 'subnet';

var NAV = [
  { key: 'home', icon: '🏠', label: '首页' },
  { key: 'dashboard', icon: '📊', label: '仪表盘' },
  { key: 'devices', icon: '📋', label: '设备台账' },
  { key: 'tickets', icon: '🛠️', label: '故障工单' },
  { key: 'inspections', icon: '📝', label: '巡检记录' },
  { key: 'wizard', icon: '🧭', label: '排障向导' },
  { key: 'quick', icon: '🃏', label: '速查卡' },
  { key: 'kb', icon: '📚', label: '知识库' },
  { key: 'tools', icon: '🧰', label: '工程宝' },
  { key: 'sync', icon: '🔄', label: '数据同步' }
];

/* 角色中文名 */
var ROLE_LABEL = { admin: '管理员', supervisor: '负责人', worker: '员工' };
/* 始终展示的「暂不支持」宫格（禁用态） */
var ALWAYS_TILES = ['discover', 'ping', 'scan'];

/* ---------------- 基础工具 ---------------- */
function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
  });
}
function byId(id) { return document.getElementById(id); }
function valOf(id) { var e = byId(id); return e ? e.value : ''; }
function processingCount() { return state.tickets.filter(function (t) { return t.status === 'processing'; }).length; }
function statusLabel(s) {
  return ({ online: '在线', offline: '离线', unknown: '未知', fault: '故障', processing: '处理中', closed: '已关闭', pending: '待处理' })[s] || s || '未知';
}
function statusClass(s) { return 'st-' + s; }
function typeLabel(t) { return t === 'door' ? '门禁' : '摄像头'; }
function emptyHTML(t) { return '<div class="empty">' + esc(t || '暂无数据') + '</div>'; }

/* ---------------- 权限体系 ---------------- */
function currentUser() {
  try {
    var u = localStorage.getItem(CURRENT_USER_KEY);
    if (!u) { return null; }
    var user = state.users.find(function (x) { return x.username === u; });
    if (!user || user.status !== 'active') { return null; }
    return user;
  } catch (e) { return null; }
}
function currentRole() { var u = currentUser(); return u ? u.role : ''; }
function isAdmin() { return currentRole() === 'admin'; }
function isSupervisor() { return currentRole() === 'supervisor'; }
function isWorker() { return currentRole() === 'worker'; }
function canManageUsers() { return isAdmin(); }
function canSyncData() { return isAdmin(); }
function canManageDevices() { var r = currentRole(); return r === 'admin' || r === 'supervisor'; }
function canManageTickets() { var r = currentRole(); return r === 'admin' || r === 'supervisor'; }
function canAssignTickets() { var r = currentRole(); return r === 'admin' || r === 'supervisor'; }

/* 模块可见性：admin 全部；supervisor/worker 无『人员管理』『数据同步』 */
function canView(key) {
  var r = currentRole();
  if (!r) { return false; }
  if (r === 'admin') { return true; }
  return ['home', 'dashboard', 'devices', 'tickets', 'inspections', 'wizard', 'quick', 'kb', 'tools'].indexOf(key) >= 0;
}

/* ---------------- 导航 / 渲染 ---------------- */
function renderNav() {
  var items = NAV.filter(function (n) { return canView(n.key); });
  var html = items.map(function (n) {
    var active = n.key === currentView ? ' active' : '';
    var badge = n.key === 'tickets' ? processingCount() : 0;
    var b = badge ? '<span class="nav-badge">' + badge + '</span>' : '';
    return '<button class="nav-item' + active + '" data-action="goto" data-arg="' + n.key + '"><span class="nav-ico">' + n.icon + '</span>' + n.label + b + '</button>';
  }).join('');
  byId('topNav').innerHTML = html;
}

/* 右上角操作区：🔧 个人中心（常驻）+ 🔄 同步（仅 admin） */
function renderHeader() {
  var act = byId('headerActions');
  if (!act) { return; }
  if (!currentUser()) { act.innerHTML = ''; return; }
  var html = '<button class="btn-sync" data-action="openPersonal" title="个人中心">🔧 个人中心</button>';
  if (canSyncData()) {
    html += '<button class="btn-sync" data-action="goto" data-arg="sync" title="数据同步">🔄 同步</button>';
  }
  act.innerHTML = html;
}

function goto(view) {
  if (view !== 'home' && !canView(view)) { view = 'home'; }
  currentView = view;
  render();
}

function render() {
  if (currentView && currentView !== 'home' && !canView(currentView)) { currentView = 'home'; }
  renderNav();
  renderHeader();
  var c = byId('content');
  if (currentView === 'home') { c.innerHTML = viewHome(); }
  else if (currentView === 'dashboard') { c.innerHTML = viewDashboard(); }
  else if (currentView === 'devices') { c.innerHTML = viewDevices(); }
  else if (currentView === 'tickets') { c.innerHTML = viewTickets(); }
  else if (currentView === 'inspections') { c.innerHTML = viewInspections(); }
  else if (currentView === 'wizard') { c.innerHTML = viewWizard(); }
  else if (currentView === 'quick') { c.innerHTML = viewQuick(); }
  else if (currentView === 'kb') { c.innerHTML = viewKB(); }
  else if (currentView === 'tools') { c.innerHTML = viewTools(); bindToolInputs(); computeTool(); }
  else if (currentView === 'sync') { c.innerHTML = viewSync(); }
  else if (currentView === 'users') { c.innerHTML = viewUsers(); }
  window.scrollTo(0, 0);
}

/* ---------------- 首页宫格 ---------------- */
function viewHome() {
  var tiles = [
    { key: 'dashboard', icon: '📊', title: '仪表盘', sub: '统计与缺陷地图' },
    { key: 'devices', icon: '📋', title: '设备台账', sub: '台账/拓扑/导入' },
    { key: 'tickets', icon: '🛠️', title: '故障工单', sub: '工单管理', badge: processingCount() },
    { key: 'inspections', icon: '📝', title: '巡检记录', sub: '巡检管理' },
    { key: 'wizard', icon: '🧭', title: '排障向导', sub: '决策树' },
    { key: 'quick', icon: '🃏', title: '速查卡', sub: '排障速查' },
    { key: 'kb', icon: '📚', title: '知识库', sub: '红线与口诀' },
    { key: 'tools', icon: '🧰', title: '工程宝工具', sub: '计算/速查' },
    { key: 'sync', icon: '🔄', title: '数据同步', sub: 'JSON 互通' },
    { key: 'discover', icon: '🔍', title: '设备发现', sub: '请使用安卓版', disabled: true },
    { key: 'ping', icon: '📡', title: '网络 Ping', sub: '请使用安卓版', disabled: true },
    { key: 'scan', icon: '🔌', title: '端口扫描', sub: '请使用安卓版', disabled: true }
  ];
  /* 人员管理（仅 admin 可见） */
  if (isAdmin()) {
    tiles.push({ key: 'users', icon: '👥', title: '人员管理', sub: '账号与权限' });
  }
  tiles = tiles.filter(function (t) {
    if (t.disabled) { return ALWAYS_TILES.indexOf(t.key) >= 0; }
    return canView(t.key);
  });
  var cards = tiles.map(function (t) {
    if (t.disabled) {
      return '<div class="tile tile-disabled"><div class="tile-ico">' + t.icon + '</div><div class="tile-title">' + t.title + '</div><div class="tile-sub">' + t.sub + '</div></div>';
    }
    var badge = t.badge ? '<span class="tile-badge">' + t.badge + '</span>' : '';
    return '<div class="tile" data-action="goto" data-arg="' + t.key + '">' + badge + '<div class="tile-ico">' + t.icon + '</div><div class="tile-title">' + t.title + '</div><div class="tile-sub">' + t.sub + '</div></div>';
  }).join('');
  var u = currentUser();
  var welcome = u ? '当前登录：' + esc(u.name || u.username) + '（' + (ROLE_LABEL[u.role] || u.role) + '）' : '';
  return '<section class="hero"><div class="hero-title">安防维修排查 · 工作台</div>'
    + '<div class="hero-sub">本地数据 · 与安卓版 JSON 互通 · 当前待处理工单 ' + processingCount() + ' 单 ｜ ' + welcome + '</div></section>'
    + '<section class="grid">' + cards + '</section>';
}

/* ---------------- 仪表盘 ---------------- */
function parseFloor(f) { var m = String(f || '').match(/(\d+)/); return m ? parseInt(m[1], 10) : 0; }

function defectMapSVG() {
  var floors = state.devices.map(function (d) { return d.floor; }).filter(Boolean);
  floors = Array.from(new Set(floors)).sort(function (a, b) { return parseFloor(b) - parseFloor(a); });
  if (!floors.length) { return '<div class="empty">暂无楼层数据</div>'; }
  var rowH = 56, pad = 10, w = 360, h = pad * 2 + floors.length * rowH;
  var body = '';
  floors.forEach(function (f, i) {
    var y = pad + i * rowH;
    var devs = state.devices.filter(function (d) { return d.floor === f; });
    body += '<rect x="' + pad + '" y="' + y + '" width="' + (w - 2 * pad) + '" height="' + (rowH - 10) + '" rx="10" fill="#f1f6fd" stroke="#d6e6fb"/>';
    body += '<text x="' + (pad + 12) + '" y="' + (y + rowH / 2 - 2) + '" font-size="15" font-weight="700" fill="#1565C0">' + esc(f) + '</text>';
    body += '<text x="' + (pad + 12) + '" y="' + (y + rowH / 2 + 15) + '" font-size="11" fill="#5b6b7f">' + devs.length + ' 台设备</text>';
    devs.forEach(function (d, j) {
      var cx = w - pad - 20 - j * 22, cy = y + rowH / 2 - 5;
      var isF = isFaultDevice(d.id);
      body += '<circle cx="' + cx + '" cy="' + cy + '" r="7" fill="' + (isF ? '#d1242f' : '#1E88E5') + '"' + (isF ? ' stroke="#fff" stroke-width="2"' : '') + '/>';
    });
  });
  return '<svg viewBox="0 0 ' + w + ' ' + h + '" width="100%" style="max-width:380px">' + body + '</svg>';
}

function viewDashboard() {
  var devs = state.devices.length;
  var online = state.devices.filter(function (d) { return d.status === 'online'; }).length;
  var fault = Object.keys(faultDeviceIds()).length;
  var tk = state.tickets.length;
  var proc = processingCount();
  var insp = state.inspections.length;

  var stats = '<div class="stats">'
    + statCard(devs, '设备总数', false)
    + statCard(online, '在线设备', false)
    + statCard(fault, '故障设备', true)
    + statCard(tk, '工单总数', false)
    + statCard(proc, '处理中工单', true)
    + statCard(insp, '巡检次数', false)
    + '</div>';

  var recent = state.tickets.slice().sort(function (a, b) { return String(b.created).localeCompare(String(a.created)); }).slice(0, 5);
  var recentHTML = recent.length ? '<table class="tbl"><thead><tr><th>工单号</th><th>标题</th><th>状态</th><th>日期</th></tr></thead><tbody>'
    + recent.map(function (t) {
      return '<tr class="' + (t.status === 'processing' ? 'fault' : '') + '"><td><b>' + esc(t.code) + '</b></td><td>' + esc(t.title) + '</td><td><span class="st ' + (t.status === 'processing' ? 'st-processing' : statusClass(t.status)) + '">' + statusLabel(t.status) + '</span></td><td>' + esc(t.created) + '</td></tr>';
    }).join('') + '</tbody></table>' : emptyHTML('暂无工单');

  var faultList = Object.keys(faultDeviceIds());
  var faultHTML = faultList.length ? '<ul class="fault-list">' + faultList.map(function (id) {
    var d = state.devices.find(function (x) { return x.id === Number(id); });
    if (!d) { return ''; }
    return '<li>🔴 <b>' + esc(d.code) + '</b> ' + esc(d.brand) + ' ' + esc(d.model) + ' <span class="muted">(' + esc(d.location) + ')</span> — 存在处理中工单</li>';
  }).join('') + '</ul>' : emptyHTML('当前无故障设备');

  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>仪表盘</h2></div></div>'
    + stats
    + '<div class="dash-cols">'
    + '<div class="card"><h3 style="margin:0 0 10px;color:#123a63">最近工单</h3>' + recentHTML + '</div>'
    + '<div class="card"><h3 style="margin:0 0 10px;color:#d1242f">故障设备（红色清单）</h3>' + faultHTML + '</div>'
    + '</div>'
    + '<div class="card"><h3 style="margin:0 0 10px;color:#123a63">缺陷分布图（按楼层 · 红点为故障设备）</h3><div class="defect-wrap">' + defectMapSVG() + '</div></div>';
}
function statCard(num, label, red) {
  return '<div class="stat-card' + (red ? ' red' : '') + '"><div class="stat-num">' + num + '</div><div class="stat-lbl">' + label + '</div></div>';
}

/* ---------------- 设备台账 ---------------- */
function devRowHTML(d) {
  var fault = isFaultDevice(d.id);
  var parent = '';
  if (d.parent_id) {
    var p = state.devices.find(function (x) { return x.id === d.parent_id; });
    if (p) { parent = ' <span class="muted">↳' + esc(p.code) + '</span>'; }
  }
  var ops = canManageDevices()
    ? '<button class="mini" data-action="editDevice" data-arg="' + d.id + '">编辑</button> <button class="mini danger" data-action="delDevice" data-arg="' + d.id + '">删除</button>'
    : '<span class="muted">只读</span>';
  return '<tr class="' + (fault ? 'fault' : '') + '">'
    + '<td><b>' + esc(d.code) + '</b></td>'
    + '<td><span class="tag tag-' + d.type + '">' + typeLabel(d.type) + '</span></td>'
    + '<td>' + esc(d.brand) + ' ' + esc(d.model) + '</td>'
    + '<td>' + esc(d.ip) + '</td>'
    + '<td><span class="st ' + statusClass(d.status) + '">' + statusLabel(d.status) + '</span></td>'
    + '<td>' + esc(d.location) + parent + '</td>'
    + '<td class="ops">' + ops + '</td>'
    + '</tr>';
}
function devListHTML() {
  if (!state.devices.length) { return emptyHTML(canManageDevices() ? '暂无设备，点击「新增设备」' : '暂无设备'); }
  var rows = state.devices.slice().sort(function (a, b) { return Number(b.id) - Number(a.id); }).map(devRowHTML).join('');
  return '<table class="tbl"><thead><tr><th>编号</th><th>类型</th><th>品牌/型号</th><th>IP</th><th>状态</th><th>位置</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table>';
}
function devGroupHTML() {
  if (!state.devices.length) { return emptyHTML('暂无设备'); }
  var floors = Array.from(new Set(state.devices.map(function (d) { return d.floor || '未分层'; })));
  return floors.map(function (f) {
    var ds = state.devices.filter(function (d) { return (d.floor || '未分层') === f; });
    var items = ds.map(function (d) {
      var f2 = isFaultDevice(d.id);
      return '<li class="g-item' + (f2 ? ' fault' : '') + '"><span class="g-dot ' + (f2 ? 'red' : 'blue') + '"></span><b>' + esc(d.code) + '</b> ' + esc(d.brand) + ' ' + esc(d.model) + ' <span class="muted">' + esc(d.location) + '</span></li>';
    }).join('');
    return '<div class="group"><div class="group-h"><span class="group-bar"></span>' + esc(f) + ' <span class="muted">(' + ds.length + ')</span></div><ul class="g-list">' + items + '</ul></div>';
  }).join('');
}
function devTreeHTML() {
  if (!state.devices.length) { return emptyHTML('暂无设备'); }
  var byIdMap = {}; state.devices.forEach(function (d) { byIdMap[d.id] = d; });
  var roots = state.devices.filter(function (d) { return !d.parent_id || !byIdMap[d.parent_id]; });
  return '<div class="tree">' + roots.map(function (r) {
    var kids = state.devices.filter(function (d) { return d.parent_id === r.id; });
    var k = kids.map(function (c) {
      var f = isFaultDevice(c.id);
      return '<li class="tree-child' + (f ? ' fault' : '') + '">└ <b>' + esc(c.code) + '</b> ' + esc(c.brand) + ' ' + esc(c.model) + ' <span class="muted">' + esc(c.location) + '</span></li>';
    }).join('');
    var rf = isFaultDevice(r.id);
    return '<div class="tree-node' + (rf ? ' fault' : '') + '"><span class="tree-ico">📦</span> <b>' + esc(r.code) + '</b> ' + esc(r.brand) + ' ' + esc(r.model) + ' <span class="muted">' + esc(r.location) + '</span></div>' + (k ? '<ul class="tree-kids">' + k + '</ul>' : '');
  }).join('') + '</div>';
}
function viewDevices() {
  var tabs = '<div class="seg">'
    + '<button class="seg-btn' + (devView === 'list' ? ' on' : '') + '" data-action="devView" data-arg="list">列表</button>'
    + '<button class="seg-btn' + (devView === 'group' ? ' on' : '') + '" data-action="devView" data-arg="group">分组</button>'
    + '<button class="seg-btn' + (devView === 'tree' ? ' on' : '') + '" data-action="devView" data-arg="tree">树形拓扑</button>'
    + '</div>';
  var actions = '';
  if (canManageDevices()) {
    actions = '<div class="row-actions">'
      + '<button class="btn btn-primary" data-action="addDevice">＋ 新增设备</button>'
      + '<button class="btn" data-action="exportDeviceCSV">导出 CSV</button>'
      + '<button class="btn" data-action="importDeviceCSV">导入 CSV</button>'
      + '<button class="btn" data-action="devCSVTemplate">CSV 模板</button>'
      + '</div>';
  }
  var body = devView === 'list' ? devListHTML() : (devView === 'group' ? devGroupHTML() : devTreeHTML());
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>设备台账</h2></div>' + actions + '</div>' + tabs + '<div class="card">' + body + '</div>';
}

/* ---------------- 通用表单字段 ---------------- */
function fld(label, id, type, value, ph, options, rawOptions) {
  value = value == null ? '' : value;
  ph = ph || '';
  var ctrl;
  if (type === 'select') {
    var opts = rawOptions != null ? rawOptions : (options ? options.map(function (o) {
      return '<option value="' + o[0] + '" ' + (value === o[0] ? 'selected' : '') + '>' + o[1] + '</option>';
    }).join('') : '');
    ctrl = '<select id="' + id + '">' + opts + '</select>';
  } else if (type === 'textarea') {
    ctrl = '<textarea id="' + id + '" rows="3" placeholder="' + esc(ph) + '">' + esc(value) + '</textarea>';
  } else {
    ctrl = '<input id="' + id + '" type="' + type + '" value="' + esc(value) + '" placeholder="' + esc(ph) + '">';
  }
  return '<label class="fld"><span>' + esc(label) + '</span>' + ctrl + '</label>';
}

/* ---------------- 弹窗 ---------------- */
function openModal(title, bodyHtml) {
  var root = byId('modalRoot');
  // 先强制 blur 掉任何正在展开的原生 select（Windows 原生 select 不会自动收起）
  try { document.activeElement.blur(); } catch (e) {}
  document.querySelectorAll('select').forEach(function (s) {
    try { s.blur(); } catch (e) {}
  });
  root.innerHTML = '<div class="modal-backdrop" data-action="closeModal"><div class="modal" onclick="event.stopPropagation()">'
    + '<div class="modal-head"><span>' + esc(title) + '</span><button class="modal-x" data-action="closeModal" tabindex="0">✕</button></div>'
    + '<div class="modal-body">' + bodyHtml + '</div></div></div>';
  root.classList.add('show');
  // mousedown 兜底：Windows 上部分场景 click 被吞，mousedown 必然触发
  root.addEventListener('mousedown', modalMouseHandler, true);
  root.addEventListener('click', modalBackdropHandler, true);
}
function modalMouseHandler(e) {
  var t = e.target;
  if (!t || !t.closest) { return; }
  var el = t.closest('[data-action]');
  if (el && el.getAttribute('data-action') === 'closeModal') {
    closeModal();
  }
}
function modalBackdropHandler(e) {
  if (e.target === byId('modalRoot') || e.target.classList.contains('modal-backdrop')) {
    closeModal();
  }
}
function closeModal() {
  var r = byId('modalRoot');
  if (!r) { return; }
  r.classList.remove('show');
  r.removeEventListener('mousedown', modalMouseHandler, true);
  r.removeEventListener('click', modalBackdropHandler, true);
  r.innerHTML = '';
}
function toast(msg) {
  var t = byId('toast');
  t.textContent = msg;
  t.classList.add('show');
  clearTimeout(t._t);
  t._t = setTimeout(function () { t.classList.remove('show'); }, 2200);
}

/* ---------------- 设备表单 ---------------- */
function openDeviceModal(id) {
  var d = id ? state.devices.find(function (x) { return x.id === Number(id); }) : null;
  var parentOpts = '<option value="0">（无父级）</option>' + state.devices
    .filter(function (x) { return !id || x.id !== Number(id); })
    .map(function (x) { return '<option value="' + x.id + '" ' + (d && d.parent_id === x.id ? 'selected' : '') + '>' + esc(x.code) + ' ' + esc(x.brand) + ' ' + esc(x.model) + '</option>'; }).join('');
  var html = '<div class="form">'
    + fld('编号 code', 'd_code', 'text', d ? d.code : '', '留空自动生成 S 编号')
    + '<div class="form-2">'
    + fld('类型', 'd_type', 'select', d ? d.type : 'camera', null, [['camera', '摄像头'], ['door', '门禁']])
    + fld('状态', 'd_status', 'select', d ? d.status : 'online', null, [['online', '在线'], ['offline', '离线'], ['unknown', '未知'], ['fault', '故障']])
    + '</div>'
    + '<div class="form-2">' + fld('品牌', 'd_brand', 'text', d ? d.brand : '') + fld('型号', 'd_model', 'text', d ? d.model : '') + '</div>'
    + fld('IP 地址', 'd_ip', 'text', d ? d.ip : '')
    + fld('位置', 'd_location', 'text', d ? d.location : '', '如 1F大堂东侧')
    + fld('父级设备', 'd_parent', 'select', d ? String(d.parent_id) : '0', null, null, parentOpts)
    + fld('备注', 'd_note', 'textarea', d ? d.note : '')
    + '<div class="form-actions"><button class="btn btn-primary" data-action="saveDevice">保存</button><button class="btn" data-action="closeModal">取消</button></div>'
    + '</div>';
  openModal(id ? '编辑设备' : '新增设备', html);
}
function saveDevice() {
  var d = {
    code: valOf('d_code').trim(),
    type: valOf('d_type'),
    brand: valOf('d_brand'),
    model: valOf('d_model'),
    ip: valOf('d_ip'),
    status: valOf('d_status'),
    location: valOf('d_location'),
    note: valOf('d_note'),
    parent_id: Number(valOf('d_parent')) || 0
  };
  upsertDevice(d);
  closeModal(); toast('设备已保存'); render();
}
function delDevice(id) {
  if (!confirm('确定删除该设备？关联工单不会被删除。')) { return; }
  deleteDevice(id); toast('已删除'); render();
}

/* ---------------- 工单 + 派单 ---------------- */
function visibleTickets() {
  var u = currentUser();
  var list = state.tickets.slice();
  if (isWorker()) {
    list = list.filter(function (t) { return t.assigned === u.username; });
  }
  return list.sort(function (a, b) { return Number(b.id) - Number(a.id); });
}
/* 可指派对象：员工 / 负责人 */
function assignOptions(selected) {
  return '<option value="">未指派</option>' + state.users
    .filter(function (x) { return x.role === 'worker' || x.role === 'supervisor'; })
    .map(function (x) {
      var sel = x.username === selected ? 'selected' : '';
      return '<option value="' + esc(x.username) + '" ' + sel + '>' + esc(x.name || x.username) + '（' + (ROLE_LABEL[x.role] || x.role) + '）</option>';
    }).join('');
}
function ticketRowHTML(t) {
  var fault = t.status === 'processing';
  var dev = t.device_id ? state.devices.find(function (x) { return x.id === Number(t.device_id); }) : null;
  var assignee = t.assigned ? state.users.find(function (x) { return x.username === t.assigned; }) : null;
  var assignCell;
  if (canAssignTickets()) {
    assignCell = '<select class="mini-sel" data-action="assignTicket" data-arg="' + t.id + '">' + assignOptions(t.assigned) + '</select>';
  } else {
    assignCell = assignee ? esc(assignee.name || assignee.username) : '<span class="muted">—</span>';
  }
  var ops;
  if (canManageTickets()) {
    ops = '<button class="mini" data-action="editTicket" data-arg="' + t.id + '">编辑</button> <button class="mini danger" data-action="delTicket" data-arg="' + t.id + '">删除</button>';
  } else {
    if (t.status === 'pending') { ops = '<button class="mini" data-action="advanceTicket" data-arg="' + t.id + '">待处理→处理中</button>'; }
    else if (t.status === 'processing') { ops = '<button class="mini" data-action="advanceTicket" data-arg="' + t.id + '">处理中→已结</button>'; }
    else { ops = '<span class="muted">已结</span>'; }
  }
  return '<tr class="' + (fault ? 'fault' : '') + '">'
    + '<td><b>' + esc(t.code) + '</b></td>'
    + '<td>' + esc(t.title) + '</td>'
    + '<td>' + (dev ? '<span class="link">' + esc(dev.code) + '</span>' : '<span class="muted">' + esc(t.device || '未关联') + '</span>') + '</td>'
    + '<td>' + esc(t.ftype) + '</td>'
    + '<td><span class="lv lv-' + esc(t.level) + '">' + esc(t.level) + '</span></td>'
    + '<td>' + assignCell + '</td>'
    + '<td><span class="st ' + (fault ? 'st-processing' : statusClass(t.status)) + '">' + statusLabel(t.status) + '</span></td>'
    + '<td class="ops">' + ops + '</td>'
    + '</tr>';
}
function ticketListHTML(list) {
  list = list || state.tickets;
  if (!list.length) { return emptyHTML('暂无工单'); }
  var rows = list.map(ticketRowHTML).join('');
  return '<table class="tbl"><thead><tr><th>工单号</th><th>标题</th><th>关联设备</th><th>类型</th><th>级别</th><th>指派给</th><th>状态</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table>';
}
function viewTickets() {
  var actions;
  if (isWorker()) {
    var me = currentUser();
    actions = '<div class="row-actions"><span class="muted">仅显示指派给「' + esc(me.name || me.username) + '」的工单（' + visibleTickets().length + '）</span></div>';
  } else {
    actions = '<div class="row-actions"><button class="btn btn-primary" data-action="addTicket">＋ 新增工单</button></div>';
  }
  var list = visibleTickets();
  var body = list.length ? ticketListHTML(list) : emptyHTML(isWorker() ? '暂无指派给您的工单' : '暂无工单');
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>故障工单</h2></div>' + actions + '</div><div class="card">' + body + '</div>';
}
function openTicketModal(id) {
  var t = id ? state.tickets.find(function (x) { return x.id === Number(id); }) : null;
  var devOpts = '<option value="0">（未关联）</option>' + state.devices.map(function (x) {
    return '<option value="' + x.id + '" ' + (t && Number(t.device_id) === x.id ? 'selected' : '') + '>' + esc(x.code) + ' ' + esc(x.brand) + ' ' + esc(x.model) + ' (' + esc(x.location) + ')</option>';
  }).join('');
  var assignHtml = '';
  if (canAssignTickets()) {
    assignHtml = fld('指派给', 't_assigned', 'select', t ? (t.assigned || '') : '', null, null, assignOptions(t ? t.assigned : ''));
  }
  var html = '<div class="form">'
    + fld('工单号 code', 't_code', 'text', t ? t.code : '', '留空自动生成')
    + fld('关联设备', 't_device', 'select', t ? String(t.device_id) : '0', null, null, devOpts)
    + fld('标题', 't_title', 'text', t ? t.title : '')
    + '<div class="form-2">'
    + fld('故障类型', 't_ftype', 'text', t ? t.ftype : '')
    + fld('级别', 't_level', 'select', t ? t.level : 'P3', null, [['P1', 'P1'], ['P2', 'P2'], ['P3', 'P3']])
    + '</div>'
    + assignHtml
    + fld('状态', 't_status', 'select', t ? t.status : 'pending', null, [['pending', '待处理'], ['processing', '处理中'], ['closed', '已关闭']])
    + fld('故障描述', 't_description', 'textarea', t ? t.description : '')
    + fld('处理方案', 't_solution', 'textarea', t ? t.solution : '')
    + '<div class="form-actions"><button class="btn btn-primary" data-action="saveTicket">保存</button><button class="btn" data-action="closeModal">取消</button></div>'
    + '</div>';
  openModal(id ? '编辑工单' : '新增工单', html);
}
function saveTicket() {
  var t = {
    code: valOf('t_code').trim(),
    device_id: Number(valOf('t_device')) || 0,
    title: valOf('t_title'),
    ftype: valOf('t_ftype'),
    level: valOf('t_level'),
    status: valOf('t_status'),
    description: valOf('t_description'),
    solution: valOf('t_solution'),
    assigned: byId('t_assigned') ? (valOf('t_assigned') || '') : (currentTicketAssigned || '')
  };
  upsertTicket(t);
  closeModal(); toast('工单已保存'); render();
}
/* 用于「仅编辑态保留原 assigned」（worker 不显示指派字段） */
var currentTicketAssigned = '';
function delTicket(id) {
  if (!confirm('确定删除该工单？')) { return; }
  deleteTicket(id); toast('已删除'); render();
}
function assignTicket(id, username) {
  var t = state.tickets.find(function (x) { return x.id === Number(id); });
  if (!t) { return; }
  t.assigned = username || '';
  save();
  toast('已指派' + (username ? '：' + username : '（取消指派）'));
  render();
}
function advanceTicket(id) {
  var t = state.tickets.find(function (x) { return x.id === Number(id); });
  if (!t) { return; }
  if (t.status === 'pending') { t.status = 'processing'; }
  else if (t.status === 'processing') { t.status = 'closed'; t.closed = today(); }
  else { return; }
  save(); toast('状态已更新'); render();
}

/* ---------------- 巡检 ---------------- */
function inspRowHTML(i) {
  return '<tr><td>' + esc(i.title) + '</td><td>' + esc(i.date) + '</td><td>' + esc(i.result) + '</td><td>' + esc(i.note) + '</td>'
    + '<td class="ops"><button class="mini" data-action="editInspection" data-arg="' + i.id + '">编辑</button> <button class="mini danger" data-action="delInspection" data-arg="' + i.id + '">删除</button></td></tr>';
}
function inspListHTML() {
  if (!state.inspections.length) { return emptyHTML('暂无巡检记录'); }
  var rows = state.inspections.slice().sort(function (a, b) { return String(b.date).localeCompare(String(a.date)); }).map(inspRowHTML).join('');
  return '<table class="tbl"><thead><tr><th>标题</th><th>日期</th><th>结果</th><th>备注</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table>';
}
function viewInspections() {
  var actions = '<div class="row-actions"><button class="btn btn-primary" data-action="addInspection">＋ 新增巡检</button></div>';
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>巡检记录</h2></div>' + actions + '</div><div class="card">' + inspListHTML() + '</div>';
}
function openInspectionModal(id) {
  var i = id ? state.inspections.find(function (x) { return x.id === Number(id); }) : null;
  var html = '<div class="form">'
    + fld('标题', 'i_title', 'text', i ? i.title : '')
    + fld('日期', 'i_date', 'date', i ? i.date : today())
    + fld('结果', 'i_result', 'text', i ? i.result : '', '如 正常 / 隐患')
    + fld('备注', 'i_note', 'textarea', i ? i.note : '')
    + '<div class="form-actions"><button class="btn btn-primary" data-action="saveInspection">保存</button><button class="btn" data-action="closeModal">取消</button></div></div>';
  openModal(id ? '编辑巡检' : '新增巡检', html);
}
function saveInspection() {
  var o = { title: valOf('i_title'), date: valOf('i_date'), result: valOf('i_result'), note: valOf('i_note') };
  upsertInspection(o);
  closeModal(); toast('已保存'); render();
}
function delInspection(id) {
  if (!confirm('确定删除？')) { return; }
  deleteInspection(id); toast('已删除'); render();
}

/* ---------------- 排障向导 ---------------- */
function wizardTree() { return wiz.kind === 'camera' ? CAMERA_WIZARD : DOOR_WIZARD; }
function wizardNode(id) { var t = wizardTree(); return t.find(function (n) { return n.id === id; }) || t[0]; }
function viewWizard() {
  var tabs = '<div class="seg">'
    + '<button class="seg-btn' + (wiz.kind === 'camera' ? ' on' : '') + '" data-action="wizTab" data-arg="camera">摄像头</button>'
    + '<button class="seg-btn' + (wiz.kind === 'door' ? ' on' : '') + '" data-action="wizTab" data-arg="door">门禁</button></div>';
  var node = wizardNode(wiz.current);
  var kindLabel = { branch: '分类', judge: '判断', fix: '处理' }[node.kind];
  var body = '<div class="wz-node wz-' + node.kind + '">';
  body += '<div class="wz-kind">' + kindLabel + (node.tag ? ' · ' + esc(node.tag) : '') + '</div>';
  body += '<div class="wz-text">' + esc(node.text) + '</div>';
  if (node.kind === 'branch') {
    body += '<div class="wz-opts">' + node.opts.map(function (o, idx) {
      return '<button class="wz-opt" data-action="wizGoto" data-arg="' + node.gotos[idx] + '">' + esc(o) + '</button>';
    }).join('') + '</div>';
  } else if (node.kind === 'judge') {
    body += '<div class="wz-opts"><button class="wz-opt yes" data-action="wizGoto" data-arg="' + node.yes + '">是</button><button class="wz-opt no" data-action="wizGoto" data-arg="' + node.no + '">否</button></div>';
  } else {
    body += '<div class="wz-opts"><button class="wz-opt" data-action="wizBack">← 返回</button><button class="wz-opt" data-action="wizReset">↺ 重新开始</button></div>';
  }
  body += '</div>';
  var ctrl = '<div class="row-actions"><button class="btn" data-action="wizReset">↺ 重置向导</button></div>';
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>排障向导</h2></div></div>' + tabs + '<div class="card wz-card">' + body + '</div>' + ctrl;
}
function wizTab(kind) { wiz.kind = kind; wiz.current = kind === 'camera' ? 'c_root' : 'd_root'; wiz.stack = []; render(); }
function wizGoto(id) { wiz.stack.push(wiz.current); wiz.current = id; render(); }
function wizBack() { if (wiz.stack.length) { wiz.current = wiz.stack.pop(); render(); } }
function wizReset() { wiz.current = wiz.kind === 'camera' ? 'c_root' : 'd_root'; wiz.stack = []; render(); }

/* ---------------- 速查卡 ---------------- */
function quickRows() { return quickTab === 'camera' ? CAMERA_QUICK : DOOR_QUICK; }
function quickTableHTML() {
  var data = quickRows();
  var filtered = data.filter(function (r) {
    if (!quickSearch) { return true; }
    var s = quickSearch.toLowerCase();
    return r[0].toLowerCase().indexOf(s) >= 0 || (r[1] || '').toLowerCase().indexOf(s) >= 0 || (r[2] || '').toLowerCase().indexOf(s) >= 0;
  });
  if (!filtered.length) { return emptyHTML('无匹配'); }
  var rows = filtered.map(function (r) {
    return '<tr><td><b>' + esc(r[0]) + '</b></td><td>' + esc(r[1]) + '</td><td class="muted">' + esc(r[2]) + '</td></tr>';
  }).join('');
  return '<table class="tbl"><thead><tr><th>现象</th><th>先做（前2步）</th><th>若未恢复</th></tr></thead><tbody>' + rows + '</tbody></table>';
}
function viewQuick() {
  var tabs = '<div class="seg">'
    + '<button class="seg-btn' + (quickTab === 'camera' ? ' on' : '') + '" data-action="quickTab" data-arg="camera">摄像头</button>'
    + '<button class="seg-btn' + (quickTab === 'door' ? ' on' : '') + '" data-action="quickTab" data-arg="door">门禁</button></div>';
  var search = '<div class="search"><input id="q_search" data-action="quickSearch" value="' + esc(quickSearch) + '" placeholder="搜索现象 / 步骤…"></div>';
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>速查卡</h2></div></div>' + tabs + search + '<div class="card"><div id="quickList">' + quickTableHTML() + '</div></div>';
}
function onQuickSearch(v) {
  quickSearch = v;
  var el = byId('quickList');
  if (el) { el.innerHTML = quickTableHTML(); }
}

/* ---------------- 知识库 ---------------- */
function viewKB() {
  var kb = KB.map(function (k) {
    return '<div class="kb-card"><div class="kb-h"><span class="kb-ico">📌</span>' + esc(k[0]) + '</div><div class="kb-b">' + esc(k[1]).replace(/\n/g, '<br>') + '</div></div>';
  }).join('');
  var red = '<div class="redline"><div class="rl-h">🚫 红线</div><div class="rl-b">' + esc(REDLINE_CAM).replace(/\n/g, '<br>') + '</div><div class="rl-b">' + esc(REDLINE_DOOR).replace(/\n/g, '<br>') + '</div></div>';
  var mantra = '<div class="mantra"><div class="mn-h">🎯 口诀</div><div class="mn-b">' + esc(MANTRA).replace(/\n/g, '<br>') + '</div></div>';
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>知识库</h2></div></div><div class="grid-2">' + kb + '</div>' + red + mantra;
}

/* ---------------- 工程宝工具（修复2：纵向标签，窄屏纯竖版） ---------------- */
function brandTable(rows, headers) {
  var b = rows.map(function (r) {
    return '<tr>' + r.map(function (c, i) { return (i === 4 || i === 3) ? '<td><code>' + esc(c) + '</code></td>' : '<td>' + esc(c) + '</td>'; }).join('') + '</tr>';
  }).join('');
  return '<table class="tbl"><thead><tr>' + headers.map(function (h) { return '<th>' + h + '</th>'; }).join('') + '</tr></thead><tbody>' + b + '</tbody></table>';
}
function toolBody() {
  var h = '';
  if (toolTab === 'subnet') {
    h = fld('IP 地址', 't_ip', 'text', '10.10.10.0') + fld('子网前缀', 't_prefix', 'number', '24', '0-32');
  } else if (toolTab === 'poe') {
    h = fld('端口数', 't_ports', 'number', '8') + fld('单口功率(W)', 't_pp', 'number', '30', 'af≈15.4 at≈30 bt≈60-90');
  } else if (toolTab === 'storage') {
    h = fld('单路码流(Mbps)', 't_br', 'number', '4') + fld('保存天数', 't_days', 'number', '30') + fld('路数', 't_ch', 'number', '16');
  } else if (toolTab === 'bandwidth') {
    h = fld('路数', 't_ch2', 'number', '16') + fld('单路码流(Mbps)', 't_br2', 'number', '4') + fld('开销(%)', 't_ov', 'number', '20');
  } else if (toolTab === 'pass') {
    var rows = DEFAULT_PASS.map(function (p) {
      return '<tr><td>' + esc(p[0]) + '</td><td>' + esc(p[1]) + '</td><td><code>' + esc(p[2]) + '</code></td><td class="red">' + esc(p[3]) + '</td></tr>';
    }).join('');
    h = '<p class="muted">仅授权设备初始化/上线使用，上线后必须修改默认口令。</p><table class="tbl"><thead><tr><th>品牌</th><th>用户名</th><th>默认密码</th><th>说明</th></tr></thead><tbody>' + rows + '</tbody></table>';
  } else if (toolTab === 'rtsp') {
    var brandOpts = ['海康威视', '大华', '宇视', '华为', 'TP-LINK', '天地伟业', '同为', '博世', '安讯士', '汉邦高科'].map(function (b) {
      return '<option value="' + b + '">' + b + '</option>';
    }).join('');
    h = fld('品牌', 't_rbrand', 'select', '海康威视', null, null, brandOpts)
      + fld('设备 IP', 't_rip', 'text', '10.10.10.21')
      + fld('用户名', 't_ruser', 'text', 'admin')
      + fld('密码', 't_rpass', 'text', '12345');
  } else if (toolTab === 'brand') {
    h = '<h4>摄像头品牌</h4>' + brandTable(CAMERA_BRANDS, ['名称', '默认端口', '默认用户', '默认密码', 'RTSP 模板', 'ONVIF', 'SDK/对接'])
      + '<h4 style="margin-top:18px">门禁品牌</h4>' + brandTable(DOOR_BRANDS, ['名称', '协议', '端口', 'SDK/平台', '对接方式', '类型']);
  }
  return '<div class="tool-form" id="toolForm">' + h + '</div><div id="toolResult" class="tool-result"></div>';
}
function viewTools() {
  var tabs = ['subnet', 'poe', 'storage', 'bandwidth', 'pass', 'rtsp', 'brand'];
  var labels = { subnet: '子网计算', poe: 'PoE 预算', storage: '存储计算', bandwidth: '带宽计算', pass: '默认密码', rtsp: 'RTSP 生成', brand: '品牌目录' };
  var sel = '<div class="tool-tabs">' + tabs.map(function (t) {
    return '<button class="tool-tab' + (toolTab === t ? ' on' : '') + '" data-action="toolTab" data-arg="' + t + '">' + labels[t] + '</button>';
  }).join('') + '</div>';
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>工程宝工具</h2></div></div>' + sel + '<div class="card">' + toolBody() + '</div>';
}
function resItem(k, v) { return '<div class="res-item"><div class="res-k">' + k + '</div><div class="res-v">' + v + '</div></div>'; }
function computeTool() {
  var r = byId('toolResult'); if (!r) { return; }
  var html = '';
  if (toolTab === 'subnet') {
    var s = calcSubnet(valOf('t_ip'), valOf('t_prefix'));
    html = s ? '<div class="res-grid">' + resItem('子网掩码', s.mask) + resItem('网络地址', s.network) + resItem('广播地址', s.broadcast) + resItem('可用主机范围', s.first + ' ~ ' + s.last) + resItem('可用主机数', s.hosts) + '</div>' : '<span class="red">输入无效</span>';
  } else if (toolTab === 'poe') {
    var p = calcPoE(valOf('t_ports'), valOf('t_pp'));
    html = '<div class="res-grid">' + resItem('总功率预算(W)', p.total) + resItem('安全可用(×0.8)', p.usable) + resItem('端口/单口', p.ports + ' × ' + p.perPort + 'W') + '</div>';
  } else if (toolTab === 'storage') {
    var st = calcStorage(valOf('t_br'), valOf('t_days'), valOf('t_ch'));
    html = '<div class="res-grid">' + resItem('所需容量', st.gb + ' GB ≈ ' + st.tb + ' TB') + resItem('参数', st.channels + '路 × ' + st.bitrate + 'Mbps × ' + st.days + '天') + '</div>';
  } else if (toolTab === 'bandwidth') {
    var bw = calcBandwidth(valOf('t_ch2'), valOf('t_br2'), valOf('t_ov'));
    html = '<div class="res-grid">' + resItem('基础带宽', bw.base + ' Mbps') + resItem('含开销总带宽', bw.total + ' Mbps') + '</div>';
  } else if (toolTab === 'rtsp') {
    var u = rtspFor(valOf('t_rbrand'), valOf('t_rip'), valOf('t_ruser'), valOf('t_rpass'));
    html = '<div class="res-grid">' + resItem('RTSP 地址', '<code>' + esc(u) + '</code>') + '</div>';
  }
  r.innerHTML = html;
}
function bindToolInputs() {
  var f = byId('toolForm'); if (!f) { return; }
  f.querySelectorAll('input,select').forEach(function (el) {
    el.addEventListener('input', computeTool);
    el.addEventListener('change', computeTool);
  });
}

/* ---------------- 数据同步 ---------------- */
function viewSync() {
  var fmt = '<div class="fmt"><b>安卓导出格式（参考，字段较少）：</b><br>'
    + 'devices: type, brand, model, ip, status, location, note<br>'
    + 'tickets: code, device, title, ftype, level, status, desc, solution<br>'
    + 'inspections: title, date, result, note<br>'
    + '<span class="muted">网页版按「超集」导出，额外含 id / code / parent_id / device_id / building / floor / created / closed / assigned，并包含 users（含 pass_hash 哈希同步）。可被安卓导入（按 username upsert，admin 不可被禁用/删除）。</span></div>';
  var body = ''
    + '<div class="sync-box"><h3>导出（备份到文件）</h3><p class="muted">生成 SafetyRepair-sync-日期.json，可导入安卓版「数据备份」恢复。</p>'
    + '<button class="btn btn-primary" data-action="syncExport">⬇ 导出 JSON 备份</button> '
    + '<button class="btn" data-action="exportDeviceCSV">导出设备 CSV</button> '
    + '<button class="btn" data-action="devCSVTemplate">设备 CSV 模板</button></div>'
    + '<div class="sync-box"><h3>导入（从文件恢复）</h3>'
    + '<label class="fld"><span>导入模式</span><select id="sync_mode"><option value="merge">合并（按编号更新，保留其它）</option><option value="overwrite">覆盖（清空后导入）</option></select></label>'
    + '<input type="file" id="sync_file" accept=".json,application/json">'
    + '<div class="row-actions"><button class="btn btn-primary" data-action="syncImport">⬆ 导入 JSON</button></div>'
    + '<p class="muted" id="sync_msg"></p></div>'
    + '<div class="sync-box danger-box"><h3>危险操作</h3><p class="muted">清空本地所有数据并恢复演示种子。</p><button class="btn danger" data-action="syncReset">重置为种子数据</button></div>'
    + fmt;
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>数据同步</h2></div></div><div class="card">' + body + '</div>';
}
function syncExport() {
  var d = today().replace(/-/g, '');
  download('SafetyRepair-sync-' + d + '.json', exportJSON(), 'application/json');
  toast('已导出');
}
function syncImport() {
  var f = byId('sync_file');
  if (!f || !f.files || !f.files.length) { toast('请先选择 JSON 文件'); return; }
  var mode = byId('sync_mode') ? byId('sync_mode').value : 'merge';
  var reader = new FileReader();
  reader.onload = function (e) {
    try {
      var obj = JSON.parse(e.target.result);
      var res = importData(obj, mode);
      var m = byId('sync_msg');
      if (m) { m.textContent = '导入成功：设备 ' + res.devices + ' · 工单 ' + res.tickets + ' · 巡检 ' + res.inspections; }
      toast('导入成功'); render();
    } catch (err) {
      var m2 = byId('sync_msg');
      if (m2) { m2.textContent = '导入失败：' + err.message; }
      toast('导入失败');
    }
  };
  reader.readAsText(f.files[0]);
}
function syncReset() {
  if (!confirm('确定清空并恢复种子数据？')) { return; }
  resetData(); toast('已重置'); render();
}
function devCSVTemplate() { download('设备台账模板.csv', deviceCSVTemplate(), 'text/csv;charset=utf-8'); toast('已下载模板'); }
function exportDeviceCSV() { download('设备台账-' + today().replace(/-/g, '') + '.csv', exportDeviceCSV(), 'text/csv;charset=utf-8'); toast('已导出 CSV'); }
function importDeviceCSVModal() {
  var html = '<div class="form"><p class="muted">选择设备 CSV（按编号 code 更新/新增，支持 parent_code 父子）。</p>'
    + '<input type="file" id="csv_file" accept=".csv,text/csv">'
    + '<div class="form-actions"><button class="btn btn-primary" data-action="doImportDeviceCSV">导入</button><button class="btn" data-action="closeModal">取消</button></div>'
    + '<p class="muted" id="csv_msg"></p></div>';
  openModal('导入设备 CSV', html);
}
function doImportDeviceCSV() {
  var f = byId('csv_file');
  if (!f || !f.files || !f.files.length) { toast('请选择文件'); return; }
  var reader = new FileReader();
  reader.onload = function (e) {
    try {
      var res = importDeviceCSV(e.target.result);
      var m = byId('csv_msg');
      if (m) { m.textContent = '导入完成：新增 ' + res.added + ' · 更新 ' + res.updated; }
      toast('导入完成'); closeModal(); render();
    } catch (err) { toast('失败：' + err.message); }
  };
  reader.readAsText(f.files[0]);
}

/* ---------------- 个人中心（修复1） ---------------- */
function openPersonal() {
  var u = currentUser(); if (!u) { return; }
  var html = '<div class="form pc">'
    + '<div class="pc-row"><span class="pc-k">用户名</span><span class="pc-v">' + esc(u.username) + '</span></div>'
    + '<div class="pc-row"><span class="pc-k">姓名</span><span class="pc-v">' + esc(u.name || '—') + '</span></div>'
    + '<div class="pc-row"><span class="pc-k">角色</span><span class="pc-v"><span class="role-badge role-' + u.role + '">' + (ROLE_LABEL[u.role] || u.role) + '</span></span></div>'
    + '<hr class="pc-hr">'
    + '<h4 style="margin:4px 0 10px;color:#123a63">修改密码</h4>'
    + fld('旧密码', 'pc_old', 'password', '')
    + fld('新密码', 'pc_new', 'password', '', '至少 6 位')
    + fld('确认新密码', 'pc_new2', 'password', '')
    + '<div class="form-actions"><button class="btn btn-primary" data-action="pcChangePwd">修改密码</button></div>'
    + '<div class="form-actions" style="margin-top:10px"><button class="btn danger" data-action="pcLogout">退出登录</button></div>'
    + '</div>';
  openModal('个人中心', html);
}
function pcChangePwd() {
  var u = currentUser(); if (!u) { return; }
  var old = byId('pc_old').value;
  var nw = byId('pc_new').value;
  var nw2 = byId('pc_new2').value;
  if (!old || !nw || !nw2) { toast('请填写完整'); return; }
  if (nw.length < 6) { toast('新密码至少 6 位'); return; }
  if (nw !== nw2) { toast('两次新密码不一致'); return; }
  (async function () {
    var ok = await verifyPassword(old, u.pass_hash);
    if (!ok) { toast('旧密码错误'); return; }
    u.pass_hash = await hashPassword(nw);
    save();
    closeModal(); toast('密码已修改');
  })();
}
function doLogout() {
  try { localStorage.removeItem(CURRENT_USER_KEY); } catch (e) {}
  var app = byId('app'); if (app) { app.classList.add('hidden'); }
  var ls = byId('loginScreen'); if (ls) { ls.classList.remove('hidden'); }
  var pu = byId('login_user'); if (pu) { pu.value = ''; }
  var pp = byId('login_pass'); if (pp) { pp.value = ''; }
  var pe = byId('login_err'); if (pe) { pe.textContent = ''; }
}

/* ---------------- 人员管理（仅 admin） ---------------- */
function viewUsers() {
  if (!isAdmin()) { return viewHome(); }
  var actions = '<div class="row-actions"><button class="btn btn-primary" data-action="addUser">＋ 新增账号</button></div>';
  var rows = state.users.slice().sort(function (a, b) { return String(a.username).localeCompare(String(b.username)); }).map(userRowHTML).join('');
  var body = rows ? '<table class="tbl"><thead><tr><th>用户名</th><th>姓名</th><th>角色</th><th>手机号</th><th>状态</th><th>操作</th></tr></thead><tbody>' + rows + '</tbody></table>' : emptyHTML('暂无账号');
  return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>人员管理</h2></div>' + actions + '</div><div class="card">' + body + '</div>';
}
function userRowHTML(u) {
  var isSelf = u.username === currentUser().username;
  var roleBadge = '<span class="role-badge role-' + u.role + '">' + (ROLE_LABEL[u.role] || u.role) + '</span>';
  var statusHtml = u.status === 'active'
    ? '<span class="st st-online">启用</span>'
    : '<span class="st st-offline">停用</span>';
  var ops = '';
  if (isSelf) {
    ops = '<span class="muted">当前账号</span>';
  } else {
    ops += '<button class="mini" data-action="resetPwd" data-arg="' + esc(u.username) + '">重置密码</button> ';
    ops += '<button class="mini" data-action="toggleUser" data-arg="' + esc(u.username) + '">' + (u.status === 'active' ? '停用' : '启用') + '</button> ';
    if (u.role !== 'admin') {
      ops += '<button class="mini danger" data-action="delUser" data-arg="' + esc(u.username) + '">删除</button>';
    }
  }
  return '<tr><td><b>' + esc(u.username) + '</b></td><td>' + esc(u.name || '') + '</td><td>' + roleBadge + '</td><td>' + esc(u.phone || '') + '</td><td>' + statusHtml + '</td><td class="ops">' + ops + '</td></tr>';
}
function openUserModal() {
  if (!isAdmin()) { return; }
  var html = '<div class="form">'
    + fld('用户名', 'u_username', 'text', '', '登录用户名')
    + fld('初始密码', 'u_pass', 'password', '', '至少 6 位')
    + fld('姓名', 'u_name', 'text', '')
    + fld('角色', 'u_role', 'select', 'worker', null, [['worker', '员工'], ['supervisor', '负责人'], ['admin', '管理员']])
    + fld('手机号', 'u_phone', 'text', '', '可选')
    + '<div class="form-actions"><button class="btn btn-primary" data-action="saveUser">保存</button><button class="btn" data-action="closeModal">取消</button></div>'
    + '</div>';
  openModal('新增账号', html);
}
function saveUser() {
  var username = byId('u_username').value.trim();
  var pass = byId('u_pass').value;
  var name = byId('u_name').value.trim();
  var role = byId('u_role').value;
  var phone = byId('u_phone').value.trim();
  if (!username) { toast('请填写用户名'); return; }
  if (pass.length < 6) { toast('初始密码至少 6 位'); return; }
  if (state.users.some(function (x) { return x.username === username; })) { toast('用户名已存在'); return; }
  (async function () {
    var h = await hashPassword(pass);
    state.users.push({ username: username, pass_hash: h, name: name, role: role, phone: phone, status: 'active', created: today() });
    save();
    closeModal(); toast('账号已创建'); render();
  })();
}
function resetPwd(username) {
  if (!isAdmin()) { return; }
  var html = '<div class="form">'
    + '<p class="muted">为 <b>' + esc(username) + '</b> 设置新密码（至少 6 位）。</p>'
    + fld('新密码', 'rp_new', 'password', '', '至少 6 位')
    + fld('确认新密码', 'rp_new2', 'password', '')
    + '<div class="form-actions"><button class="btn btn-primary" data-action="doResetPwd" data-arg="' + esc(username) + '">确定</button><button class="btn" data-action="closeModal">取消</button></div>'
    + '</div>';
  openModal('重置密码', html);
}
function doResetPwd(username) {
  var nw = byId('rp_new').value;
  var nw2 = byId('rp_new2').value;
  if (nw.length < 6) { toast('新密码至少 6 位'); return; }
  if (nw !== nw2) { toast('两次不一致'); return; }
  var u = state.users.find(function (x) { return x.username === username; });
  if (!u) { toast('账号不存在'); return; }
  (async function () {
    u.pass_hash = await hashPassword(nw);
    save();
    closeModal(); toast('密码已重置');
  })();
}
function toggleUser(username) {
  if (!isAdmin()) { return; }
  var u = state.users.find(function (x) { return x.username === username; });
  if (!u) { return; }
  if (u.role === 'admin') { toast('管理员账号不可停用'); return; }
  if (u.username === currentUser().username) { toast('不能操作当前账号'); return; }
  u.status = u.status === 'active' ? 'disabled' : 'active';
  save(); toast(u.status === 'active' ? '已启用' : '已停用'); render();
}
function delUser(username) {
  if (!isAdmin()) { return; }
  var u = state.users.find(function (x) { return x.username === username; });
  if (!u) { return; }
  if (u.role === 'admin') { toast('管理员账号不可删除'); return; }
  if (u.username === currentUser().username) { toast('不能删除当前账号'); return; }
  if (!confirm('确定删除账号 ' + username + '？')) { return; }
  state.users = state.users.filter(function (x) { return x.username !== username; });
  save(); toast('已删除'); render();
}

/* ---------------- 事件委托 ---------------- */
document.addEventListener('click', function (e) {
  var t = e.target;
  if (!t || !t.closest) { return; }
  var el = t.closest('[data-action]');
  if (!el) { return; }
  var action = el.getAttribute('data-action');
  var arg = el.getAttribute('data-arg');
  switch (action) {
    case 'goto': goto(arg); break;
    case 'closeModal': closeModal(); break;
    case 'devView': devView = arg; render(); break;
    case 'addDevice': openDeviceModal(null); break;
    case 'editDevice': openDeviceModal(arg); break;
    case 'delDevice': delDevice(arg); break;
    case 'saveDevice': saveDevice(); break;
    case 'addTicket': currentTicketAssigned = ''; openTicketModal(null); break;
    case 'editTicket':
      var et = state.tickets.find(function (x) { return x.id === Number(arg); });
      currentTicketAssigned = et ? (et.assigned || '') : '';
      openTicketModal(arg); break;
    case 'delTicket': delTicket(arg); break;
    case 'saveTicket': saveTicket(); break;
    case 'advanceTicket': advanceTicket(arg); break;
    case 'addInspection': openInspectionModal(null); break;
    case 'editInspection': openInspectionModal(arg); break;
    case 'delInspection': delInspection(arg); break;
    case 'saveInspection': saveInspection(); break;
    case 'wizTab': wizTab(arg); break;
    case 'wizGoto': wizGoto(arg); break;
    case 'wizBack': wizBack(); break;
    case 'wizReset': wizReset(); break;
    case 'quickTab': quickTab = arg; render(); break;
    case 'toolTab': toolTab = arg; render(); break;
    case 'syncExport': syncExport(); break;
    case 'syncImport': syncImport(); break;
    case 'syncReset': syncReset(); break;
    case 'devCSVTemplate': devCSVTemplate(); break;
    case 'exportDeviceCSV': exportDeviceCSV(); break;
    case 'importDeviceCSV': importDeviceCSVModal(); break;
    case 'doImportDeviceCSV': doImportDeviceCSV(); break;
    case 'openPersonal': openPersonal(); break;
    case 'pcChangePwd': pcChangePwd(); break;
    case 'pcLogout': doLogout(); break;
    case 'addUser': openUserModal(); break;
    case 'saveUser': saveUser(); break;
    case 'resetPwd': resetPwd(arg); break;
    case 'doResetPwd': doResetPwd(arg); break;
    case 'toggleUser': toggleUser(arg); break;
    case 'delUser': delUser(arg); break;
  }
});
document.addEventListener('input', function (e) {
  if (e.target && e.target.getAttribute && e.target.getAttribute('data-action') === 'quickSearch') {
    onQuickSearch(e.target.value);
  }
});
/* 指派工单（下拉 change 事件） */
document.addEventListener('change', function (e) {
  var el = e.target.closest ? e.target.closest('[data-action]') : null;
  if (!el) { return; }
  var action = el.getAttribute('data-action');
  if (action === 'assignTicket') { assignTicket(el.getAttribute('data-arg'), el.value); }
});

/* ---------------- 登录门 ---------------- */
function bindLogin() {
  var form = byId('loginForm');
  if (!form) { return; }
  form.addEventListener('submit', function (e) {
    e.preventDefault();
    var uName = byId('login_user').value.trim();
    var pWord = byId('login_pass').value;
    var err = byId('login_err');
    var user = state.users.find(function (x) { return x.username === uName; });
    if (!user) { if (err) { err.textContent = '用户名不存在'; } return; }
    if (user.status !== 'active') { if (err) { err.textContent = '该账号已停用，请联系管理员'; } return; }
    (async function () {
      var ok = await verifyPassword(pWord, user.pass_hash);
      if (!ok) { if (err) { err.textContent = '密码错误'; } return; }
      try { localStorage.setItem(CURRENT_USER_KEY, user.username); } catch (e2) {}
      if (err) { err.textContent = ''; }
      byId('loginScreen').classList.add('hidden');
      byId('app').classList.remove('hidden');
      render();
    })();
  });
}

/* ---------------- 启动 ---------------- */
async function boot() {
  // 先尝试从云端加载数据（忽略错误，本地优先）
  if (typeof window.sbLoad === 'function') {
    try {
      var cloudData = await window.sbLoad();
      if (cloudData && cloudData.devices) {
        // 字段映射：ip_address → ip，description → desc 兼容
        cloudData.devices.forEach(function (d) {
          if (d.ip_address && !d.ip) d.ip = d.ip_address;
        });
        cloudData.tickets && cloudData.tickets.forEach(function (t) {
          if (t.description !== undefined && t.desc === undefined) t.desc = t.description;
        });
        // 合并到本地 state（云端优先）
        if (cloudData.devices.length) state.devices = cloudData.devices;
        if (cloudData.tickets.length) state.tickets = cloudData.tickets;
        if (cloudData.users.length) state.users = cloudData.users;
        if (cloudData.inspections.length) state.inspections = cloudData.inspections;
      }
    } catch (e) {
      console.warn('[Boot] 云端加载失败，使用本地数据:', e);
    }
  }
  await ensureAdminUser();
  bindLogin();
  if (currentUser()) {
    var ls = byId('loginScreen'); if (ls) { ls.classList.add('hidden'); }
    var app = byId('app'); if (app) { app.classList.remove('hidden'); }
    render();
  } else {
    var app2 = byId('app'); if (app2) { app2.classList.add('hidden'); }
  }
}
if (document.readyState === 'loading') {
  document.addEventListener('DOMContentLoaded', boot);
} else {
  boot();
}

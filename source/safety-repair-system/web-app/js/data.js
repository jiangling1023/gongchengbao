/* ============================================================
 * data.js — 数据层：localStorage 持久化 / 种子数据 / 导入导出 / CSV
 * 对齐安卓 DBHelper 表结构与 DataFragment 的 JSON 字段。
 * ============================================================ */

var STORE_KEY = 'safetyRepairData';
var CURRENT_USER_KEY = 'safetyRepairCurrentUser';

/* ---------- 日期 / 文本工具 ---------- */
function today() {
  var d = new Date();
  var p = function (n) { return String(n).padStart(2, '0'); };
  return d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate());
}

/* 从 location 提取楼层（如 "1F大堂东侧" -> "1F"） */
function extractFloor(loc) {
  if (!loc) return '';
  var m = String(loc).match(/(\d+)\s*[Ff层楼]/);
  return m ? m[1] + 'F' : '';
}

/* 从 location 提取楼栋（含「栋/号楼/座」前缀），无则返回空 */
function extractBuilding(loc) {
  if (!loc) return '';
  var m = String(loc).match(/^([A-Za-z0-9一-龥]*?(?:栋|号楼|座))/);
  return m ? m[1] : '';
}

/* ---------- 密码哈希（SHA-256 via Web Crypto，file:// 降级简单哈希） ---------- */
function hasCrypto() {
  return typeof window !== 'undefined' && !!(window.crypto && window.crypto.subtle && window.crypto.subtle.digest);
}
function bufToHex(buf) {
  var b = new Uint8Array(buf);
  var s = '';
  for (var i = 0; i < b.length; i++) { s += b[i].toString(16).padStart(2, '0'); }
  return s;
}
async function sha256Hex(pwd) {
  var buf = await window.crypto.subtle.digest('SHA-256', new TextEncoder().encode(pwd));
  return bufToHex(buf);
}
/* 简单哈希（FNV-1a），用于 file:// 或非安全上下文降级 */
function simpleHash(pwd) {
  var h = 0x811c9dc5;
  for (var i = 0; i < pwd.length; i++) { h ^= pwd.charCodeAt(i); h = (h * 0x01000193) >>> 0; }
  return 's' + h.toString(16);
}
/* 对明文密码计算存储哈希（优先 SHA-256，失败降级） */
async function hashPassword(pwd) {
  if (hasCrypto()) {
    try { return await sha256Hex(pwd); } catch (e) {}
  }
  return simpleHash(pwd);
}
/* 校验明文密码与已存储哈希（兼容两种哈希格式，跨上下文可登录） */
async function verifyPassword(pwd, storedHash) {
  var candidates = [];
  if (storedHash && storedHash.charAt(0) === 's') {
    candidates.push(simpleHash(pwd));
    if (hasCrypto()) { try { candidates.push(await sha256Hex(pwd)); } catch (e) {} }
  } else {
    if (hasCrypto()) { try { candidates.push(await sha256Hex(pwd)); } catch (e) {} }
    candidates.push(simpleHash(pwd));
  }
  return candidates.indexOf(storedHash) >= 0;
}

/* ---------- 种子数据（翻译自 DBHelper.java 的种子） ---------- */
var SEED = {
  devices: [
    { id: 1, code: 'S001', parent_id: 0, type: 'camera', brand: '海康威视', model: 'DS-2CD3346', ip: '10.10.10.21', ip_address: '10.10.10.21', status: 'online', location: '1F大堂东侧', building: '', floor: '1F', note: '2.8mm枪机', created: '2026-01-01' },
    { id: 2, code: 'S002', parent_id: 0, type: 'camera', brand: '海康威视', model: 'DS-2CD3T46', ip: '10.10.10.22', ip_address: '10.10.10.22', status: 'online', location: '2F电梯厅', building: '', floor: '2F', note: '4mm半球', created: '2026-01-01' },
    { id: 3, code: 'S003', parent_id: 0, type: 'door', brand: '立林', model: 'L-A1四门控制器', ip: '10.10.20.10', ip_address: '10.10.20.10', status: 'online', location: '1F弱电间', building: '', floor: '1F', note: 'TCP/IP通讯', created: '2026-01-01' },
    { id: 4, code: 'S004', parent_id: 3, type: 'door', brand: '立林', model: '读卡器IC', ip: '-', ip_address: '-', status: 'online', location: '1F大门', building: '', floor: '1F', note: '韦根34', created: '2026-01-01' },
    { id: 5, code: 'S005', parent_id: 0, type: 'camera', brand: '大华', model: 'DH-IPC-HFW', ip: '10.10.10.31', ip_address: '10.10.10.31', status: 'unknown', location: '3F走廊', building: '', floor: '3F', note: '待核IP', created: '2026-01-02' }
  ],
  tickets: [
    { id: 1, code: 'WX-20251001', device_id: 1, device: '1F大堂东侧-海康DS-2CD3346', title: '设备离线', ftype: '网络', level: 'P2', status: 'closed', description: 'ping不通，PoE端口down', desc: 'ping不通，PoE端口down', solution: '更换网线水晶头，端口恢复', created: '2025-10-01', closed: '2025-10-01' },
    { id: 2, code: 'WX-20251002', device_id: 4, device: '1F大门-立林读卡器', title: '刷卡不开门', ftype: '权限', level: 'P1', status: 'closed', description: '卡片过期未续', desc: '卡片过期未续', solution: '平台延期并补卡', created: '2025-10-02', closed: '2025-10-02' },
    { id: 3, code: 'WX-20251003', device_id: 5, device: '3F走廊-大华DH-IPC-HFW', title: '画面卡顿', ftype: '硬件', level: 'P2', status: 'processing', description: '夜间红外画面卡顿，疑似电源老化', desc: '夜间红外画面卡顿，疑似电源老化', solution: '待更换电源适配器', created: '2025-10-03', closed: '' }
  ],
  inspections: []
};

/* ---------- 状态加载 / 保存 ---------- */
function seedData() {
  var s = JSON.parse(JSON.stringify(SEED));
  try { localStorage.setItem(STORE_KEY, JSON.stringify(s)); } catch (e) {}
  return s;
}

var state = (function () {
  try {
    var r = localStorage.getItem(STORE_KEY);
    if (r) {
      var o = JSON.parse(r);
      if (o && Array.isArray(o.devices) && Array.isArray(o.tickets) && Array.isArray(o.inspections)) {
        return o;
      }
    }
  } catch (e) {}
  // 首次使用或数据损坏时，返回空数据结构（不用种子数据）
  return { devices: [], tickets: [], inspections: [] };
})();
if (!Array.isArray(state.users)) { state.users = []; }
// 挂载到 window，供其他模块访问
window.state = state;

function save() {
  try { localStorage.setItem(STORE_KEY, JSON.stringify(state)); } catch (e) {}
  // 云端同步：已配置 Supabase 时自动推送
  if (typeof window.sbSync === 'function') {
    try { window.sbSync(); } catch (e) {}
  }
}

function resetData() {
  try { localStorage.removeItem(STORE_KEY); } catch (e) {}
  state = seedData();
  return state;
}

/* ---------- 字段规范化 ---------- */
function normalizeDeviceFields(raw) {
  raw = raw || {};
  var d = {};
  d.type = raw.type === 'door' ? 'door' : 'camera';
  d.brand = String(raw.brand || '').trim();
  d.model = String(raw.model || '').trim();
  d.ip = String(raw.ip || '').trim();
  var st = String(raw.status || '').trim();
  d.status = ['online', 'offline', 'unknown', 'fault'].indexOf(st) >= 0 ? st : (st || 'unknown');
  d.location = String(raw.location || '').trim();
  d.note = String(raw.note || '').trim();
  d.parent_id = Number(raw.parent_id) || 0;
  d.building = String(raw.building || '').trim() || extractBuilding(d.location);
  d.floor = String(raw.floor || '').trim() || extractFloor(d.location);
  d.code = String(raw.code || '').trim();
  d.created = String(raw.created || '').trim() || today();
  d.id = Number(raw.id) || 0;
  return d;
}

function normalizeTicketFields(raw) {
  raw = raw || {};
  var t = {};
  var d = today().replace(/-/g, '');
  t.code = String(raw.code || '').trim() || ('WX-' + d + '-' + Math.floor(Math.random() * 900 + 100));
  t.device_id = Number(raw.device_id) || 0;
  t.device = String(raw.device || '').trim();
  t.title = String(raw.title || '').trim();
  t.ftype = String(raw.ftype || '').trim();
  t.level = String(raw.level || '').trim() || 'P3';
  var st = String(raw.status || '').trim();
  t.status = ['processing', 'closed', 'pending'].indexOf(st) >= 0 ? st : (st || 'pending');
  t.description = String(raw.description || '').trim();
  t.solution = String(raw.solution || '').trim();
  t.created = String(raw.created || '').trim() || today();
  t.closed = String(raw.closed || '').trim();
  t.assigned = raw.assigned ? String(raw.assigned).trim() : '';
  t.id = Number(raw.id) || 0;
  if (t.device_id && !t.device) {
    var dv = state.devices.find(function (x) { return x.id === t.device_id; });
    if (dv) { t.device = (dv.location || '') + '-' + (dv.brand || '') + (dv.model || ''); }
  }
  return t;
}

function normalizeInspectionFields(raw) {
  raw = raw || {};
  var o = {};
  o.title = String(raw.title || '').trim();
  o.date = String(raw.date || '').trim() || today();
  o.result = String(raw.result || '').trim();
  o.note = String(raw.note || '').trim();
  o.id = Number(raw.id) || 0;
  return o;
}

/* ---------- 用户字段规范化 ---------- */
function normalizeUserFields(raw) {
  raw = raw || {};
  var u = {};
  u.username = String(raw.username || '').trim();
  u.pass_hash = String(raw.pass_hash || '').trim();
  u.name = String(raw.name || '').trim();
  u.role = (['admin', 'supervisor', 'worker'].indexOf(raw.role) >= 0) ? raw.role : 'worker';
  u.phone = String(raw.phone || '').trim();
  u.status = (raw.status === 'disabled') ? 'disabled' : 'active';
  u.created = String(raw.created || '').trim() || today();
  return u;
}

/* ---------- 故障设备（处理中工单关联） ---------- */
function faultDeviceIds() {
  var s = {};
  state.tickets.forEach(function (t) {
    if (t.status === 'processing' && Number(t.device_id) > 0) { s[Number(t.device_id)] = true; }
  });
  return s;
}
function isFaultDevice(id) { return !!faultDeviceIds()[Number(id)]; }

/* ---------- 编码生成 ---------- */
function genDeviceCode() {
  var max = 0;
  state.devices.forEach(function (d) {
    var m = String(d.code || '').match(/(\d+)/);
    if (m) { max = Math.max(max, parseInt(m[1], 10)); }
  });
  return 'S' + String(max + 1).padStart(3, '0');
}

/* ---------- CRUD ---------- */
function upsertDevice(d) {
  d = normalizeDeviceFields(d);
  if (!d.code) { d.code = genDeviceCode(); }
  var ex = state.devices.find(function (x) { return x.code === d.code; });
  if (ex) { Object.assign(ex, d, { id: ex.id }); }
  else {
    var max = state.devices.reduce(function (m, x) { return Math.max(m, Number(x.id) || 0); }, 0);
    d.id = max + 1;
    state.devices.push(d);
  }
  save();
  return d;
}
function deleteDevice(id) {
  state.devices = state.devices.filter(function (x) { return x.id !== Number(id); });
  save();
}

function upsertTicket(t) {
  t = normalizeTicketFields(t);
  var ex = state.tickets.find(function (x) { return x.code === t.code; });
  if (ex) { Object.assign(ex, t, { id: ex.id }); }
  else {
    var max = state.tickets.reduce(function (m, x) { return Math.max(m, Number(x.id) || 0); }, 0);
    t.id = max + 1;
    state.tickets.push(t);
  }
  save();
  return t;
}
function deleteTicket(id) {
  state.tickets = state.tickets.filter(function (x) { return x.id !== Number(id); });
  save();
}

function upsertInspection(o) {
  o = normalizeInspectionFields(o);
  var ex = state.inspections.find(function (x) { return x.title === o.title && x.date === o.date; });
  if (ex) { Object.assign(ex, o, { id: ex.id }); }
  else {
    var max = state.inspections.reduce(function (m, x) { return Math.max(m, Number(x.id) || 0); }, 0);
    o.id = max + 1;
    state.inspections.push(o);
  }
  save();
  return o;
}
function deleteInspection(id) {
  state.inspections = state.inspections.filter(function (x) { return x.id !== Number(id); });
  save();
}

/* ---------- JSON 导出（超集，兼容安卓导入） ---------- */
function exportJSON() {
  var out = {
    devices: state.devices.map(function (d) {
      return { id: d.id, code: d.code, parent_id: d.parent_id, type: d.type, brand: d.brand, model: d.model, ip: d.ip, status: d.status, location: d.location, building: d.building, floor: d.floor, note: d.note, created: d.created };
    }),
    tickets: state.tickets.map(function (t) {
      return { id: t.id, code: t.code, device_id: t.device_id, device: t.device, title: t.title, ftype: t.ftype, level: t.level, status: t.status, description: t.description, solution: t.solution, created: t.created, closed: t.closed, assigned: t.assigned || '' };
    }),
    inspections: state.inspections.map(function (i) {
      return { id: i.id, title: i.title, date: i.date, result: i.result, note: i.note };
    }),
    users: state.users.map(function (u) {
      return { username: u.username, pass_hash: u.pass_hash, name: u.name, role: u.role, phone: u.phone, status: u.status, created: u.created };
    })
  };
  return JSON.stringify(out, null, 2);
}

/* ---------- JSON 导入（校验 + 合并/覆盖） ---------- */
function importData(raw, mode) {
  if (!raw || typeof raw !== 'object' || Array.isArray(raw)) {
    throw new Error('文件不是合法的数据对象');
  }
  var dev = Array.isArray(raw.devices) ? raw.devices : [];
  var tk = Array.isArray(raw.tickets) ? raw.tickets : [];
  var ins = Array.isArray(raw.inspections) ? raw.inspections : [];
  if (!dev.length && !tk.length && !ins.length && !Array.isArray(raw.users)) {
    throw new Error('JSON 缺少 devices / tickets / inspections / users 数据');
  }

  // 设备
  var devices = mode === 'overwrite' ? [] : state.devices.map(function (d) { return Object.assign({}, d); });
  var codeToId = {};
  devices.forEach(function (d) { if (d.code) { codeToId[d.code] = d.id; } });
  var maxId = devices.reduce(function (m, d) { return Math.max(m, Number(d.id) || 0); }, 0);

  dev.forEach(function (r) {
    var d = normalizeDeviceFields(r);
    if (d.code && codeToId[d.code]) {
      var id = codeToId[d.code];
      var ex = devices.find(function (x) { return x.id === id; });
      Object.assign(ex, d, { id: id });
    } else {
      maxId++;
      d.id = maxId;
      if (!d.code) { d.code = 'S' + String(maxId).padStart(3, '0'); }
      codeToId[d.code] = d.id;
      devices.push(d);
    }
  });
  // 解析父级（优先按父级 code）
  devices.forEach(function (d) {
    if (d.parent_code) { d.parent_id = codeToId[d.parent_code] || 0; delete d.parent_code; }
    else if (d.parent_id) {
      var ok = devices.some(function (x) { return x.id === d.parent_id; });
      if (!ok) { d.parent_id = 0; }
    }
  });

  // 工单
  var tickets = mode === 'overwrite' ? [] : state.tickets.map(function (t) { return Object.assign({}, t); });
  tk.forEach(function (r) {
    var t = normalizeTicketFields(r);
    if (!t.device_id && t.device) {
      var dv = devices.find(function (x) { return x.location && t.device.indexOf(x.location) >= 0; });
      if (dv) { t.device_id = dv.id; }
    }
    if (t.code) {
      var ex = tickets.find(function (x) { return x.code === t.code; });
      if (ex) { Object.assign(ex, t, { id: ex.id }); return; }
    }
    var tmax = tickets.reduce(function (m, x) { return Math.max(m, Number(x.id) || 0); }, 0);
    tmax++; t.id = tmax; tickets.push(t);
  });

  // 巡检
  var inspections = mode === 'overwrite' ? [] : state.inspections.map(function (i) { return Object.assign({}, i); });
  ins.forEach(function (r) {
    var o = normalizeInspectionFields(r);
    if (o.title && o.date) {
      var ex = inspections.find(function (x) { return x.title === o.title && x.date === o.date; });
      if (ex) { Object.assign(ex, o, { id: ex.id }); return; }
    }
    var imax = inspections.reduce(function (m, x) { return Math.max(m, Number(x.id) || 0); }, 0);
    imax++; o.id = imax; inspections.push(o);
  });

  /* 用户（按 username upsert；admin 不可被禁用/删除） */
  if (Array.isArray(raw.users)) {
    raw.users.forEach(function (r) {
      var u = normalizeUserFields(r);
      if (!u.username) { return; }
      if (u.username === 'admin') { u.role = 'admin'; u.status = 'active'; }
      var ex = state.users.find(function (x) { return x.username === u.username; });
      if (ex) {
        if (!u.pass_hash) { u.pass_hash = ex.pass_hash; }
        Object.assign(ex, u, { username: ex.username });
      } else {
        if (!u.pass_hash) { u.pass_hash = simpleHash('123456'); }
        state.users.push(u);
      }
    });
  }

  state.devices = devices;
  state.tickets = tickets;
  state.inspections = inspections;
  save();
  return { devices: devices.length, tickets: tickets.length, inspections: inspections.length };
}

/* ---------- CSV ---------- */
function parseCSV(text) {
  var rows = [];
  var row = [], field = '', inQ = false;
  text = text.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  for (var i = 0; i < text.length; i++) {
    var c = text[i];
    if (inQ) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i++; }
        else { inQ = false; }
      } else { field += c; }
    } else {
      if (c === '"') { inQ = true; }
      else if (c === ',') { row.push(field); field = ''; }
      else if (c === '\n') { row.push(field); rows.push(row); row = []; field = ''; }
      else { field += c; }
    }
  }
  if (field.length > 0 || row.length > 0) { row.push(field); rows.push(row); }
  return rows.filter(function (r) { return r.some(function (c) { return c.trim() !== ''; }); });
}

function csvCell(v) {
  v = v == null ? '' : String(v);
  if (/[",\n]/.test(v)) { return '"' + v.replace(/"/g, '""') + '"'; }
  return v;
}
function toCSV(rows) {
  return rows.map(function (r) { return r.map(csvCell).join(','); }).join('\n');
}

function deviceCSVTemplate() {
  return toCSV([
    ['code', 'parent_code', 'type', 'brand', 'model', 'ip', 'status', 'location', 'note'],
    ['S006', '', 'camera', '海康威视', 'DS-2CD', '10.10.10.40', 'online', '1F前台', '示例设备']
  ]);
}

function exportDeviceCSV() {
  var header = ['code', 'parent_code', 'type', 'brand', 'model', 'ip', 'status', 'location', 'note'];
  var rows = [header];
  state.devices.forEach(function (d) {
    var parentCode = '';
    if (d.parent_id) {
      var p = state.devices.find(function (x) { return x.id === d.parent_id; });
      if (p) { parentCode = p.code || ''; }
    }
    rows.push([d.code || '', parentCode, d.type || '', d.brand || '', d.model || '', d.ip || '', d.status || '', d.location || '', d.note || '']);
  });
  return toCSV(rows);
}

function importDeviceCSV(text) {
  var rows = parseCSV(text);
  if (!rows.length) { throw new Error('CSV 为空'); }
  var header = rows[0].map(function (h) { return h.trim(); });
  function idx(name) { return header.indexOf(name); }
  var iCode = idx('code'), iParent = idx('parent_code'), iType = idx('type'),
      iBrand = idx('brand'), iModel = idx('model'), iIp = idx('ip'),
      iStatus = idx('status'), iLoc = idx('location'), iNote = idx('note');

  var codeToId = {};
  state.devices.forEach(function (d) { if (d.code) { codeToId[d.code] = d.id; } });
  var maxId = state.devices.reduce(function (m, d) { return Math.max(m, Number(d.id) || 0); }, 0);

  var added = 0, updated = 0;
  for (var r = 1; r < rows.length; r++) {
    var row = rows[r];
    var code = (iCode >= 0 ? row[iCode] : '').trim();
    if (!code) { continue; }
    var parentCode = (iParent >= 0 ? row[iParent] : '').trim();
    var d = normalizeDeviceFields({
      code: code,
      type: (iType >= 0 ? row[iType] : '').trim(),
      brand: (iBrand >= 0 ? row[iBrand] : '').trim(),
      model: (iModel >= 0 ? row[iModel] : '').trim(),
      ip: (iIp >= 0 ? row[iIp] : '').trim(),
      status: (iStatus >= 0 ? row[iStatus] : '').trim(),
      location: (iLoc >= 0 ? row[iLoc] : '').trim(),
      note: (iNote >= 0 ? row[iNote] : '').trim()
    });
    if (codeToId[code]) {
      var id = codeToId[code];
      var ex = state.devices.find(function (x) { return x.id === id; });
      d.parent_id = parentCode ? (codeToId[parentCode] || 0) : d.parent_id;
      Object.assign(ex, d, { id: id });
      updated++;
    } else {
      maxId++;
      d.id = maxId;
      d.parent_id = parentCode ? (codeToId[parentCode] || 0) : 0;
      codeToId[code] = d.id;
      state.devices.push(d);
      added++;
    }
  }
  save();
  return { added: added, updated: updated };
}

/* ---------- 种子管理员账号（首次初始化） ---------- */
async function ensureAdminUser() {
  if (!Array.isArray(state.users)) { state.users = []; }
  var admin = state.users.find(function (x) { return x.username === 'admin'; });
  if (!admin) {
    var h = await hashPassword('Admin@2026');
    state.users.push({ username: 'admin', pass_hash: h, name: '管理员', role: 'admin', phone: '', status: 'active', created: today() });
    save();
  }
}

/* ---------- 文件下载（浏览器 / WebView 通用） ---------- */
function download(filename, text, mime) {
  var blob = new Blob([text], { type: mime || 'text/plain;charset=utf-8' });
  var url = URL.createObjectURL(blob);
  var a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(function () { URL.revokeObjectURL(url); }, 1000);
}

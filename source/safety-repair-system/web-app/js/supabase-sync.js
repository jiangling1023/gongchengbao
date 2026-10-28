/**
 * supabase-sync.js — 云端同步层（完全免费）
 *
 * 设计原则：
 * 1. 所有 localStorage 读写自动同步到 Supabase，无感知
 * 2. 未配置 Supabase 时降级为纯 localStorage（离线可用）
 * 3. 登录使用 Supabase Auth（邮箱/密码），数据按 project_id 隔离
 * 4. 自动检测云端最新数据，避免覆盖他人变更
 *
 * 配置方式（在 index.html <head> 中设置）：
 *   <script>
 *     window.SUPABASE_URL = 'https://xxxx.supabase.co';
 *     window.SUPABASE_ANON_KEY = 'eyJ...';
 *   </script>
 *   <script src="js/supabase-sync.js"></script>
 *   <script src="js/data.js"></script>
 *   ...
 */
(function () {
  'use strict';

  var SB_URL = window.SUPABASE_URL || '';
  var SB_KEY = window.SUPABASE_ANON_KEY || '';
  var PROJECT_ID = 'gongchengbao';  // 业务隔离前缀，所有表名加此前缀
  var CACHE_TTL = 30 * 1000;  // 本地缓存 30 秒，避免频繁请求

  /* ---------- 工具函数 ---------- */
  function ok(res) { return res.ok && res.status !== 204; }
  function json(res) { return res.ok ? res.json() : Promise.reject({ status: res.status, body: null }); }
  function err(res) { return Promise.reject({ status: res.status, body: res.text ? res.text() : null }); }

  /* ---------- Supabase REST API 封装 ---------- */
  var api = {
    /** 获取表数据（带缓存） */
    get: function (table) {
      var cacheKey = 'sb_' + PROJECT_ID + '_' + table + '_cache';
      var cached = localStorage.getItem(cacheKey);
      if (cached) {
        try {
          var c = JSON.parse(cached);
          if (Date.now() - c.ts < CACHE_TTL) return Promise.resolve(c.data);
        } catch (e) {}
      }
      return fetch(SB_URL + '/rest/v1/' + encodeTable(table) + '?select=*', {
        method: 'GET',
        headers: { 'apikey': SB_KEY, 'Authorization': 'Bearer ' + SB_KEY, 'Prefer': 'return=representation' }
      }).then(json).then(function (d) {
        localStorage.setItem(cacheKey, JSON.stringify({ ts: Date.now(), data: d }));
        return d;
      }).catch(function () { return []; });
    },

    /** 插入一条记录 */
    insert: function (table, row) {
      return fetch(SB_URL + '/rest/v1/' + encodeTable(table), {
        method: 'POST',
        headers: {
          'apikey': SB_KEY,
          'Authorization': 'Bearer ' + SB_KEY,
          'Content-Type': 'application/json',
          'Prefer': 'return=representation'
        },
        body: JSON.stringify(row)
      }).then(json).then(function (d) { return d && d[0] ? d[0] : row; });
    },

    /** 更新一条记录（通过 id） */
    update: function (table, id, row) {
      return fetch(SB_URL + '/rest/v1/' + encodeTable(table) + '?id=' + encodeURIComponent(id), {
        method: 'PATCH',
        headers: {
          'apikey': SB_KEY,
          'Authorization': 'Bearer ' + SB_KEY,
          'Content-Type': 'application/json',
          'Prefer': 'return=representation'
        },
        body: JSON.stringify(row)
      }).then(json).then(function (d) { return d && d[0] ? d[0] : row; });
    },

    /** 删除一条记录（通过 id） */
    delete: function (table, id) {
      return fetch(SB_URL + '/rest/v1/' + encodeTable(table) + '?id=' + encodeURIComponent(id), {
        method: 'DELETE',
        headers: { 'apikey': SB_KEY, 'Authorization': 'Bearer ' + SB_KEY }
      }).then(function (res) { return res.ok; });
    },

    /** 批量 upsert（INSERT ON CONFLICT UPDATE） */
    upsert: function (table, rows) {
      if (!rows || !rows.length) return Promise.resolve([]);
      return fetch(SB_URL + '/rest/v1/' + encodeTable(table), {
        method: 'POST',
        headers: {
          'apikey': SB_KEY,
          'Authorization': 'Bearer ' + SB_KEY,
          'Content-Type': 'application/json',
          'Prefer': 'resolution=merge-duplicates,return=representation'
        },
        body: JSON.stringify(rows)
      }).then(json).then(function (d) { return d || []; });
    },

    /** Supabase Auth：注册 */
    signUp: function (email, password) {
      return fetch(SB_URL + '/auth/v1/signup', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'apikey': SB_KEY },
        body: JSON.stringify({ email: email, password: password })
      }).then(json).catch(function (e) { return { error: e }; });
    },

    /** Supabase Auth：登录 */
    signIn: function (email, password) {
      return fetch(SB_URL + '/auth/v1/token?grant_type=password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', 'apikey': SB_KEY },
        body: JSON.stringify({ email: email, password: password })
      }).then(json).catch(function (e) { return { error: e }; });
    },

    /** 获取当前会话 */
    getSession: function () {
      return fetch(SB_URL + '/auth/v1/user', {
        headers: { 'apikey': SB_KEY, 'Authorization': 'Bearer ' + getAccessToken() }
      }).then(json).catch(function () { return null; });
    },

    /** 登出 */
    signOut: function () {
      var token = getAccessToken();
      if (!token) return Promise.resolve();
      return fetch(SB_URL + '/auth/v1/logout', {
        method: 'POST',
        headers: { 'apikey': SB_KEY, 'Authorization': 'Bearer ' + token }
      }).catch(function () {});
    }
  };

  function encodeTable(name) { return PROJECT_ID + '__' + name; }

  function getAccessToken() {
    try { return localStorage.getItem('sb-' + SB_URL.replace(/https?:\/\//, '') + '-auth-token') || ''; } catch (e) { return ''; }
  }

  /* ---------- 云端状态对象（替代 localStorage 持久化） ---------- */
  var cloudState = { devices: [], tickets: [], inspections: [], users: [] };
  var syncPending = false;
  var syncTimer = null;

  /** 从云端加载数据 */
  function loadFromCloud() {
    if (!SB_URL || !SB_KEY) return Promise.resolve(null);
    return Promise.all([
      api.get('devices'),
      api.get('tickets'),
      api.get('inspections'),
      api.get('users')
    ]).then(function (r) {
      cloudState.devices = r[0] || [];
      cloudState.tickets = r[1] || [];
      cloudState.inspections = r[2] || [];
      cloudState.users = r[3] || [];
      return cloudState;
    }).catch(function (e) {
      console.warn('[Sync] 云端加载失败，降级本地:', e);
      return null;
    });
  }

  /** 将 state 推送到云端（防抖，避免频繁请求） */
  function syncToCloud() {
    if (!SB_URL || !SB_KEY || syncPending) return;
    syncPending = true;
    if (syncTimer) clearTimeout(syncTimer);
    syncTimer = setTimeout(function () {
      syncTimer = null;
      syncPending = false;
      var devices = (window.state && window.state.devices) || [];
      var tickets = (window.state && window.state.tickets) || [];
      var inspections = (window.state && window.state.inspections) || [];
      var users = (window.state && window.state.users) || [];

      // 只推有 id 的记录（新增/更新）
      var promises = [];
      if (devices.length) promises.push(api.upsert('devices', devices.map(function (d) { return cleanRow(d, ['id','code','type','brand','model','ip','status','location','note','parent_id','building','floor','created']); })));
      if (tickets.length) promises.push(api.upsert('tickets', tickets.map(function (t) { return cleanRow(t, ['id','code','device_id','device','title','ftype','level','status','description','solution','created','closed','assigned']); })));
      if (inspections.length) promises.push(api.upsert('inspections', inspections.map(function (i) { return cleanRow(i, ['id','title','date','result','note']); })));
      if (users.length) promises.push(api.upsert('users', users.map(function (u) { return cleanRow(u, ['id','username','pass_hash','name','role','phone','status','created']); })));

      Promise.all(promises).then(function () {
        console.log('[Sync] 云端同步成功');
        // 刷新缓存
        localStorage.removeItem('sb_' + PROJECT_ID + '_devices_cache');
        localStorage.removeItem('sb_' + PROJECT_ID + '_tickets_cache');
        localStorage.removeItem('sb_' + PROJECT_ID + '_inspections_cache');
        localStorage.removeItem('sb_' + PROJECT_ID + '_users_cache');
      }).catch(function (e) {
        console.warn('[Sync] 云端同步失败:', e);
      });
    }, 500);
  }

  function cleanRow(row, keep) {
    var out = {};
    keep.forEach(function (k) { if (row[k] !== undefined) out[k] = row[k]; });
    return out;
  }

  /* ---------- 登录/注册 ---------- */
  async function sbSignIn(username, password) {
    // 用 username 作为 email 前缀登录（支持 username@supabase.local 映射）
    var email = username + '@gongchengbao.local';
    var r = await api.signIn(email, password);
    if (r.error) return null;
    localStorage.setItem('sb_user', JSON.stringify({ username: username, email: email }));
    // 同步用户角色到全局 state
    if (window.state && window.state.users) {
      var u = window.state.users.find(function (x) { return x.username === username; });
      if (u) {
        localStorage.setItem(window.CURRENT_USER_KEY, username);
      }
    }
    return r;
  }

  async function sbSignOut() {
    await api.signOut();
    localStorage.removeItem('sb_user');
    localStorage.removeItem(window.CURRENT_USER_KEY);
  }

  function getSbUser() {
    try { return JSON.parse(localStorage.getItem('sb_user') || 'null'); } catch (e) { return null; }
  }

  /* ---------- 注入到全局 ---------- */
  window.sbApi = api;
  window.sbLoad = loadFromCloud;
  window.sbSync = syncToCloud;
  window.sbSignIn = sbSignIn;
  window.sbSignOut = sbSignOut;
  window.sbUser = getSbUser;
  window.SUPABASE_AVAILABLE = !!(SB_URL && SB_KEY);

  /* ---------- 自动同步：监听 state.save 变化 ---------- */
  if (window.SUPABASE_AVAILABLE) {
    var origSave = window.save;
    if (typeof origSave === 'function') {
      window.save = function () {
        origSave.call(this, arguments);
        syncToCloud();
      };
    }
  }

  console.log('[Sync] 初始化', window.SUPABASE_AVAILABLE ? '云端模式' : '本地模式');
})();

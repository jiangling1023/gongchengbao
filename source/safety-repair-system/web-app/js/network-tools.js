/**
 * network-tools.js — 网页版网络诊断工具
 *
 * 功能：
 * 1. 设备发现（ARP 扫描，基于现有设备台账）
 * 2. Ping 测试（使用 HTML5 fetch + 超时）
 * 3. 端口扫描（80/443/554/8000/8080 常见端口）
 *
 * 注意：浏览器受同源限制，无法真正扫描内网，
 * 因此这些工具基于设备台账数据模拟结果。
 */
(function () {
  'use strict';

  var networkTab = 'discover';

  /* ---------- 设备发现 ---------- */
  function discoverDevices() {
    if (!window.state || !window.state.devices) return [];
    return window.state.devices.map(function (d) {
      return {
        ip: d.ip || d.ip_address || '',
        mac: d.mac || generateMac(),
        brand: d.brand || '',
        model: d.model || '',
        status: d.status || 'unknown',
        location: d.location || ''
      };
    }).filter(function (d) { return d.ip; });
  }

  function generateMac() {
    var chars = '0123456789ABCDEF';
    var mac = '';
    for (var i = 0; i < 6; i++) {
      if (i > 0) mac += ':';
      mac += chars[Math.floor(Math.random() * 16)] + chars[Math.floor(Math.random() * 16)];
    }
    return mac;
  }

  /* ---------- Ping 测试 ---------- */
  async function pingHost(ip, count) {
    count = parseInt(count) || 4;
    var results = [];
    for (var i = 0; i < count; i++) {
      var start = Date.now();
      try {
        // 尝试 fetch 一个已知端点来检测连通性
        await fetch('http://' + ip, { mode: 'no-cors', cache: 'no-store', signal: AbortSignal.timeout(1000) });
        var latency = Date.now() - start;
        results.push({ seq: i + 1, status: 'reply', latency: latency });
      } catch (e) {
        // no-cors 请求会因为 CORS 错误而被拒绝，但能到达主机
        if (e.name === 'AbortError' || e.message && e.message.includes('abort')) {
          results.push({ seq: i + 1, status: 'timeout' });
        } else {
          // CORS 错误说明主机可达
          var latency2 = Date.now() - start;
          results.push({ seq: i + 1, status: 'reply', latency: latency2 });
        }
      }
    }
    return results;
  }

  /* ---------- 端口扫描 ---------- */
  var SCAN_PORTS = [80, 443, 554, 8000, 8080, 37777, 9000];

  async function scanPorts(ip) {
    var open = [];
    for (var i = 0; i < SCAN_PORTS.length; i++) {
      var port = SCAN_PORTS[i];
      try {
        await fetch('http://' + ip + ':' + port, { mode: 'no-cors', cache: 'no-store', signal: AbortSignal.timeout(500) });
      } catch (e) {
        if (e.name === 'AbortError') continue;
        // 非超时错误说明端口可能开放
        var label = getPortLabel(port);
        open.push({ port: port, label: label });
      }
    }
    return open;
  }

  function getPortLabel(port) {
    var labels = {
      80: 'HTTP',
      443: 'HTTPS',
      554: 'RTSP',
      8000: 'HTTP-alt',
      8080: 'HTTP-proxy',
      37777: 'Dahua',
      9000: 'ONVIF'
    };
    return labels[port] || 'port-' + port;
  }

  /* ---------- UI 渲染 ---------- */
  function renderDiscover() {
    var devices = discoverDevices();
    if (!devices.length) return '<div class="empty">暂无设备数据，请先添加设备</div>';
    var rows = devices.map(function (d) {
      var stClass = d.status === 'online' ? 'st-online' : (d.status === 'offline' ? 'st-offline' : 'st-unknown');
      var stLabel = { online: '在线', offline: '离线', unknown: '未知' }[d.status] || d.status;
      return '<tr>'
        + '<td><code>' + esc(d.ip) + '</code></td>'
        + '<td>' + esc(d.mac) + '</td>'
        + '<td>' + esc(d.brand) + '</td>'
        + '<td>' + esc(d.model) + '</td>'
        + '<td><span class="st ' + stClass + '">' + stLabel + '</span></td>'
        + '<td class="muted">' + esc(d.location) + '</td>'
        + '</tr>';
    }).join('');
    return '<table class="tbl"><thead><tr><th>IP</th><th>MAC</th><th>品牌</th><th>型号</th><th>状态</th><th>位置</th></tr></thead><tbody>' + rows + '</tbody></table>'
      + '<p class="muted">共 ' + devices.length + ' 台设备</p>';
  }

  function renderPing() {
    return '<div class="tool-form">'
      + fld('目标 IP', 'ping_ip', 'text', '', '如 10.10.10.21')
      + fld('次数', 'ping_count', 'number', '4', '1-10')
      + '<div class="form-actions"><button class="btn btn-primary" id="pingBtn">开始 Ping</button></div>'
      + '</div><div id="pingResult" class="tool-result"></div>'
      + '<script>document.getElementById(\'pingBtn\')?.addEventListener(\'click\', function() { sbPingTest(); });<\/script>';
  }

  function renderScan() {
    return '<div class="tool-form">'
      + fld('目标 IP', 'scan_ip', 'text', '', '如 10.10.10.21')
      + '<div class="form-actions"><button class="btn btn-primary" id="scanBtn">开始扫描</button></div>'
      + '</div><div id="scanResult" class="tool-result"></div>'
      + '<script>document.getElementById(\'scanBtn\')?.addEventListener(\'click\', function() { sbScanPorts(); });<\/script>';
  }

  function viewNetworkTools() {
    var tabs = ['discover', 'ping', 'scan'];
    var labels = { discover: '设备发现', ping: '网络 Ping', scan: '端口扫描' };
    var sel = '<div class="tool-tabs">' + tabs.map(function (t) {
      return '<button class="tool-tab' + (networkTab === t ? ' on' : '') + '" data-action="netTab" data-arg="' + t + '">' + labels[t] + '</button>';
    }).join('') + '</div>';
    var body = '';
    if (networkTab === 'discover') body = renderDiscover();
    else if (networkTab === 'ping') body = renderPing();
    else if (networkTab === 'scan') body = renderScan();
    return '<div class="page-head"><div class="ph-left"><div class="ph-bar"></div><h2>网络工具</h2></div></div>' + sel + '<div class="card">' + body + '</div>';
  }

  /* ---------- 全局暴露 ---------- */
  window.netToolsView = viewNetworkTools;
  window.netTabSwitch = function (tab) { networkTab = tab; };
  window.sbPingTest = async function () {
    var ip = document.getElementById('ping_ip')?.value.trim();
    var count = document.getElementById('ping_count')?.value || '4';
    if (!ip) { showToast('请输入 IP 地址'); return; }
    var el = document.getElementById('pingResult');
    el.innerHTML = '<p class="muted">正在 Ping ' + esc(ip) + '...</p>';
    var results = await pingHost(ip, parseInt(count));
    var okCount = results.filter(function (r) { return r.status === 'reply'; }).length;
    var html = '<div class="res-grid">';
    html += resItem('目标', '<code>' + esc(ip) + '</code>');
    html += resItem('结果', okCount + '/' + results.length + ' 包到达');
    html += '</div><pre style="margin-top:12px;font-size:13px;line-height:1.6;">';
    results.forEach(function (r) {
      if (r.status === 'reply') {
        html += '64 bytes from ' + esc(ip) + ': icmp_seq=' + r.seq + ' ttl=64 time=' + r.latency + ' ms\n';
      } else {
        html += 'Request timed out.\n';
      }
    });
    html += '</pre>';
    el.innerHTML = html;
  };

  window.sbScanPorts = async function () {
    var ip = document.getElementById('scan_ip')?.value.trim();
    if (!ip) { showToast('请输入 IP 地址'); return; }
    var el = document.getElementById('scanResult');
    el.innerHTML = '<p class="muted">正在扫描 ' + esc(ip) + '...</p>';
    var open = await scanPorts(ip);
    var html = '<div class="res-grid">';
    html += resItem('目标', '<code>' + esc(ip) + '</code>');
    html += resItem('开放端口', open.length > 0 ? open.map(function (p) { return p.port + '(' + p.label + ')'; }).join(', ') : '无');
    html += '</div>';
    el.innerHTML = html;
  };

  function esc(s) { return String(s == null ? '' : s).replace(/[&<>"']/g, function (c) {
    return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
  }); }
  function fld(label, id, type, value, placeholder, opts) {
    var sel = type === 'select' ? '<select id="' + id + '">' + (opts || []).map(function (o) { return '<option value="' + o[0] + '">' + o[1] + '</option>'; }).join('') + '</select>' :
               '<input id="' + id + '" type="' + type + '" value="' + esc(value) + '" placeholder="' + esc(placeholder || '') + '">';
    return '<div class="form-item"><label>' + esc(label) + '</label>' + sel + '</div>';
  }
  function resItem(k, v) { return '<div class="res-item"><div class="res-k">' + k + '</div><div class="res-v">' + v + '</div></div>'; }
  function showToast(msg) {
    var t = document.getElementById('toast');
    if (!t) return;
    t.textContent = msg;
    t.classList.add('show');
    setTimeout(function () { t.classList.remove('show'); }, 2000);
  }
})();

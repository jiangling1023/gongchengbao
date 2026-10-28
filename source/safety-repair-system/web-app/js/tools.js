/* ============================================================
 * tools.js — 工程宝工具（纯 JS 计算）
 * 子网 / PoE 预算 / 存储 / 带宽 / RTSP 生成
 * ============================================================ */

/* 子网计算器：返回掩码、网络、广播、首末主机、可用数 */
function calcSubnet(ip, prefix) {
  prefix = Number(prefix);
  if (!/^\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(ip) || prefix < 0 || prefix > 32) { return null; }
  var parts = ip.split('.').map(Number);
  if (parts.some(function (p) { return p > 255; })) { return null; }
  var ipInt = ((parts[0] << 24) | (parts[1] << 16) | (parts[2] << 8) | parts[3]) >>> 0;
  var maskInt = prefix === 0 ? 0 : ((0xFFFFFFFF << (32 - prefix)) >>> 0);
  var netInt = (ipInt & maskInt) >>> 0;
  var bcastInt = (netInt | (~maskInt >>> 0)) >>> 0;
  var first = netInt + 1, last = bcastInt - 1;
  var toIp = function (n) { return [(n >>> 24) & 255, (n >>> 16) & 255, (n >>> 8) & 255, n & 255].join('.'); };
  var hosts = prefix >= 31 ? 0 : (Math.pow(2, 32 - prefix) - 2);
  return {
    mask: toIp(maskInt),
    network: toIp(netInt),
    broadcast: toIp(bcastInt),
    first: prefix >= 31 ? toIp(netInt) : toIp(first),
    last: prefix >= 31 ? toIp(bcastInt) : toIp(last),
    hosts: hosts,
    prefix: prefix
  };
}

/* PoE 预算：总功率 × 0.8 安全系数 */
function calcPoE(ports, perPort) {
  ports = Number(ports) || 0;
  perPort = Number(perPort) || 0;
  var total = ports * perPort;
  return { ports: ports, perPort: perPort, total: +total.toFixed(1), usable: +(total * 0.8).toFixed(1) };
}

/* 存储计算：码流(Mbps) × 3600 × 24 × 天数 × 路数 ÷ 8 ÷ 1024 = GB */
function calcStorage(bitrate, days, channels) {
  bitrate = Number(bitrate) || 0;
  days = Number(days) || 0;
  channels = Number(channels) || 0;
  var gb = bitrate * 3600 * 24 * days * channels / 8 / 1024;
  return { bitrate: bitrate, days: days, channels: channels, gb: +gb.toFixed(1), tb: +(gb / 1024).toFixed(2) };
}

/* 带宽计算：基础 + 开销% */
function calcBandwidth(channels, bitrate, overhead) {
  channels = Number(channels) || 0;
  bitrate = Number(bitrate) || 0;
  overhead = Number(overhead) || 0;
  var base = channels * bitrate;
  return { channels: channels, bitrate: bitrate, overhead: overhead, base: +base.toFixed(1), total: +(base * (1 + overhead / 100)).toFixed(1) };
}

/* RTSP 地址生成 */
var RTSP_TEMPLATES = {
  '海康威视': 'rtsp://USER:PASS@IP:554/Streaming/Channels/101',
  '大华': 'rtsp://USER:PASS@IP:554/cam/realmonitor?channel=1&subtype=0',
  '宇视': 'rtsp://USER:PASS@IP:554/unicast/c1/s1/live',
  '华为': 'rtsp://USER:PASS@IP:554/LiveMedia/ch1/Media1',
  'TP-LINK': 'rtsp://USER:PASS@IP:554/stream1',
  '天地伟业': 'rtsp://USER:PASS@IP:554/1/1',
  '同为': 'rtsp://USER:PASS@IP:554/stream1',
  '博世': 'rtsp://USER:PASS@IP:554/rtsp_tunnel',
  '安讯士': 'rtsp://USER:PASS@IP:554/axis-media/media.amp',
  '汉邦高科': 'rtsp://USER:PASS@IP:554'
};
function rtspFor(brand, ip, user, pass) {
  var tpl = RTSP_TEMPLATES[brand] || 'rtsp://USER:PASS@IP:554/Streaming/Channels/101';
  return tpl
    .replace(/USER/g, user || 'admin')
    .replace(/PASS/g, pass || '')
    .replace(/IP/g, ip || '');
}

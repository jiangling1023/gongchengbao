package com.safetyrepair;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 设备发现与 ONVIF 后台操作（原生实现）
 *  - ONVIF WS-Discovery（UDP 多播 239.255.255.250:3702）发现摄像头
 *  - TCP 端口存活探测（通用设备，含门禁控制器等网内设备）
 *  - ONVIF 预览地址生成 / 远程重启（Digest 认证）
 */
public class Discovery {

    private static Context sContext;

    public static void init(Context c) { sContext = c; }

    /* ============ 总入口 ============ */
    public static String discoverAll(int timeoutMs) {
        JSONArray arr = new JSONArray();
        Map<String, JSONObject> cams = onvifProbe(timeoutMs);
        for (JSONObject o : cams.values()) arr.put(o);

        String subnet = getLocalSubnet();
        if (subnet != null) {
            Set<String> known = new HashSet<>();
            for (int i = 0; i < arr.length(); i++) {
                try { known.add(arr.getJSONObject(i).getString("ip")); } catch (Exception ignore) {}
            }
            Set<String> found = tcpScan(subnet, new int[]{80, 554, 8000, 8080, 60000, 37777, 34567}, Math.max(1500, timeoutMs / 3));
            for (String ip : found) {
                if (known.contains(ip)) continue;
                JSONObject o = new JSONObject();
                try {
                    o.put("ip", ip);
                    o.put("kind", "unknown");
                    o.put("source", "tcp");
                    o.put("manufacturer", "-");
                    o.put("model", "端口存活(未知网络设备)");
                } catch (Exception ignore) {}
                arr.put(o);
            }
        }
        return arr.toString();
    }

    /* ============ ONVIF WS-Discovery ============ */
    private static Map<String, JSONObject> onvifProbe(int timeoutMs) {
        Map<String, JSONObject> map = new HashMap<>();
        MulticastSocket sock = null;
        WifiManager.MulticastLock lock = null;
        try {
            WifiManager wm = (sContext != null) ? (WifiManager) sContext.getSystemService(Context.WIFI_SERVICE) : null;
            if (wm != null) { lock = wm.createMulticastLock("onvif_discovery"); lock.acquire(); }

            InetAddress group = InetAddress.getByName("239.255.255.250");
            int port = 3702;
            sock = new MulticastSocket(port);
            sock.setSoTimeout(timeoutMs);
            sock.joinGroup(group);

            String probe = buildProbe();
            byte[] send = probe.getBytes(StandardCharsets.UTF_8);
            DatagramPacket pkt = new DatagramPacket(send, send.length, group, port);
            sock.send(pkt);

            long end = System.currentTimeMillis() + timeoutMs;
            byte[] buf = new byte[8192];
            while (System.currentTimeMillis() < end) {
                try {
                    DatagramPacket recv = new DatagramPacket(buf, buf.length);
                    sock.receive(recv);
                    String data = new String(recv.getData(), 0, recv.getLength(), StandardCharsets.UTF_8);
                    parseMatch(data, map);
                } catch (SocketTimeoutException e) { break; } catch (Exception ignore) {}
            }
        } catch (Exception ignore) {
        } finally {
            try { if (sock != null) { sock.leaveGroup(InetAddress.getByName("239.255.255.250")); sock.close(); } } catch (Exception ignore) {}
            if (lock != null && lock.isHeld()) lock.release();
        }
        return map;
    }

    private static String buildProbe() {
        String uuid = UUID.randomUUID().toString();
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"\r\n" +
                "  xmlns:wsa=\"http://schemas.xmlsoap.org/ws/2004/08/addressing\"\r\n" +
                "  xmlns:tns=\"http://schemas.xmlsoap.org/ws/2005/04/discovery\">\r\n" +
                "  <soap:Header>\r\n" +
                "    <wsa:MessageID>urn:uuid:" + uuid + "</wsa:MessageID>\r\n" +
                "    <wsa:ReplyTo><wsa:Address>http://schemas.xmlsoap.org/ws/2004/08/addressing/role/anonymous</wsa:Address></wsa:ReplyTo>\r\n" +
                "    <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>\r\n" +
                "  </soap:Header>\r\n" +
                "  <soap:Body>\r\n" +
                "    <tns:Probe/>\r\n" +
                "  </soap:Body>\r\n" +
                "</soap:Envelope>\r\n";
    }

    private static void parseMatch(String data, Map<String, JSONObject> map) {
        try {
            String xaddrs = findTag(data, "XAddrs");
            if (xaddrs == null || xaddrs.trim().isEmpty()) return;
            String xaddr = xaddrs.trim().split("\\s+")[0];
            String ip = hostOf(xaddr);
            if (ip == null) return;

            String scopes = findTag(data, "Scopes");
            String mfr = pickScope(scopes, "mfr");
            String name = pickScope(scopes, "name");
            String hw = pickScope(scopes, "hardware");

            JSONObject o = new JSONObject();
            o.put("ip", ip);
            o.put("kind", "onvif");
            o.put("source", "onvif");
            o.put("manufacturer", mfr.isEmpty() ? (name.isEmpty() ? "ONVIF" : name) : mfr);
            o.put("model", hw.isEmpty() ? (name.isEmpty() ? "NetworkVideoTransmitter" : name) : hw);
            o.put("xaddr", xaddr);
            map.put(ip, o);
        } catch (Exception ignore) {}
    }

    private static String findTag(String xml, String tag) {
        Pattern p = Pattern.compile("<" + tag + ">(.*?)</" + tag + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    private static String pickScope(String scopes, String key) {
        if (scopes == null) return "";
        Pattern p = Pattern.compile("/(?:mfr|name|hardware)/([^\\s]+)", Pattern.CASE_INSENSITIVE);
        for (String s : scopes.split("\\s+")) {
            Matcher m = p.matcher(s);
            if (m.find()) return m.group(1);
        }
        return "";
    }

    private static String hostOf(String url) {
        try {
            String u = url.replace(" ", "");
            if (!u.startsWith("http")) u = "http://" + u;
            return new URL(u).getHost();
        } catch (Exception e) { return null; }
    }

    /* ============ TCP 端口存活探测 ============ */
    private static Set<String> tcpScan(final String subnet, final int[] ports, int timeoutMs) {
        final Set<String> result = new HashSet<>();
        ExecutorService pool = Executors.newFixedThreadPool(64);
        List<Future<Boolean>> futures = new ArrayList<>();
        List<String> ips = new ArrayList<>();
        for (int i = 1; i <= 254; i++) ips.add(subnet + i);

        for (final String ip : ips) {
            for (final int port : ports) {
                futures.add(pool.submit((Callable<Boolean>) () -> {
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(ip, port), Math.min(150, timeoutMs));
                        return true;
                    } catch (Exception e) { return false; }
                }));
            }
        }
        int idx = 0;
        for (Future<Boolean> f : futures) {
            try {
                if (Boolean.TRUE.equals(f.get(timeoutMs, TimeUnit.MILLISECONDS))) {
                    result.add(ips.get(idx / ports.length));
                }
            } catch (Exception ignore) {}
            idx++;
        }
        pool.shutdownNow();
        return result;
    }

    /* ============ 本地子网 / WiFi 信息 ============ */
    private static String getLocalSubnet() {
        try {
            WifiManager wm = (sContext != null) ? (WifiManager) sContext.getSystemService(Context.WIFI_SERVICE) : null;
            if (wm == null) return null;
            WifiInfo info = wm.getConnectionInfo();
            int ip = info.getIpAddress();
            if (ip == 0) return null;
            // little-endian
            String s = String.format("%d.%d.%d.", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff));
            return s; // x.x.x. 前缀
        } catch (Exception e) { return null; }
    }

    public static String getWifiInfo(Context ctx) {
        JSONObject o = new JSONObject();
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            WifiInfo info = wm.getConnectionInfo();
            String ssid = info.getSSID();
            if (ssid != null) ssid = ssid.replace("\"", "");
            int ip = info.getIpAddress();
            String ipStr = String.format("%d.%d.%d.%d", (ip & 0xff), (ip >> 8 & 0xff), (ip >> 16 & 0xff), (ip >> 24 & 0xff));
            o.put("ssid", ssid);
            o.put("ip", ipStr);
            o.put("subnet", getLocalSubnet());
        } catch (Exception e) {
            try { o.put("error", e.getMessage()); } catch (Exception ignore) {}
        }
        return o.toString();
    }

    /* ============ ONVIF 后台操作 ============ */
    public static String onvifOperation(String ip, String op, String cred) {
        String user = "admin", pass = "";
        if (cred != null && cred.contains("|")) {
            String[] p = cred.split("\\|", 2);
            user = p[0]; pass = p[1];
        }
        try {
            if ("preview".equals(op)) {
                String url = "rtsp://" + user + ":" + pass + "@" + ip + ":554/Streaming/Channels/101";
                return "预览地址(用VLC/播放器打开)：\n" + url;
            }
            if ("reboot".equals(op)) {
                String xaddr = "http://" + ip + "/onvif/device_service";
                return rebootDevice(xaddr, user, pass);
            }
            return "暂不支持的操作：" + op;
        } catch (Exception e) {
            return "操作失败：" + e.getMessage();
        }
    }

    private static String rebootDevice(String xaddr, String user, String pass) throws Exception {
        String soap = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\"\r\n" +
                "  xmlns:tas=\"http://www.onvif.org/ver10/device/wsdl\">\r\n" +
                "  <soap:Body><tas:SystemReboot/></soap:Body></soap:Envelope>";
        String body = httpPost(xaddr, soap, user, pass, null);
        if (body != null && body.contains("SystemRebootResponse")) return "已发送重启指令，设备将重启。";
        return "重启未确认(可能凭据错误或设备不支持匿名)：" + (body == null ? "无响应" : body.substring(0, Math.min(120, body.length())));
    }

    /** 发送 ONVIF POST，必要时走 Digest 认证 */
    private static String httpPost(String url, String soap, String user, String pass, String authHeader) throws Exception {
        URL u = new URL(url);
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(4000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
        if (authHeader != null) conn.setRequestProperty("Authorization", authHeader);
        try (OutputStream os = conn.getOutputStream()) { os.write(soap.getBytes(StandardCharsets.UTF_8)); }

        int code = conn.getResponseCode();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(
                code == 401 ? conn.getErrorStream() : conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line; while ((line = br.readLine()) != null) sb.append(line);
        }
        if (code == 401 && authHeader == null) {
            String www = conn.getHeaderField("WWW-Authenticate");
            if (www != null && www.contains("Digest")) {
                String auth = digestHeader(www, user, pass, "POST", url);
                return httpPost(url, soap, user, pass, auth);
            }
        }
        return sb.toString();
    }

    private static String digestHeader(String www, String user, String pass, String method, String uri) {
        try {
            String realm = between(www, "realm=\"", "\"");
            String nonce = between(www, "nonce=\"", "\"");
            String qop = www.contains("qop=") ? between(www, "qop=\"", "\"") : "";
            String nc = "00000001";
            String cnonce = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            String ha1 = md5(user + ":" + realm + ":" + pass);
            String ha2 = md5(method + ":" + uri);
            String response;
            if (!qop.isEmpty()) response = md5(ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2);
            else response = md5(ha1 + ":" + nonce + ":" + ha2);
            StringBuilder h = new StringBuilder("Digest username=\"").append(user).append("\"");
            h.append(", realm=\"").append(realm).append("\"");
            h.append(", nonce=\"").append(nonce).append("\"");
            h.append(", uri=\"").append(uri).append("\"");
            if (!qop.isEmpty()) h.append(", qop=").append(qop).append(", nc=").append(nc).append(", cnonce=\"").append(cnonce).append("\"");
            h.append(", response=\"").append(response).append("\"");
            return h.toString();
        } catch (Exception e) { return null; }
    }

    private static String between(String s, String a, String b) {
        int i = s.indexOf(a); if (i < 0) return ""; i += a.length();
        int j = s.indexOf(b, i); if (j < 0) return s.substring(i);
        return s.substring(i, j);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : b) sb.append(String.format("%02x", x));
            return sb.toString();
        } catch (Exception e) { return ""; }
    }

    /* ============ 工程宝：Ping ============ */
    public static String ping(String host, int count) {
        JSONObject res = new JSONObject();
        try {
            int c = Math.max(1, Math.min(10, count));
            Process p = Runtime.getRuntime().exec(new String[]{"ping", "-c", String.valueOf(c), "-w", String.valueOf(Math.max(8, c * 2)), host});
            BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(); String line;
            while ((line = br.readLine()) != null) sb.append(line).append("\n");
            p.waitFor();
            String out = sb.toString();
            java.util.List<Double> times = new java.util.ArrayList<>();
            Matcher m = Pattern.compile("time=([0-9.]+)").matcher(out);
            while (m.find()) times.add(Double.parseDouble(m.group(1)));
            int sent = c, recv = times.size();
            int loss = sent > 0 ? Math.round((sent - recv) * 100f / sent) : 100;
            double avg = 0; for (double t : times) avg += t; if (recv > 0) avg /= recv;
            res.put("ok", recv > 0); res.put("host", host);
            res.put("sent", sent); res.put("recv", recv); res.put("loss", loss);
            res.put("times", new JSONArray(times)); res.put("avg", avg);
        } catch (Exception e) {
            try { res.put("ok", false); res.put("error", e.getMessage()); } catch (Exception ignore) {}
        }
        return res.toString();
    }

    /* ============ 工程宝：Traceroute（逐跳 TTL ping） ============ */
    public static String traceroute(String host, int maxHops) {
        JSONObject res = new JSONObject();
        try {
            JSONArray hops = new JSONArray();
            boolean unsupported = false;
            for (int ttl = 1; ttl <= maxHops; ttl++) {
                Process p = Runtime.getRuntime().exec(new String[]{"ping", "-c", "1", "-t", String.valueOf(ttl), "-w", "2", host});
                BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder(); String line;
                while ((line = br.readLine()) != null) sb.append(line).append("\n");
                p.waitFor();
                String out = sb.toString();
                if (out.contains("invalid option") || out.contains("Usage")) { unsupported = true; break; }
                JSONObject hop = new JSONObject(); hop.put("ttl", ttl); hop.put("ip", "*"); hop.put("host", ""); hop.put("time", null);
                Matcher mf = Pattern.compile("From\\s+([0-9.]+)").matcher(out);
                if (mf.find()) hop.put("ip", mf.group(1));
                Matcher mb = Pattern.compile("bytes from\\s+([^:(\\s]+)").matcher(out);
                boolean finalHop = false;
                if (mb.find()) {
                    String from = mb.group(1).trim();
                    hop.put("ip", from);
                    Matcher mh = Pattern.compile("\\(([0-9.]+)\\)").matcher(out);
                    if (mh.find()) hop.put("host", mh.group(1));
                    finalHop = true;
                }
                Matcher mt = Pattern.compile("time=([0-9.]+)").matcher(out);
                if (mt.find()) hop.put("time", Double.parseDouble(mt.group(1)));
                hops.put(hop);
                if (finalHop) break;
            }
            if (unsupported) { res.put("ok", false); res.put("error", "本设备 ping 不支持 -t 选项，无法路由跟踪，请改用 Ping 工具"); }
            else { res.put("ok", true); res.put("hops", hops); }
        } catch (Exception e) {
            try { res.put("ok", false); res.put("error", e.getMessage()); } catch (Exception ignore) {}
        }
        return res.toString();
    }

    /* ============ 工程宝：端口扫描（单 IP） ============ */
    public static String portScan(String ip, int[] ports, int timeoutMs) {
        JSONObject res = new JSONObject();
        try {
            JSONArray open = new JSONArray(), closed = new JSONArray();
            ExecutorService pool = Executors.newFixedThreadPool(32);
            Map<Integer, Future<Boolean>> map = new HashMap<>();
            for (int p : ports) map.put(p, pool.submit((Callable<Boolean>) () -> {
                try (Socket s = new Socket()) { s.connect(new InetSocketAddress(ip, p), timeoutMs); return true; }
                catch (Exception e) { return false; }
            }));
            for (Map.Entry<Integer, Future<Boolean>> e : map.entrySet()) {
                try { if (Boolean.TRUE.equals(e.getValue().get(timeoutMs + 800, TimeUnit.MILLISECONDS))) open.put(e.getKey()); else closed.put(e.getKey()); }
                catch (Exception ex) { closed.put(e.getKey()); }
            }
            pool.shutdownNow();
            res.put("ok", true); res.put("ip", ip); res.put("open", open); res.put("closed", closed);
        } catch (Exception e) {
            try { res.put("ok", false); res.put("error", e.getMessage()); } catch (Exception ignore) {}
        }
        return res.toString();
    }

    /* ============ 工程宝：WiFi 信号分析 ============ */
    public static String wifiScan(Context ctx) {
        JSONObject res = new JSONObject();
        try {
            WifiManager wm = (WifiManager) ctx.getSystemService(Context.WIFI_SERVICE);
            if (wm == null) { res.put("ok", false); res.put("error", "无 WifiManager"); return res.toString(); }
            try { wm.startScan(); } catch (Exception ignore) {}
            Thread.sleep(1500);
            List<ScanResult> results = wm.getScanResults();
            JSONArray aps = new JSONArray();
            for (ScanResult r : results) {
                JSONObject o = new JSONObject();
                o.put("ssid", r.SSID); o.put("bssid", r.BSSID); o.put("rssi", r.level); o.put("freq", r.frequency);
                int ch = r.frequency >= 4900 ? ((r.frequency - 5000) / 5 + 100) : (r.frequency >= 2400 ? ((r.frequency - 2412) / 5 + 1) : -1);
                o.put("channel", ch);
                aps.put(o);
            }
            res.put("ok", true); res.put("aps", aps);
        } catch (SecurityException se) {
            try { res.put("ok", false); res.put("error", "缺少定位权限：请在系统设置→应用→权限中授予定位权限，并打开 WiFi/位置"); } catch (Exception ignore) {}
        } catch (Exception e) {
            try { res.put("ok", false); res.put("error", e.getMessage()); } catch (Exception ignore) {}
        }
        return res.toString();
    }

    /* ============ 工程宝：ONVIF PTZ 云台 ============ */
    public static String onvifPtz(String ip, String dir, String cred) {
        String user = "admin", pass = "";
        if (cred != null && cred.contains("|")) { String[] p = cred.split("\\|", 2); user = p[0]; pass = p[1]; }
        try {
            String token = getProfileToken(ip, user, pass);
            if (token == null) return "PTZ 失败：未取到 ONVIF Profile（设备可能不支持 ONVIF 或凭据错误）";
            if ("stop".equals(dir)) { ptzStop(ip, token, user, pass); return "已停止"; }
            double[] v = ptzVector(dir);
            ptzMove(ip, token, v[0], v[1], v[2], user, pass);
            try { Thread.sleep(600); } catch (Exception ignore) {}
            ptzStop(ip, token, user, pass);
            return "已发送 " + dir + " 指令";
        } catch (Exception e) { return "PTZ 失败：" + e.getMessage(); }
    }

    private static double[] ptzVector(String dir) {
        switch (dir) {
            case "up": return new double[]{0, 1, 0};
            case "down": return new double[]{0, -1, 0};
            case "left": return new double[]{-1, 0, 0};
            case "right": return new double[]{1, 0, 0};
            case "upleft": return new double[]{-1, 1, 0};
            case "upright": return new double[]{1, 1, 0};
            case "downleft": return new double[]{-1, -1, 0};
            case "downright": return new double[]{1, -1, 0};
            case "zoomin": return new double[]{0, 0, 1};
            case "zoomout": return new double[]{0, 0, -1};
            default: return new double[]{0, 0, 0};
        }
    }

    private static String getProfileToken(String host, String user, String pass) throws Exception {
        String url = "http://" + host + "/onvif/media";
        String soap = soapEnvelope("<tr2:GetProfiles/>", "http://www.onvif.org/ver20/media/wsdl", "tr2");
        String body = httpPost(url, soap, user, pass, null);
        if (body == null) return null;
        Matcher m = Pattern.compile(":token>([^<]+)<").matcher(body);
        return m.find() ? m.group(1) : null;
    }

    private static void ptzMove(String host, String token, double x, double y, double z, String user, String pass) throws Exception {
        String url = "http://" + host + "/onvif/PTZ";
        String inner = "<ptz:ProfileToken>" + token + "</ptz:ProfileToken>" +
                "<ptz:Velocity><tt:PanTilt x=\"" + x + "\" y=\"" + y + "\" z=\"0\"/><tt:Zoom x=\"" + z + "\"/></ptz:Velocity>";
        String soap = soapEnvelope("<ptz:ContinuousMove>" + inner + "</ptz:ContinuousMove>",
                "http://www.onvif.org/ver20/ptz/wsdl", "ptz");
        httpPost(url, soap, user, pass, null);
    }

    private static void ptzStop(String host, String token, String user, String pass) throws Exception {
        String url = "http://" + host + "/onvif/PTZ";
        String inner = "<ptz:ProfileToken>" + token + "</ptz:ProfileToken><ptz:PanTilt>true</ptz:PanTilt><ptz:Zoom>true</ptz:Zoom>";
        String soap = soapEnvelope("<ptz:Stop>" + inner + "</ptz:Stop>",
                "http://www.onvif.org/ver20/ptz/wsdl", "ptz");
        httpPost(url, soap, user, pass, null);
    }

    /* ============ 工程宝：ONVIF 快照 ============ */
    public static String onvifSnapshot(String ip, String cred) {
        String user = "admin", pass = "";
        if (cred != null && cred.contains("|")) { String[] p = cred.split("\\|", 2); user = p[0]; pass = p[1]; }
        try {
            String token = getProfileToken(ip, user, pass);
            if (token == null) return "{\"ok\":false,\"error\":\"未取到 ONVIF Profile（设备可能不支持或被认证拦截）\"}";
            String url = "http://" + ip + "/onvif/media";
            String soap = soapEnvelope("<tr2:GetSnapshot><tr2:ProfileToken>" + token + "</tr2:ProfileToken></tr2:GetSnapshot>",
                    "http://www.onvif.org/ver20/media/wsdl", "tr2");
            String b64 = httpGetBinary(url, soap, user, pass, null);
            if (b64 == null) return "{\"ok\":false,\"error\":\"快照无响应\"}";
            JSONObject o = new JSONObject(); o.put("ok", true); o.put("ip", ip);
            o.put("data", "data:image/jpeg;base64," + b64);
            return o.toString();
        } catch (Exception e) {
            return "{\"ok\":false,\"error\":\"" + e.getMessage() + "\"}";
        }
    }

    private static String httpGetBinary(String url, String soap, String user, String pass, String auth) throws Exception {
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000); conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8");
        if (auth != null) conn.setRequestProperty("Authorization", auth);
        try (OutputStream os = conn.getOutputStream()) { os.write(soap.getBytes(StandardCharsets.UTF_8)); }
        int code = conn.getResponseCode();
        if (code == 401 && auth == null) {
            String www = conn.getHeaderField("WWW-Authenticate");
            if (www != null && www.contains("Digest")) {
                String a = digestHeader(www, user, pass, "POST", url);
                return httpGetBinary(url, soap, user, pass, a);
            }
        }
        InputStream is = (code == 200) ? conn.getInputStream() : conn.getErrorStream();
        if (is == null) return null;
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[4096]; int n;
        while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
        return Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP);
    }

    /* ============ ONVIF 设备信息查询（GetDeviceInformation） ============ */
    public static String onvifDeviceInfo(String ip, String user, String pass) {
        JSONObject o = new JSONObject();
        try {
            String url = "http://" + ip + "/onvif/device_service";
            String soap = soapEnvelope("<tds:GetDeviceInformation/>", "http://www.onvif.org/ver10/device/wsdl", "tds");
            String body = httpPost(url, soap, user, pass, null);
            if (body == null) { o.put("ok", false); o.put("error", "无响应（设备不在线/不支持ONVIF/凭据错误）"); return o.toString(); }
            o.put("ok", true);
            o.put("manufacturer", tagVal(body, "Manufacturer"));
            o.put("model", tagVal(body, "Model"));
            o.put("firmware", tagVal(body, "FirmwareVersion"));
            o.put("serial", tagVal(body, "SerialNumber"));
            o.put("hardware", tagVal(body, "HardwareId"));
        } catch (Exception e) {
            try { o.put("ok", false); o.put("error", e.getMessage()); } catch (Exception ignore) {}
        }
        return o.toString();
    }

    /* ============ ONVIF 门禁档案(AC Profile)发现 ============ */
    public static String onvifAcProbe(int timeoutMs) {
        JSONArray arr = new JSONArray();
        Map<String, JSONObject> cams = onvifProbe(timeoutMs);
        for (JSONObject o : cams.values()) {
            try {
                String ip = o.getString("ip");
                boolean ac = hasOnvifAccess(ip, "admin", "");
                o.put("kind", "onvif-ac");
                o.put("acSupport", ac);
            } catch (Exception ignore) {}
            arr.put(o);
        }
        return arr.toString();
    }

    private static boolean hasOnvifAccess(String ip, String user, String pass) {
        try {
            String url = "http://" + ip + "/onvif/device_service";
            String soap = soapEnvelope("<tds:GetCapabilities><tds:Category>All</tds:Category></tds:GetCapabilities>",
                    "http://www.onvif.org/ver10/device/wsdl", "tds");
            String body = httpPost(url, soap, user, pass, null);
            if (body == null) return false;
            String low = body.toLowerCase();
            return low.contains("accesscontrol") || low.contains("<access") || low.contains("accesscontrolwsdl");
        } catch (Exception e) { return false; }
    }

    /* ============ 品牌已知端口 · 子网扫描 ============ */
    public static String subnetScan(int[] ports, int timeoutMs) {
        JSONArray arr = new JSONArray();
        String subnet = getLocalSubnet();
        if (subnet == null) return arr.toString();
        Set<String> found = tcpScan(subnet, ports, Math.max(1500, timeoutMs));
        for (String ip : found) arr.put(ip);
        return arr.toString();
    }

    private static String tagVal(String xml, String name) {
        if (xml == null) return "";
        Pattern p = Pattern.compile("<(?:\\w+:)?" + name + ">(.*?)</(?:\\w+:)?" + name + ">", Pattern.DOTALL);
        Matcher m = p.matcher(xml);
        return m.find() ? m.group(1).trim() : "";
    }

    private static String soapEnvelope(String bodyInner, String ns, String prefix) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n" +
                "<soap:Envelope xmlns:soap=\"http://www.w3.org/2003/05/soap-envelope\" " +
                "xmlns:tt=\"http://www.onvif.org/ver10/schema\" xmlns:" + prefix + "=\"" + ns + "\">" +
                "<soap:Body>" + bodyInner + "</soap:Body></soap:Envelope>";
    }
}

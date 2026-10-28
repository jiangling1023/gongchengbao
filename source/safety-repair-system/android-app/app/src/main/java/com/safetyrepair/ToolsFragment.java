package com.safetyrepair;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;
import org.json.JSONArray;
import org.json.JSONObject;

/** 工程宝工具箱：计算类(离线) + 探测类(原生网络层)。 */
public class ToolsFragment extends Fragment {
    private LinearLayout content;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        // 工具选择：竖版 Spinner 下拉（替代原横版 HorizontalScrollView 标签栏），
        // 节省纵向空间，且 8 个工具全部保留可用。
        final String[] tools = {"子网计算", "PoE预算", "存储计算", "带宽计算", "默认密码", "RTSP生成", "Ping", "端口扫描"};
        Spinner toolSpinner = new Spinner(getActivity());
        toolSpinner.setBackgroundColor(Color.parseColor("#eef2f7"));
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(),
                android.R.layout.simple_spinner_item, tools);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        toolSpinner.setAdapter(spinnerAdapter);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        spLp.setMargins(0, 0, 0, 8);
        toolSpinner.setLayoutParams(spLp);
        root.addView(toolSpinner);

        content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);
        sv.addView(root);

        toolSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                setTool(tools[position]);
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
                // 保持当前选择
            }
        });

        setTool("子网计算");
        return sv;
    }

    private void setTool(String t) {
        content.removeAllViews();
        content.addView(build(t));
    }

    private View build(String t) {
        switch (t) {
            case "子网计算": return buildSubnet();
            case "PoE预算": return buildPoe();
            case "存储计算": return buildStorage();
            case "带宽计算": return buildBandwidth();
            case "默认密码": return buildPass();
            case "RTSP生成": return buildRtsp();
            case "Ping": return buildPing();
            case "端口扫描": return buildPort();
        }
        return new TextView(getActivity());
    }

    /* ---------- 子网计算 ---------- */
    private View buildSubnet() {
        LinearLayout L = col();
        final EditText ip = et("IP 地址，如 192.168.1.10");
        final Spinner pre = new Spinner(getActivity());
        String[] ps = new String[23];
        for (int i = 8; i <= 30; i++) ps[i - 8] = "/" + i;
        pre.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, ps));
        pre.setSelection(16); // /24
        L.addView(lab("IP 地址")); L.addView(ip);
        L.addView(lab("掩码前缀")); L.addView(pre);
        final TextView r = resBox();
        L.addView(btn("计算", "#1f6feb", v -> {
            try {
                long ipi = ipToInt(ip.getText().toString().trim());
                int p = Integer.parseInt(pre.getSelectedItem().toString().replace("/", ""));
                long mask = p == 0 ? 0 : (0xFFFFFFFFL << (32 - p)) & 0xFFFFFFFFL;
                long net = ipi & mask;
                long bcast = net | (~mask & 0xFFFFFFFFL);
                long hosts = (long) Math.pow(2, 32 - p) - 2;
                r.setText("网络地址：" + intToIp(net) + "/" + p +
                        "\n广播地址：" + intToIp(bcast) +
                        "\n可用范围：" + intToIp(net + 1) + " ~ " + intToIp(bcast - 1) +
                        "\n可用主机数：" + hosts);
            } catch (Exception e) { r.setText("输入有误：" + e.getMessage()); }
        }));
        L.addView(r);
        return L;
    }

    /* ---------- PoE 预算 ---------- */
    private View buildPoe() {
        LinearLayout L = col();
        final EditText ports = et("端口数，如 24");
        final Spinner std = new Spinner(getActivity());
        std.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, new String[]{"af ≈15.4W", "at ≈30W", "bt ≈75W"}));
        L.addView(lab("端口数")); L.addView(ports);
        L.addView(lab("PoE 标准(单口功率)")); L.addView(std);
        final TextView r = resBox();
        L.addView(btn("计算", "#1f6feb", v -> {
            try {
                int n = Integer.parseInt(ports.getText().toString().trim());
                double per = std.getSelectedItemPosition() == 0 ? 15.4 : (std.getSelectedItemPosition() == 1 ? 30 : 75);
                double need = n * per;
                double safe = need * 0.8;
                boolean over = per > (std.getSelectedItemPosition() == 2 ? 90 : (std.getSelectedItemPosition() == 1 ? 30 : 15.4));
                r.setText("端口数：" + n + "\n单口功率：" + per + " W" +
                        "\n总功率需求：" + String.format("%.1f", need) + " W" +
                        "\n建议供电(×0.8安全系数)：" + String.format("%.1f", safe) + " W" +
                        "\n判定：" + (over ? "⚠ 超标准，建议独立 12V 供电" : "✅ 在 PoE 标准内") +
                        "\n提示：立林门禁控制器多为 12V/24V 独立供电，电锁必须独立电源+UPS");
            } catch (Exception e) { r.setText("输入有误"); }
        }));
        L.addView(r);
        return L;
    }

    /* ---------- 存储计算 ---------- */
    private View buildStorage() {
        LinearLayout L = col();
        final EditText rate = et("单路码率 Mbps，如 4");
        final EditText days = et("保存天数，如 30");
        final EditText ch = et("路数，如 32");
        L.addView(lab("单路码率(Mbps)")); L.addView(rate);
        L.addView(lab("保存天数")); L.addView(days);
        L.addView(lab("路数")); L.addView(ch);
        final TextView r = resBox();
        L.addView(btn("计算", "#1f6feb", v -> {
            try {
                double rt = Double.parseDouble(rate.getText().toString().trim());
                int d = Integer.parseInt(days.getText().toString().trim());
                int c = Integer.parseInt(ch.getText().toString().trim());
                double gb = rt * 3600 * 24 * d * c / 8;
                double tb = gb / 1024;
                int disks = (int) Math.ceil(tb / 8);
                r.setText(String.format("总容量：%.0f GB（%.1f TB）", gb, tb) +
                        "\n建议：NVR 盘位×单盘容量 ≥ " + String.format("%.1f", tb) + " TB" +
                        "\n（按 8TB/盘约需 " + Math.max(1, disks) + " 块）");
            } catch (Exception e) { r.setText("输入有误"); }
        }));
        L.addView(r);
        return L;
    }

    /* ---------- 带宽计算 ---------- */
    private View buildBandwidth() {
        LinearLayout L = col();
        final EditText ch = et("路数，如 64");
        final EditText rate = et("单路码率 Mbps，如 4");
        final Spinner red = new Spinner(getActivity());
        red.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, new String[]{"冗余 1.2", "冗余 1.5"}));
        L.addView(lab("路数")); L.addView(ch);
        L.addView(lab("单路码率(Mbps)")); L.addView(rate);
        L.addView(lab("冗余系数")); L.addView(red);
        final TextView r = resBox();
        L.addView(btn("计算", "#1f6feb", v -> {
            try {
                int c = Integer.parseInt(ch.getText().toString().trim());
                double rt = Double.parseDouble(rate.getText().toString().trim());
                double rd = red.getSelectedItemPosition() == 0 ? 1.2 : 1.5;
                double bw = c * rt * rd;
                String sug = bw <= 1000 ? "千兆上联足够" : (bw <= 2500 ? "建议双千兆聚合或万兆" : "建议万兆上联");
                r.setText(String.format("总上联带宽需求：%.0f Mbps", bw) + "\n建议：" + sug);
            } catch (Exception e) { r.setText("输入有误"); }
        }));
        L.addView(r);
        return L;
    }

    /* ---------- 默认密码 ---------- */
    private View buildPass() {
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout L = col();
        for (String[] p : ContentData.DEFAULT_PASS) {
            L.addView(card(p[0] + " ｜ " + p[1] + " / " + p[2], p[3], "#d1242f", "#fff3f3"));
        }
        L.addView(card("合规提示", "仅用于授权设备的初始化；设备上线前必须修改默认密码并关闭不必要的服务(UPnP/Telnet)。", "#bc4c00", "#fff8e6"));
        sv.addView(L);
        return sv;
    }

    /* ---------- RTSP 生成 ---------- */
    private View buildRtsp() {
        LinearLayout L = col();
        final Spinner brand = new Spinner(getActivity());
        String[] names = new String[ContentData.CAMERA_BRANDS.length];
        for (int i = 0; i < names.length; i++) names[i] = ContentData.CAMERA_BRANDS[i][0];
        brand.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, names));
        final EditText ip = et("设备 IP");
        final EditText user = et("用户名(默认 admin)");
        final EditText pass = et("密码");
        L.addView(lab("品牌")); L.addView(brand);
        L.addView(lab("IP")); L.addView(ip);
        L.addView(lab("用户名")); L.addView(user);
        L.addView(lab("密码")); L.addView(pass);
        final TextView r = resBox();
        L.addView(btn("生成", "#1f6feb", v -> {
            try {
                String tpl = ContentData.CAMERA_BRANDS[brand.getSelectedItemPosition()][4];
                String u = user.getText().toString().trim();
                String p = pass.getText().toString().trim();
                String out = tpl.replace("u", u.isEmpty() ? "admin" : u).replace("p", p);
                out = out.replace("IP", ip.getText().toString().trim());
                r.setText("取流地址（用 VLC/播放器打开）：\n" + out);
            } catch (Exception e) { r.setText("生成失败"); }
        }));
        L.addView(r);
        return L;
    }

    /* ---------- Ping ---------- */
    private View buildPing() {
        LinearLayout L = col();
        final EditText host = et("目标 IP 或域名");
        final EditText cnt = et("次数(1-10)，默认 4");
        L.addView(lab("目标")); L.addView(host);
        L.addView(lab("次数")); L.addView(cnt);
        final TextView r = resBox();
        L.addView(btn("Ping", "#1565c0", v -> {
            r.setText("测试中…");
            new Thread(() -> {
                int c = 4;
                try { c = Integer.parseInt(cnt.getText().toString().trim()); } catch (Exception ignore) {}
                final String json = Discovery.ping(host.getText().toString().trim(), c);
                if (getActivity() != null) getActivity().runOnUiThread(() -> {
                    try {
                        JSONObject o = new JSONObject(json);
                        if (o.optBoolean("ok", false))
                            r.setText("主机：" + o.optString("host") + "\n发送：" + o.optInt("sent") + " 接收：" + o.optInt("recv") +
                                    "\n丢包率：" + o.optInt("loss") + "%\n平均时延：" + o.optDouble("avg") + " ms");
                        else r.setText("失败：" + o.optString("error", "未知"));
                    } catch (Exception e) { r.setText("解析失败"); }
                });
            }).start();
        }));
        L.addView(r);
        return L;
    }

    /* ---------- 端口扫描 ---------- */
    private View buildPort() {
        LinearLayout L = col();
        final EditText ip = et("目标 IP");
        final EditText ports = et("端口，逗号分隔，如 80,554,8000,37777");
        L.addView(lab("目标 IP")); L.addView(ip);
        L.addView(lab("端口")); L.addView(ports);
        final TextView r = resBox();
        L.addView(btn("扫描", "#1565c0", v -> {
            r.setText("扫描中…");
            try {
                String[] ps = ports.getText().toString().trim().split(",");
                int[] arr = new int[ps.length];
                for (int i = 0; i < ps.length; i++) arr[i] = Integer.parseInt(ps[i].trim());
                final int[] fa = arr;
                new Thread(() -> {
                    final String json = Discovery.portScan(ip.getText().toString().trim(), fa, 1500);
                    if (getActivity() != null) getActivity().runOnUiThread(() -> {
                        try {
                            JSONObject o = new JSONObject(json);
                            JSONArray open = o.optJSONArray("open");
                            StringBuilder sb = new StringBuilder("开放端口：");
                            for (int i = 0; i < open.length(); i++) sb.append(open.getInt(i)).append(" ");
                            if (open.length() == 0) sb.append("无");
                            r.setText(sb.toString());
                        } catch (Exception e) { r.setText("解析失败"); }
                    });
                }).start();
            } catch (Exception e) { r.setText("端口格式有误"); }
        }));
        L.addView(r);
        return L;
    }

    /* ---------- helpers ---------- */
    private LinearLayout col() {
        LinearLayout L = new LinearLayout(getActivity());
        L.setOrientation(LinearLayout.VERTICAL);
        return L;
    }

    private EditText et(String hint) {
        EditText e = new EditText(getActivity());
        e.setHint(hint);
        e.setBackgroundColor(Color.WHITE);
        e.setPadding(12, 10, 12, 10);
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 8);
        e.setLayoutParams(p);
        return e;
    }

    private TextView lab(String t) {
        TextView v = new TextView(getActivity());
        v.setText(t);
        v.setTextSize(12);
        v.setTextColor(Color.parseColor("#6b7280"));
        v.setPadding(0, 10, 0, 2);
        return v;
    }

    private Button btn(String t, String color, View.OnClickListener l) {
        Button b = new Button(getActivity());
        b.setText(t);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 4, 0, 8);
        b.setLayoutParams(p);
        b.setOnClickListener(l);
        return b;
    }

    private TextView resBox() {
        TextView t = new TextView(getActivity());
        t.setTextSize(14);
        t.setTextColor(Color.parseColor("#1f2328"));
        t.setBackgroundColor(Color.parseColor("#eef4ff"));
        t.setPadding(12, 12, 12, 12);
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 6, 0, 0);
        t.setLayoutParams(p);
        return t;
    }

    private LinearLayout card(String title, String body, String tc, String bg) {
        LinearLayout c = new LinearLayout(getActivity());
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackgroundColor(Color.parseColor(bg));
        c.setPadding(14, 12, 14, 12);
        android.widget.LinearLayout.LayoutParams p = new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, 10);
        c.setLayoutParams(p);
        TextView t = new TextView(getActivity());
        t.setText(title);
        t.setTextSize(14);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.parseColor(tc));
        TextView b = new TextView(getActivity());
        b.setText(body);
        b.setTextSize(13);
        b.setTextColor(Color.parseColor("#1f2328"));
        b.setPadding(0, 6, 0, 0);
        c.addView(t); c.addView(b);
        return c;
    }

    private long ipToInt(String ip) {
        String[] p = ip.split("\\.");
        long r = 0;
        for (int i = 0; i < 4; i++) r = (r << 8) + Long.parseLong(p[i].trim());
        return r & 0xFFFFFFFFL;
    }

    private String intToIp(long ip) {
        return ((ip >> 24) & 0xff) + "." + ((ip >> 16) & 0xff) + "." + ((ip >> 8) & 0xff) + "." + (ip & 0xff);
    }
}

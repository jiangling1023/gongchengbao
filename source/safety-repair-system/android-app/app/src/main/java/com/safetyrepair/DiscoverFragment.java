package com.safetyrepair;

import android.app.Fragment;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;
import org.json.JSONArray;
import org.json.JSONObject;

/** 设备发现：ONVIF 通用查询 + 品牌端口子网扫描（摄像头/门禁）。 */
public class DiscoverFragment extends Fragment {
    private LinearLayout resultList;
    private TextView info;
    private Spinner sysSpin, brandSpin;
    private DBHelper dbh;
    private SQLiteDatabase db;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        dbh = new DBHelper(getActivity());
        db = dbh.getWritableDatabase();
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        TextView red = new TextView(getActivity());
        red.setText("⚠ 合规红线：仅在授权管理网络内操作；不得为发现而破坏安防网隔离；远程开门/权限/重启需授权审计。");
        red.setTextColor(Color.parseColor("#d1242f"));
        red.setTextSize(12);
        red.setBackgroundColor(Color.parseColor("#fff3f3"));
        red.setPadding(12, 10, 12, 10);
        root.addView(red);

        LinearLayout sel = new LinearLayout(getActivity());
        sel.setOrientation(LinearLayout.HORIZONTAL);
        sysSpin = new Spinner(getActivity());
        sysSpin.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, new String[]{"摄像头", "门禁"}));
        brandSpin = new Spinner(getActivity());
        sel.addView(wrap(sysSpin, 1));
        sel.addView(wrap(brandSpin, 1));
        root.addView(sel);

        LinearLayout btns = new LinearLayout(getActivity());
        btns.setOrientation(LinearLayout.HORIZONTAL);
        Button scan = new Button(getActivity());
        scan.setText("🔍 开始扫描(ONVIF+端口)");
        scan.setTextColor(Color.WHITE);
        scan.setBackgroundColor(Color.parseColor("#1f6feb"));
        scan.setOnClickListener(v -> doScan());
        Button bscan = new Button(getActivity());
        bscan.setText("🏷 品牌端口扫描");
        bscan.setTextColor(Color.WHITE);
        bscan.setBackgroundColor(Color.parseColor("#1565c0"));
        bscan.setOnClickListener(v -> doBrandScan());
        btns.addView(wrap(scan, 1));
        btns.addView(wrap(bscan, 1));
        root.addView(btns);

        info = new TextView(getActivity());
        info.setText("选择系统后扫描：摄像头走 ONVIF 通用发现；门禁走品牌端口探测（ONVIF-AC 需 SDK 对接）。");
        info.setTextSize(12);
        info.setTextColor(Color.parseColor("#6b7280"));
        info.setPadding(0, 10, 0, 6);
        root.addView(info);

        resultList = new LinearLayout(getActivity());
        resultList.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultList);

        sv.addView(root);
        updateBrand("摄像头");
        sysSpin.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            public void onItemSelected(android.widget.AdapterView<?> a, View v, int i, long id) { updateBrand(i == 0 ? "摄像头" : "门禁"); }
            public void onNothingSelected(android.widget.AdapterView<?> a) { }
        });
        return sv;
    }

    private void updateBrand(String sys) {
        String[][] list = sys.equals("摄像头") ? ContentData.CAMERA_BRANDS : ContentData.DOOR_BRANDS;
        String[] names = new String[list.length + 1];
        names[0] = "全部品牌(通用端口)";
        for (int i = 0; i < list.length; i++) names[i + 1] = list[i][0];
        brandSpin.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, names));
    }

    private LinearLayout wrap(View v, float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, w);
        p.setMargins(0, 0, 8, 0);
        v.setLayoutParams(p);
        LinearLayout wrap = new LinearLayout(getActivity());
        wrap.addView(v);
        wrap.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, w));
        return wrap;
    }

    private void doScan() {
        info.setText("扫描中…（ONVIF 多播 + TCP 端口探测，约 6 秒）");
        resultList.removeAllViews();
        new Thread(() -> {
            String json = Discovery.discoverAll(6000);
            final String j = json;
            if (getActivity() != null) getActivity().runOnUiThread(() -> renderDevices(j));
        }).start();
    }

    private void doBrandScan() {
        String sys = sysSpin.getSelectedItem().toString();
        String brand = brandSpin.getSelectedItem().toString();
        String[][] list = sys.equals("摄像头") ? ContentData.CAMERA_BRANDS : ContentData.DOOR_BRANDS;
        int[] ports = null;
        if (!"全部品牌(通用端口)".equals(brand)) {
            for (String[] b : list) if (b[0].equals(brand)) { ports = parsePorts(b[1]); break; }
        }
        if (ports == null) ports = new int[]{80, 554, 8000, 8080, 60000, 37777, 34567};
        info.setText("品牌端口扫描中… 端口：" + java.util.Arrays.toString(ports));
        resultList.removeAllViews();
        final int[] fp = ports;
        new Thread(() -> {
            String json = Discovery.subnetScan(fp, 6000);
            final String j = json;
            if (getActivity() != null) getActivity().runOnUiThread(() -> renderIpList(j, sys));
        }).start();
    }

    private int[] parsePorts(String s) {
        String[] ps = s.split("/");
        int[] r = new int[ps.length];
        for (int i = 0; i < ps.length; i++) r[i] = Integer.parseInt(ps[i].trim());
        return r;
    }

    private void renderDevices(String json) {
        resultList.removeAllViews();
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) { empty("未发现设备（当前无 WiFi/不在局域网，或设备未开启 ONVIF/端口）"); return; }
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String ip = o.optString("ip");
                String mfr = o.optString("manufacturer", "-");
                String model = o.optString("model", "-");
                String src = o.optString("source", "");
                String ac = o.has("acSupport") ? (" ｜ AC:" + o.optBoolean("acSupport")) : "";
                addCard(ip, mfr + " " + model, "来源:" + src + ac, ip);
            }
        } catch (Exception e) { empty("解析失败：" + e.getMessage()); }
    }

    private void renderIpList(String json, String sys) {
        resultList.removeAllViews();
        try {
            JSONArray arr = new JSONArray(json);
            if (arr.length() == 0) { empty("未扫描到开放端口设备"); return; }
            for (int i = 0; i < arr.length(); i++) addCard(arr.getString(i), "端口存活", "品牌端口子网探测", arr.getString(i));
        } catch (Exception e) { empty("解析失败：" + e.getMessage()); }
    }

    private void addCard(String ip, String title, String sub, String devIp) {
        final String sys = sysSpin.getSelectedItem().toString();
        final String fip = devIp;
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(14, 12, 14, 12);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cp);
        TextView t1 = new TextView(getActivity());
        t1.setText(title + (fip != null ? "  ｜ " + fip : ""));
        t1.setTextSize(15);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setTextColor(Color.parseColor("#1f2328"));
        TextView t2 = new TextView(getActivity());
        t2.setText(sub);
        t2.setTextSize(13);
        t2.setTextColor(Color.parseColor("#6b7280"));
        t2.setPadding(0, 4, 0, 0);
        Button in = new Button(getActivity());
        in.setText("入库");
        in.setTextSize(13);
        in.setBackgroundColor(Color.parseColor("#1a7f37"));
        in.setTextColor(Color.WHITE);
        in.setOnClickListener(v -> {
            String type = "摄像头".equals(sys) ? "camera" : "door";
            db.execSQL("INSERT INTO devices(type,brand,model,ip,status,location,note,created) VALUES(?,?,?,?,?,?,?,date('now'))",
                    new Object[]{type, "发现", "-", fip, "unknown", "待核", "设备发现自动入库", "now"});
            in.setText("已入库");
            in.setEnabled(false);
        });
        card.addView(t1); card.addView(t2); card.addView(in);
        resultList.addView(card);
    }

    private void empty(String msg) {
        TextView e = new TextView(getActivity());
        e.setText(msg);
        e.setTextColor(Color.parseColor("#6b7280"));
        e.setPadding(0, 20, 0, 20);
        resultList.addView(e);
    }
}

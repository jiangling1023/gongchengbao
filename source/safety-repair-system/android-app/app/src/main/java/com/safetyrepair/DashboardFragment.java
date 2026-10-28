package com.safetyrepair;

import android.app.Fragment;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 仪表盘：设备/工单统计 + 最近工单 + 故障设备红色高亮。
 */
public class DashboardFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        DBHelper dbh = new DBHelper(getActivity());
        SQLiteDatabase db = dbh.getReadableDatabase();

        int total = cnt(db, "devices", null);
        int online = cnt(db, "devices", "status='online'");
        int offline = cnt(db, "devices", "status='offline'");
        int unknown = total - online - offline;
        int tkOpen = cnt(db, "tickets", "status='processing'");
        int tkDone = cnt(db, "tickets", "status='closed'");

        // 故障设备：存在 processing 工单且已关联 device_id
        Set<Long> faultIds = dbh.getFaultDeviceIds();
        int faultCount = faultIds.size();

        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(statCard("设备", total, "#1f6feb"));
        row.addView(statCard("在线", online, "#1a7f37"));
        root.addView(row);
        LinearLayout row2 = new LinearLayout(getActivity());
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(statCard("离线", offline, "#d1242f"));
        row2.addView(statCard("未知", unknown, "#bc4c00"));
        root.addView(row2);
        LinearLayout row3 = new LinearLayout(getActivity());
        row3.setOrientation(LinearLayout.HORIZONTAL);
        row3.addView(statCard("处理中工单", tkOpen, "#bc4c00"));
        row3.addView(statCard("已结工单", tkDone, "#1a7f37"));
        root.addView(row3);
        LinearLayout row4 = new LinearLayout(getActivity());
        row4.setOrientation(LinearLayout.HORIZONTAL);
        row4.addView(statCard("故障设备", faultCount, "#d1242f"));
        root.addView(row4);

        // 故障设备清单（红色高亮）
        if (faultCount > 0) {
            TextView h = new TextView(getActivity());
            h.setText("故障设备（处理中）");
            h.setTextSize(16);
            h.setTypeface(null, Typeface.BOLD);
            h.setTextColor(Color.parseColor("#d1242f"));
            h.setPadding(0, 14, 0, 8);
            root.addView(h);

            Cursor cur = null;
            try {
                cur = db.rawQuery("SELECT code,brand,model,location FROM devices"
                        + " WHERE id IN (SELECT device_id FROM tickets WHERE status='processing' AND device_id>0)"
                        + " ORDER BY id DESC", null);
                while (cur != null && cur.moveToNext()) {
                    String code = nv(cur, 0);
                    String brand = nv(cur, 1);
                    String model = nv(cur, 2);
                    String loc = nv(cur, 3);
                    root.addView(faultItem(code + " " + brand + " " + model + "  ·  " + loc));
                }
            } catch (Exception e) {
                // 忽略
            } finally {
                if (cur != null) {
                    try {
                        cur.close();
                    } catch (Exception ignore) {
                        // 忽略
                    }
                }
            }
        }

        TextView h = new TextView(getActivity());
        h.setText("最近工单");
        h.setTextSize(16);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(Color.parseColor("#1f2328"));
        h.setPadding(0, 14, 0, 8);
        root.addView(h);

        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT code,device,title,status FROM tickets ORDER BY id DESC LIMIT 8", null);
            while (cur != null && cur.moveToNext()) {
                String code = nv(cur, 0);
                String dev = nv(cur, 1);
                String title = nv(cur, 2);
                String st = nv(cur, 3);
                LinearLayout item = new LinearLayout(getActivity());
                item.setOrientation(LinearLayout.VERTICAL);
                item.setBackgroundColor(Color.WHITE);
                item.setPadding(14, 10, 14, 10);
                android.widget.LinearLayout.LayoutParams ip =
                        new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                ip.setMargins(0, 0, 0, 8);
                item.setLayoutParams(ip);
                TextView t = new TextView(getActivity());
                t.setText(title + "  ·  " + dev);
                t.setTextSize(14);
                t.setTextColor(Color.parseColor("#1f2328"));
                TextView stt = new TextView(getActivity());
                stt.setText(code + "  ［" + ("closed".equals(st) ? "已结" : "处理中") + "］");
                stt.setTextSize(12);
                stt.setTextColor("closed".equals(st) ? Color.parseColor("#1a7f37") : Color.parseColor("#bc4c00"));
                stt.setPadding(0, 4, 0, 0);
                item.addView(t);
                item.addView(stt);
                root.addView(item);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            if (cur != null) {
                try {
                    cur.close();
                } catch (Exception ignore) {
                    // 忽略
                }
            }
        }
        // 缺陷设备地图（轻量物理分布可视化，非真实 GIS）
        TextView mh = new TextView(getActivity());
        mh.setText("缺陷设备地图");
        mh.setTextSize(16);
        mh.setTypeface(null, Typeface.BOLD);
        mh.setTextColor(Color.parseColor("#1f2328"));
        mh.setPadding(0, 14, 0, 8);
        root.addView(mh);

        DeviceMapView map = new DeviceMapView(getActivity());
        LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        mp.setMargins(0, 0, 0, 12);
        map.setLayoutParams(mp);
        root.addView(map);

        List<DeviceMapView.Item> mapItems = new ArrayList<>();
        Cursor mc = null;
        try {
            mc = db.rawQuery("SELECT code,location,status,id FROM devices", null);
            while (mc != null && mc.moveToNext()) {
                String code = nv(mc, 0);
                String loc = nv(mc, 1);
                String st = nv(mc, 2);
                long did = mc.getLong(3);
                boolean faulty = faultIds.contains(did);
                mapItems.add(new DeviceMapView.Item(code, loc, st, faulty));
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            if (mc != null) {
                try {
                    mc.close();
                } catch (Exception ignore) {
                    // 忽略
                }
            }
        }
        map.setItems(mapItems);

        db.close();

        sv.addView(root);
        return sv;
    }

    private int cnt(SQLiteDatabase db, String table, String where) {
        Cursor c = null;
        int n = 0;
        try {
            c = db.rawQuery("SELECT count(*) FROM " + table + (where != null ? " WHERE " + where : ""), null);
            if (c.moveToFirst()) {
                n = c.getInt(0);
            }
        } catch (Exception e) {
            n = 0;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignore) {
                    // 忽略
                }
            }
        }
        return n;
    }

    private LinearLayout statCard(String label, int num, String color) {
        LinearLayout card = new LinearLayout(getActivity());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(14, 14, 14, 14);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, 0, 8, 8);
        card.setLayoutParams(p);
        TextView n = new TextView(getActivity());
        n.setText(String.valueOf(num));
        n.setTextSize(26);
        n.setTypeface(null, Typeface.BOLD);
        n.setTextColor(Color.parseColor(color));
        TextView l = new TextView(getActivity());
        l.setText(label);
        l.setTextSize(13);
        l.setTextColor(Color.parseColor("#6b7280"));
        l.setPadding(0, 4, 0, 0);
        card.addView(n);
        card.addView(l);
        return card;
    }

    private LinearLayout faultItem(String text) {
        LinearLayout item = new LinearLayout(getActivity());
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackgroundColor(Color.parseColor("#fff5f5"));
        item.setPadding(14, 10, 14, 10);
        android.widget.LinearLayout.LayoutParams ip =
                new android.widget.LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, 0, 0, 8);
        item.setLayoutParams(ip);

        View dot = new View(getActivity());
        dot.setLayoutParams(new LayoutParams(10, 10));
        dot.setBackgroundColor(Color.parseColor("#d1242f"));

        TextView t = new TextView(getActivity());
        t.setText(text);
        t.setTextSize(14);
        t.setTextColor(Color.parseColor("#d1242f"));
        t.setPadding(10, 0, 0, 0);

        item.addView(dot);
        item.addView(t);
        return item;
    }

    private static String nv(Cursor c, int i) {
        try {
            String v = c.getString(i);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }
}

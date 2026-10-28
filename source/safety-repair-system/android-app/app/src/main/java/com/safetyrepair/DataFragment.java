package com.safetyrepair;

import android.app.AlertDialog;
import android.app.Fragment;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;
import android.content.Intent;
import android.os.Environment;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONObject;

/** 数据管理：台账/工单/巡检 导出与导入 JSON（应用私有目录，免存储权限）。 */
public class DataFragment extends Fragment {
    private DBHelper dbh;
    private SQLiteDatabase db;
    private TextView status;
    private File backupFile;
    private static final int REQ_EXPORT = 3001;

    /**
     * 导出完整列定义（超集格式，与网页版 data.js#exportJSON 对齐）。
     * 包含 id / code / parent_id / device_id 等关联字段，保证「安卓导出 → 网页导入」
     * 不丢失上下级(parent_id)与工单关联(device_id)。
     */
    private static final String[] DEV_COLS = {
            "id", "code", "parent_id", "type", "brand", "model", "ip", "status",
            "location", "building", "floor", "note", "created"};
    private static final String[] TICKET_COLS = {
            "id", "code", "device_id", "device", "title", "ftype", "level", "status",
            "desc", "solution", "created", "closed"};
    private static final String[] INSPECTION_COLS = {
            "id", "title", "date", "result", "note"};

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        dbh = new DBHelper(getActivity());
        db = dbh.getWritableDatabase();
        File dir = new File(getActivity().getExternalFilesDir(null), "backup");
        dir.mkdirs();
        backupFile = new File(dir, "safety_repair_backup.json");

        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12,12, 12);

        TextView h = new TextView(getActivity());
        h.setText("数据管理");
        h.setTextSize(18);
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        h.setTextColor(Color.parseColor("#1f2328"));
        root.addView(h);

        LinearLayout row = new LinearLayout(getActivity());
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button exp = new Button(getActivity());
        exp.setText("导出 JSON");
        exp.setTextColor(Color.WHITE);
        exp.setBackgroundColor(Color.parseColor("#1f6feb"));
        Button imp = new Button(getActivity());
        imp.setText("导入 JSON");
        imp.setTextColor(Color.WHITE);
        imp.setBackgroundColor(Color.parseColor("#1a7f37"));
        row.addView(exp);
        row.addView(imp);
        root.addView(row);

        status = new TextView(getActivity());
        status.setText("备份路径：\n" + backupFile.getAbsolutePath());
        status.setTextSize(12);
        status.setTextColor(Color.parseColor("#6b7280"));
        status.setPadding(0, 10, 0, 0);
        root.addView(status);

        // 报表导出（Excel / PDF，SAF 自选保存位置）
        TextView eh = new TextView(getActivity());
        eh.setText("报表导出");
        eh.setTextSize(15);
        eh.setTypeface(null, android.graphics.Typeface.BOLD);
        eh.setTextColor(Color.parseColor("#1f2328"));
        eh.setPadding(0, 14, 0, 6);
        root.addView(eh);

        LinearLayout exRow1 = new LinearLayout(getActivity());
        exRow1.setOrientation(LinearLayout.HORIZONTAL);
        Button exDevX = new Button(getActivity());
        exDevX.setText("设备 Excel");
        exDevX.setTextColor(Color.WHITE);
        exDevX.setBackgroundColor(Color.parseColor("#1f6feb"));
        exDevX.setOnClickListener(v -> doExport("excel", "devices"));
        Button exDevP = new Button(getActivity());
        exDevP.setText("设备 PDF");
        exDevP.setTextColor(Color.WHITE);
        exDevP.setBackgroundColor(Color.parseColor("#1f6feb"));
        exDevP.setOnClickListener(v -> doExport("pdf", "devices"));
        exRow1.addView(exDevX);
        exRow1.addView(exDevP);
        root.addView(exRow1);

        LinearLayout exRow2 = new LinearLayout(getActivity());
        exRow2.setOrientation(LinearLayout.HORIZONTAL);
        Button exTkX = new Button(getActivity());
        exTkX.setText("工单 Excel");
        exTkX.setTextColor(Color.WHITE);
        exTkX.setBackgroundColor(Color.parseColor("#1a7f37"));
        exTkX.setOnClickListener(v -> doExport("excel", "tickets"));
        Button exTkP = new Button(getActivity());
        exTkP.setText("工单 PDF");
        exTkP.setTextColor(Color.WHITE);
        exTkP.setBackgroundColor(Color.parseColor("#1a7f37"));
        exTkP.setOnClickListener(v -> doExport("pdf", "tickets"));
        exRow2.addView(exTkX);
        exRow2.addView(exTkP);
        root.addView(exRow2);

        sv.addView(root);

        exp.setOnClickListener(v -> doExportJson());
        imp.setOnClickListener(v -> doImport());
        return sv;
    }

    /** 导出 JSON 备份（应用私有目录）。 */
    private void doExportJson() {
        doExport();
    }

    /** 报表导出：Excel / PDF，按设备 or 工单。 */
    private void doExport(String format, String which) {
        String[] headers;
        List<String[]> rows;
        String title;
        if ("tickets".equals(which)) {
            headers = ReportData.ticketHeaders();
            rows = ReportData.ticketRows(db);
            title = "故障工单报表";
        } else {
            headers = ReportData.deviceHeaders();
            rows = ReportData.deviceRows(db, dbh);
            title = "设备台账报表";
        }
        Exporter.prepare(format, headers, rows);
        Intent intent = "pdf".equals(format)
                ? Exporter.buildPdfIntent(title + ".pdf")
                : Exporter.buildExcelIntent(title + ".xls");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, android.content.Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT) {
            Exporter.commit(getActivity(), resultCode, data);
        }
    }

    /**
     * 导出 JSON 备份（超集格式）。
     * 导出完整列：devices 含 id/code/parent_id/.../building/floor/created，
     * tickets 含 device_id，inspections 含 id/title/date/result/note，
     * 与网页版 data.js#exportJSON 字段完全一致，支持双向同步。
     */
    private void doExport() {
        try {
            JSONObject root = new JSONObject();
            root.put("devices", dumpTable("devices", DEV_COLS));
            root.put("tickets", dumpTable("tickets", TICKET_COLS));
            root.put("inspections", dumpTable("inspections", INSPECTION_COLS));
            FileWriter fw = new FileWriter(backupFile);
            fw.write(root.toString(2));
            fw.close();
            status.setText("导出成功：\n" + backupFile.getAbsolutePath() + "\n大小：" + backupFile.length() + " 字节");
        } catch (Exception e) {
            status.setText("导出失败：" + e.getMessage());
        }
    }

    /**
     * 按给定列名导出表为 JSON 数组。
     * 逐列以字符串形式读取（SQLite 数值列亦可安全转字符串），保证整数关联字段
     * (id / parent_id / device_id) 以字符串形式输出，网页端 Number() 可正确解析。
     */
    private JSONArray dumpTable(String table, String[] cols) {
        JSONArray arr = new JSONArray();
        Cursor cur = db.rawQuery("SELECT " + String.join(",", cols) + " FROM " + table, null);
        while (cur.moveToNext()) {
            JSONObject o = new JSONObject();
            for (int i = 0; i < cols.length; i++) {
                try {
                    String v = cur.isNull(i) ? "" : cur.getString(i);
                    o.put(cols[i], v == null ? "" : v);
                } catch (Exception ignore) {
                    // 单列读取异常忽略，保证其余列仍可导出
                }
            }
            arr.put(o);
        }
        cur.close();
        return arr;
    }

    /**
     * 导入 JSON 备份（超集兼容 upsert）。
     * 不再清空全表，改为按编号/关联去重合并：
     * - 设备：code 命中则更新；否则 type+ip 命中则更新；否则插入。
     * - 工单：code 命中则更新；否则插入。
     * - 巡检：title+date 命中则更新；否则插入。
     * 写入时按 PRAGMA table_info 动态匹配真实列，超集字段（parent_id/device_id/code 等）
     * 存在即写入对应列，不存在则留默认；未知键忽略，保证不崩且前向兼容。
     */
    private void doImport() {
        if (!backupFile.exists()) { status.setText("未找到备份文件，请先导出"); return; }
        try {
            BufferedReader br = new BufferedReader(new FileReader(backupFile));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();
            JSONObject root = new JSONObject(sb.toString());

            Set<String> devCols = getTableColumns("devices");
            Set<String> tkCols = getTableColumns("tickets");
            Set<String> insCols = getTableColumns("inspections");

            db.beginTransaction();
            try {
                importDevices(root.optJSONArray("devices"), devCols);
                importTickets(root.optJSONArray("tickets"), tkCols);
                importInspections(root.optJSONArray("inspections"), insCols);
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
            status.setText("导入成功：" + backupFile.getName());
            new AlertDialog.Builder(getActivity()).setTitle("导入完成")
                    .setMessage("已按编号/关联合并数据（双向同步）。").setPositiveButton("确定", null).show();
        } catch (Exception e) {
            status.setText("导入失败：" + e.getMessage());
        }
    }

    // -------------------------- 导入：设备 --------------------------

    private void importDevices(JSONArray arr, Set<String> cols) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Map<String, String> v = pickColumns(o, cols);
            String code = v.get("code");
            String type = v.get("type");
            String ip = v.get("ip");
            long existing = -1;
            if (!isEmpty(code)) existing = dbh.getDeviceIdByCode(code.trim());
            if (existing <= 0 && !isEmpty(type) && !isEmpty(ip)) {
                existing = findDeviceByTypeIp(type.trim(), ip.trim());
            }
            try {
                if (existing > 0) {
                    updateRow("devices", existing, v);
                } else {
                    insertRow("devices", v);
                }
            } catch (Exception ignore) {
                // 单条异常不阻断其余记录
            }
        }
    }

    private long findDeviceByTypeIp(String type, String ip) {
        long id = -1;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id FROM devices WHERE type=? AND ip=?",
                    new String[]{type, ip});
            if (c.moveToFirst()) id = c.getLong(0);
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return id;
    }

    // -------------------------- 导入：工单 --------------------------

    private void importTickets(JSONArray arr, Set<String> cols) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Map<String, String> v = pickColumns(o, cols);
            String code = v.get("code");
            long existing = -1;
            if (!isEmpty(code)) existing = findTicketByCode(code.trim());
            try {
                if (existing > 0) {
                    updateRow("tickets", existing, v);
                } else {
                    insertRow("tickets", v);
                }
            } catch (Exception ignore) {
                // 单条异常不阻断其余记录
            }
        }
    }

    private long findTicketByCode(String code) {
        long id = -1;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id FROM tickets WHERE code=?", new String[]{code});
            if (c.moveToFirst()) id = c.getLong(0);
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return id;
    }

    // -------------------------- 导入：巡检 --------------------------

    private void importInspections(JSONArray arr, Set<String> cols) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Map<String, String> v = pickColumns(o, cols);
            long existing = findInspectionByTitleDate(v.get("title"), v.get("date"));
            try {
                if (existing > 0) {
                    updateRow("inspections", existing, v);
                } else {
                    insertRow("inspections", v);
                }
            } catch (Exception ignore) {
                // 单条异常不阻断其余记录
            }
        }
    }

    private long findInspectionByTitleDate(String title, String date) {
        if (isEmpty(title) || isEmpty(date)) return -1;
        long id = -1;
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id FROM inspections WHERE title=? AND date=?",
                    new String[]{title, date});
            if (c.moveToFirst()) id = c.getLong(0);
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return id;
    }

    // -------------------------- 导入：写入辅助 --------------------------

    /**
     * 从 JSON 对象中按真实列集合挑选字段，超集字段若不在表中则忽略。
     * 保留 id（用于维护 parent_id / device_id 关联），空值统一为 ""。
     */
    private Map<String, String> pickColumns(JSONObject o, Set<String> cols) {
        Map<String, String> m = new HashMap<>();
        if (o == null || cols == null) return m;
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String key = it.next();
            if (key == null || !cols.contains(key)) continue;
            String v;
            try {
                v = o.isNull(key) ? "" : o.optString(key, "");
            } catch (Exception e) {
                v = "";
            }
            m.put(key, v == null ? "" : v);
        }
        return m;
    }

    /** 插入一行；若含有效 id 则保留（维护关联），否则交由 AUTOINCREMENT 分配。 */
    private void insertRow(String table, Map<String, String> values) {
        boolean hasId = values.containsKey("id") && !isEmpty(values.get("id"));
        List<String> cols = new ArrayList<>();
        List<String> phs = new ArrayList<>();
        List<String> vlist = new ArrayList<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if ("id".equals(e.getKey()) && !hasId) continue;
            cols.add(e.getKey());
            phs.add("?");
            vlist.add(e.getValue());
        }
        if (cols.isEmpty()) return;
        String sql = "INSERT INTO " + table + "(" + join(cols) + ") VALUES(" + join(phs) + ")";
        try {
            db.execSQL(sql, vlist.toArray());
        } catch (Exception e) {
            // 主键冲突（如本地已存在相同 id）：去掉 id 重试一次，确保不丢数据
            if (hasId) {
                retryInsertWithoutId(table, values);
            }
        }
    }

    private void retryInsertWithoutId(String table, Map<String, String> values) {
        List<String> cols = new ArrayList<>();
        List<String> vlist = new ArrayList<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if ("id".equals(e.getKey())) continue;
            cols.add(e.getKey());
            vlist.add(e.getValue());
        }
        if (cols.isEmpty()) return;
        try {
            db.execSQL("INSERT INTO " + table + "(" + join(cols) + ") VALUES(" + join(qmarks(cols.size())) + ")",
                    vlist.toArray());
        } catch (Exception ignore) {
            // 仍失败则放弃该条，不阻断其余
        }
    }

    /** 更新已有行（不改主键 id）。 */
    private void updateRow(String table, long id, Map<String, String> values) {
        List<String> sets = new ArrayList<>();
        List<String> vlist = new ArrayList<>();
        for (Map.Entry<String, String> e : values.entrySet()) {
            if ("id".equals(e.getKey())) continue;
            sets.add(e.getKey() + "=?");
            vlist.add(e.getValue());
        }
        if (sets.isEmpty()) return;
        vlist.add(String.valueOf(id));
        try {
            db.execSQL("UPDATE " + table + " SET " + join(sets) + " WHERE id=?", vlist.toArray());
        } catch (Exception ignore) {
            // 更新异常忽略，不阻断其余
        }
    }

    /** 获取表真实列名集合（用于动态匹配写入，兼容超集）。 */
    private Set<String> getTableColumns(String table) {
        Set<String> set = new HashSet<>();
        Cursor c = null;
        try {
            c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (c != null && c.moveToNext()) {
                String name = c.getString(1);
                if (name != null) set.add(name);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return set;
    }

    private static String join(List<String> list) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(list.get(i));
        }
        return sb.toString();
    }

    private static List<String> qmarks(int n) {
        List<String> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add("?");
        return l;
    }

    private static boolean isEmpty(String s) {
        return s == null || s.trim().isEmpty();
    }

    private static void closeQuietly(Cursor c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
                // 忽略
            }
        }
    }
}

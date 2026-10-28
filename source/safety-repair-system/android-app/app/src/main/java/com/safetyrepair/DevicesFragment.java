package com.safetyrepair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
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
import android.widget.Toast;

import android.view.ViewGroup.LayoutParams;
import android.widget.AdapterView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备台账：摄像头/门禁设备的增删改查（本地 SQLite）。
 *
 * <p>功能增强：
 * <ul>
 *   <li>防御性编码：Activity 非空、Cursor try/finally、NPE 兜底。</li>
 *   <li>「导入」(SAF 选 CSV/JSON) 与「下载模板」(SAF 写 CSV)。</li>
 *   <li>「拓扑/层级」视图：按 编号/父级 展示设备树。</li>
 *   <li>故障设备红色高亮（存在 processing 工单且已关联）。</li>
 *   <li>设备卡片「查看关联工单」跳转工单页按设备过滤。</li>
 * </ul>
 * 全程使用 SAF（Intent 选文件），不申请外部存储权限。
 */
public class DevicesFragment extends Fragment {
    private static final int REQ_IMPORT = 1001;
    private static final int REQ_TEMPLATE = 1002;

    private DBHelper dbh;
    private SQLiteDatabase db;
    private LinearLayout listContainer;
    private Spinner modeSpinner;
    private int viewMode = 0; // 0=列表 1=拓扑 2=分组
    private Set<String> collapsedGroups = new HashSet<>();
    private static final int REQ_EXPORT = 1003;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        Activity act = getActivity();
        if (act == null) {
            return new LinearLayout(c != null ? c.getContext() : null);
        }
        try {
            dbh = new DBHelper(act);
            db = dbh.getWritableDatabase();
        } catch (Exception e) {
            // DB 打开/迁移失败时降级为空库，读路径改用安全查询，避免闪退
            db = null;
            toast("数据库打开失败：" + e.getMessage());
        }

        ScrollView sv = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        // 标题行
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView h = new TextView(act);
        h.setText("设备台账");
        h.setTextSize(18);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(C(act, R.color.text));
        h.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button add = btn(act, "+ 添加", true, v -> form(null));
        top.addView(h);
        top.addView(add);
        root.addView(top);

        // 操作行：视图切换（列表/拓扑/分组）+ 导入 / 下载模板
        LinearLayout ops = new LinearLayout(act);
        ops.setOrientation(LinearLayout.HORIZONTAL);
        ops.setPadding(0, 8, 0, 4);
        modeSpinner = new Spinner(act);
        modeSpinner.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item,
                new String[]{"列表", "拓扑", "分组"}));
        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                viewMode = position;
                load();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // 保持当前选择
            }
        });
        Button imp = btn(act, "导入", false, v -> pickImportFile());
        Button tpl = btn(act, "下载模板", false, v -> createTemplateFile());
        ops.addView(modeSpinner);
        ops.addView(imp);
        ops.addView(tpl);
        root.addView(ops);

        // 导出行：Excel / PDF（SAF 自选保存位置）
        LinearLayout expRow = new LinearLayout(act);
        expRow.setOrientation(LinearLayout.HORIZONTAL);
        expRow.setPadding(0, 0, 0, 4);
        Button expXls = btn(act, "导出Excel", false, v -> doExport("excel"));
        Button expPdf = btn(act, "导出PDF", false, v -> doExport("pdf"));
        expRow.addView(expXls);
        expRow.addView(expPdf);
        root.addView(expRow);

        listContainer = new LinearLayout(act);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);
        sv.addView(root);

        load();
        return sv;
    }

    // -------------------------- 导出（Excel / PDF） --------------------------

    private void doExport(String format) {
        Activity act = getActivity();
        if (act == null || db == null) {
            return;
        }
        String[] headers = ReportData.deviceHeaders();
        List<String[]> rows = ReportData.deviceRows(db, dbh);
        Exporter.prepare(format, headers, rows);
        Intent intent = "pdf".equals(format)
                ? Exporter.buildPdfIntent("设备台账报表.pdf")
                : Exporter.buildExcelIntent("设备台账报表.xls");
        startActivityForResult(intent, REQ_EXPORT);
    }

    // -------------------------- 数据加载 --------------------------

    private void load() {
        if (listContainer == null || dbh == null) {
            return;
        }
        listContainer.removeAllViews();
        Set<Long> faultIds = safeFaultIds();
        if (viewMode == 1) {
            renderTopology(listContainer, faultIds);
        } else if (viewMode == 2) {
            renderGrouped(listContainer, faultIds);
        } else {
            renderList(listContainer, faultIds);
        }
    }

    /** 列表视图：沿用设备卡片（按 id 倒序）。读路径改用 DBHelper 安全查询，杜绝裸 rawQuery 抛异常。 */
    private void renderList(LinearLayout container, Set<Long> faultIds) {
        if (dbh == null) {
            return;
        }
        List<String[]> rows = dbh.queryAllDevices();
        for (String[] r : rows) {
            if (r == null || r.length < 9) {
                continue;
            }
            try {
                final long id = Long.parseLong(r[0]);
                String code = r[1];
                String type = r[2];
                String brand = r[3];
                String model = r[4];
                String ip = r[5];
                String status = r[6];
                String loc = r[7];
                boolean faulty = faultIds.contains(id);
                container.addView(deviceCard(id, code, type, brand, model, ip, status, loc, faulty));
            } catch (Exception e) {
                // 跳过异常行，不中断整页渲染
            }
        }
    }

    /** 设备卡片：左侧状态条 + 故障红色高亮 + 操作按钮。 */
    private LinearLayout deviceCard(final long id, String code, String type, String brand,
                                    String model, String ip, String status, String loc, boolean faulty) {
        Activity act = getActivity();
        if (act == null) {
            return new LinearLayout(getActivitySafeContext());
        }
        boolean online = "online".equals(status);

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(faulty ? C(act, R.color.fault_bg) : C(act, R.color.card));
        int pad = dp(act, 10);
        row.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rp.setMargins(0, 0, 0, 10);
        row.setLayoutParams(rp);

        // 左侧状态/故障色条
        View bar = new View(act);
        int barW = dp(act, 5);
        int barH = LayoutParams.MATCH_PARENT;
        bar.setLayoutParams(new LinearLayout.LayoutParams(barW, barH));
        bar.setBackgroundColor(faulty ? C(act, R.color.danger) : (online ? C(act, R.color.ok) : C(act, R.color.muted)));
        row.addView(bar);

        LinearLayout content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        content.setPadding(dp(act, 10), 0, 0, 0);

        TextView t1 = new TextView(act);
        String typeLabel = "camera".equals(type) ? "摄像头 " : "门禁 ";
        String title = typeLabel + brand + " " + model;
        if (code != null && !code.isEmpty()) {
            title = "[" + code + "] " + title;
        }
        t1.setText(title);
        t1.setTextSize(15);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setTextColor(C(act, R.color.text));
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t1.setSingleLine(true);

        TextView t2 = new TextView(act);
        String statusText = faulty ? "故障/损坏" : (status == null ? "" : status);
        t2.setText("IP: " + nz(ip) + "  |  " + nz(loc) + "  |  " + statusText);
        t2.setTextSize(13);
        t2.setTextColor(faulty ? C(act, R.color.danger) : C(act, R.color.muted));
        t2.setPadding(0, 4, 0, 0);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, 8, 0, 0);
        Button edit = btn(act, "编辑", false, v -> form(id));
        Button del = btn(act, "删除", false, v -> {
            if (db != null) {
                try {
                    db.delete("devices", "id=?", new String[]{String.valueOf(id)});
                } catch (Exception e) {
                    toast("删除失败：" + e.getMessage());
                }
            } else {
                toast("数据库不可用，无法删除");
            }
            load();
        });
        Button tickets = btn(act, "查看关联工单", false, v -> openTickets(id));
        btns.addView(edit);
        btns.addView(del);
        btns.addView(tickets);

        content.addView(t1);
        content.addView(t2);
        content.addView(btns);
        row.addView(content);
        return row;
    }

    /** 跳转到工单页，按设备过滤。 */
    private void openTickets(long deviceId) {
        Activity act = getActivity();
        int cnt = dbh != null ? dbh.countTicketsForDevice(deviceId) : 0;
        if (cnt <= 0) {
            toast("该设备暂无关联工单");
            return;
        }
        if (act instanceof MainActivity) {
            ((MainActivity) act).openTicketsForDevice(deviceId);
        } else {
            toast("请到「故障工单」查看");
        }
    }

    // -------------------------- 拓扑/层级视图 --------------------------

    private static class DevNode {
        long id;
        String code = "";
        String type = "";
        String brand = "";
        String model = "";
        String status = "";
        long parentId = 0;
        List<DevNode> children = new ArrayList<>();
    }

    private void renderTopology(LinearLayout container, Set<Long> faultIds) {
        Activity act = getActivity();
        if (act == null || dbh == null) {
            return;
        }
        Map<Long, DevNode> map = new HashMap<>();
        List<DevNode> roots = new ArrayList<>();
        List<String[]> rows = dbh.queryAllDevices();
        for (String[] r : rows) {
            if (r == null || r.length < 9) {
                continue;
            }
            try {
                DevNode n = new DevNode();
                n.id = Long.parseLong(r[0]);
                n.code = r[1];
                n.type = r[2];
                n.brand = r[3];
                n.model = r[4];
                n.status = r[6];
                n.parentId = Long.parseLong(r[8]);
                map.put(n.id, n);
            } catch (Exception e) {
                // 跳过异常行
            }
        }

        Set<Long> hasParent = new HashSet<>();
        for (DevNode n : map.values()) {
            if (n.parentId > 0 && map.containsKey(n.parentId)) {
                map.get(n.parentId).children.add(n);
                hasParent.add(n.id);
            }
        }
        for (DevNode n : map.values()) {
            if (!hasParent.contains(n.id)) {
                roots.add(n);
            }
        }

        if (roots.isEmpty() && !map.isEmpty()) {
            roots.addAll(map.values());
        }

        for (DevNode r : roots) {
            renderNode(container, r, 0, faultIds);
        }
    }

    private void renderNode(LinearLayout container, DevNode n, int depth, Set<Long> faultIds) {
        Activity act = getActivity();
        if (act == null) {
            return;
        }
        boolean faulty = faultIds.contains(n.id);
        boolean online = "online".equals(n.status);

        LinearLayout item = new LinearLayout(act);
        item.setOrientation(LinearLayout.HORIZONTAL);
        item.setBackgroundColor(faulty ? C(act, R.color.fault_bg) : C(act, R.color.card));
        int pad = dp(act, 10);
        item.setPadding(pad + depth * dp(act, 22), pad, pad, pad);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, 0, 0, 8);
        item.setLayoutParams(ip);

        TextView txt = new TextView(act);
        StringBuilder sb = new StringBuilder();
        if (depth > 0) {
            sb.append("  ");
            for (int i = 1; i < depth; i++) {
                sb.append("  ");
            }
            sb.append("└─ ");
        }
        String typeLabel = "camera".equals(n.type) ? "摄像头" : "门禁";
        sb.append(n.code).append(" ").append(typeLabel).append(" ").append(n.brand).append(" ").append(n.model);
        if (faulty) {
            sb.append("  [故障]");
        } else {
            sb.append("  [").append(n.status).append("]");
        }
        txt.setText(sb.toString());
        txt.setTextSize(14);
        txt.setTextColor(faulty ? C(act, R.color.danger) : (online ? C(act, R.color.text) : C(act, R.color.muted)));
        txt.setEllipsize(android.text.TextUtils.TruncateAt.END);
        item.addView(txt);
        container.addView(item);

        // 子节点按 id 排序后递归
        List<DevNode> kids = n.children;
        kids.sort((a, b) -> Long.compare(a.id, b.id));
        for (DevNode k : kids) {
            renderNode(container, k, depth + 1, faultIds);
        }
    }

    // -------------------------- 分组视图 --------------------------

    private static class DevRow {
        long id;
        String code = "";
        String type = "";
        String brand = "";
        String model = "";
        String ip = "";
        String status = "";
        String loc = "";
        boolean faulty = false;
    }

    /** 分组视图：按楼栋/楼层分组，可折叠标题（含故障设备的组标红）。 */
    private void renderGrouped(LinearLayout container, Set<Long> faultIds) {
        Activity act = getActivity();
        if (act == null || dbh == null) {
            return;
        }
        Map<String, List<DevRow>> groups = new LinkedHashMap<>();
        List<String[]> rows = dbh.queryAllDevices();
        for (String[] r : rows) {
            if (r == null || r.length < 9) {
                continue;
            }
            try {
                final long id = Long.parseLong(r[0]);
                DevRow dr = new DevRow();
                dr.id = id;
                dr.code = r[1];
                dr.type = r[2];
                dr.brand = r[3];
                dr.model = r[4];
                dr.ip = r[5];
                dr.status = r[6];
                dr.loc = r[7];
                dr.faulty = faultIds.contains(id);
                String key = LocationUtil.groupKey(dr.loc);
                List<DevRow> list = groups.get(key);
                if (list == null) {
                    list = new ArrayList<>();
                    groups.put(key, list);
                }
                list.add(dr);
            } catch (Exception e) {
                // 跳过异常行
            }
        }

        List<Map.Entry<String, List<DevRow>>> entries = new ArrayList<>(groups.entrySet());
        Collections.sort(entries, new Comparator<Map.Entry<String, List<DevRow>>>() {
            @Override
            public int compare(Map.Entry<String, List<DevRow>> a, Map.Entry<String, List<DevRow>> b) {
                int oa = LocationUtil.floorOrder(a.getKey());
                int ob = LocationUtil.floorOrder(b.getKey());
                if (oa != ob) {
                    return Integer.compare(oa, ob);
                }
                return a.getKey().compareTo(b.getKey());
            }
        });

        for (Map.Entry<String, List<DevRow>> e : entries) {
            final String key = e.getKey();
            List<DevRow> list = e.getValue();
            boolean hasFault = false;
            for (DevRow r : list) {
                if (r.faulty) {
                    hasFault = true;
                    break;
                }
            }

            TextView header = new TextView(act);
            String title = key + "（" + list.size() + "台）" + (hasFault ? "  · 含故障" : "");
            header.setText(title);
            header.setTextSize(15);
            header.setTypeface(null, Typeface.BOLD);
            header.setTextColor(hasFault ? C(act, R.color.danger) : C(act, R.color.text));
            header.setBackgroundColor(hasFault ? C(act, R.color.fault_bg) : C(act, R.color.chip));
            header.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            hp.setMargins(0, 0, 0, dp(act, 6));
            header.setLayoutParams(hp);
            header.setOnClickListener(v -> {
                if (collapsedGroups.contains(key)) {
                    collapsedGroups.remove(key);
                } else {
                    collapsedGroups.add(key);
                }
                load();
            });
            container.addView(header);

            if (!collapsedGroups.contains(key)) {
                for (DevRow r : list) {
                    container.addView(deviceCard(r.id, r.code, r.type, r.brand, r.model,
                            r.ip, r.status, r.loc, r.faulty));
                }
            }
        }
    }

    // -------------------------- 新增/编辑表单 --------------------------

    private void form(final Long id) {
        Activity act = getActivity();
        if (act == null || db == null) {
            return;
        }
        Cursor ex = null;
        String[] vals = new String[9]; // type,brand,model,ip,status,location,note,code,parent_id
        if (id != null) {
            try {
                ex = db.rawQuery("SELECT type,brand,model,ip,status,location,note,code,parent_id"
                        + " FROM devices WHERE id=?", new String[]{String.valueOf(id)});
                if (ex.moveToFirst()) {
                    for (int i = 0; i < 9; i++) {
                        vals[i] = ex.getString(i);
                    }
                }
            } catch (Exception e) {
                // 忽略
            } finally {
                if (ex != null) {
                    try {
                        ex.close();
                    } catch (Exception ignore) {
                        // 忽略
                    }
                }
            }
        }
        ScrollView fs = new ScrollView(act);
        LinearLayout fl = new LinearLayout(act);
        fl.setOrientation(LinearLayout.VERTICAL);
        fl.setPadding(20, 20, 20, 20);

        final EditText codeEt = e(act, "编号(唯一,如S001)", vals[7]);
        final Spinner type = new Spinner(act);
        type.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item, new String[]{"camera", "door"}));
        if (vals[0] != null) {
            type.setSelection(vals[0].equals("door") ? 1 : 0);
        }
        final EditText brand = e(act, "品牌", vals[1]);
        final EditText model = e(act, "型号", vals[2]);
        final EditText ip = e(act, "IP", vals[3]);
        final Spinner status = new Spinner(act);
        String[] sts = {"online", "offline", "unknown"};
        status.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item, sts));
        if (vals[4] != null) {
            for (int i = 0; i < 3; i++) {
                if (vals[4].equals(sts[i])) {
                    status.setSelection(i);
                }
            }
        }
        final EditText loc = e(act, "位置", vals[5]);
        final EditText note = e(act, "备注", vals[6]);
        final EditText parent = e(act, "父级编号(可为空)", vals[8] != null && vals[8].equals("0") ? "" : vals[8]);

        fl.addView(label(act, "编号")); fl.addView(codeEt);
        fl.addView(label(act, "类型")); fl.addView(type);
        fl.addView(label(act, "品牌")); fl.addView(brand);
        fl.addView(label(act, "型号")); fl.addView(model);
        fl.addView(label(act, "IP")); fl.addView(ip);
        fl.addView(label(act, "状态")); fl.addView(status);
        fl.addView(label(act, "位置")); fl.addView(loc);
        fl.addView(label(act, "备注")); fl.addView(note);
        fl.addView(label(act, "父级编号")); fl.addView(parent);
        fs.addView(fl);

        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle(id == null ? "新增设备" : "编辑设备");
        b.setView(fs);
        b.setPositiveButton("保存", (d, w) -> {
            if (db == null) {
                return;
            }
            String t = type.getSelectedItem().toString();
            String st = status.getSelectedItem().toString();
            String codeVal = codeEt.getText() == null ? "" : codeEt.getText().toString().trim();
            String parentVal = parent.getText() == null ? "" : parent.getText().toString().trim();
            if (codeVal.isEmpty()) {
                toast("编号不能为空");
                return;
            }
            if (id == null) {
                long pid = 0;
                if (!parentVal.isEmpty()) {
                    long p = dbh.getDeviceIdByCode(parentVal);
                    if (p > 0) {
                        pid = p;
                    }
                }
                db.execSQL("INSERT INTO devices(code,parent_id,type,brand,model,ip,status,location,note,created)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,date('now'))",
                        new Object[]{codeVal, pid, t, brand.getText().toString(), model.getText().toString(),
                                ip.getText().toString(), st, loc.getText().toString(), note.getText().toString()});
            } else {
                long pid = 0;
                if (!parentVal.isEmpty()) {
                    long p = dbh.getDeviceIdByCode(parentVal);
                    if (p > 0 && p != id) {
                        pid = p;
                    }
                }
                db.execSQL("UPDATE devices SET code=?,parent_id=?,type=?,brand=?,model=?,ip=?,status=?,location=?,note=?"
                                + " WHERE id=?",
                        new Object[]{codeVal, pid, t, brand.getText().toString(), model.getText().toString(),
                                ip.getText().toString(), st, loc.getText().toString(), note.getText().toString(), id});
            }
            load();
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    // -------------------------- SAF：导入 / 模板 --------------------------

    private void pickImportFile() {
        Activity act = getActivity();
        if (act == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"text/csv", "application/json", "text/plain"});
        try {
            startActivityForResult(intent, REQ_IMPORT);
        } catch (Exception e) {
            toast("无法打开文件选择器");
        }
    }

    private void createTemplateFile() {
        Activity act = getActivity();
        if (act == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/csv");
        intent.putExtra(Intent.EXTRA_TITLE, "设备台账导入模板.csv");
        try {
            startActivityForResult(intent, REQ_TEMPLATE);
        } catch (Exception e) {
            toast("无法打开保存位置");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT) {
            Exporter.commit(getActivity(), resultCode, data);
            return;
        }
        Activity act = getActivity();
        if (act == null || resultCode != Activity.RESULT_OK || data == null) {
            return;
        }
        Uri uri = data.getData();
        if (uri == null) {
            return;
        }
        if (requestCode == REQ_IMPORT) {
            importFromUri(uri);
        } else if (requestCode == REQ_TEMPLATE) {
            writeTemplateToUri(uri);
        }
    }

    private void importFromUri(Uri uri) {
        Activity act = getActivity();
        if (act == null || dbh == null) {
            return;
        }
        String content;
        InputStream is = null;
        try {
            is = act.getContentResolver().openInputStream(uri);
            if (is == null) {
                toast("无法读取文件：openInputStream 返回 null");
                return;
            }
            // try-with-resources 确保流一定关闭，避免异常导致误判/资源泄漏
            try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                }
                content = sb.toString();
            }
        } catch (Exception e) {
            toast("读取失败：" + e.getMessage());
            return;
        }

        int ok = 0;
        int skip = 0;
        String lower = content.toLowerCase();
        try {
            if (lower.trim().startsWith("[")) {
                // 可能是 JSON 数组
                if (content.trim().startsWith("[")) {
                    JSONArray arr = new JSONArray(content.trim());
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        if (o == null) {
                            skip++;
                            continue;
                        }
                        if (applyImport(o)) {
                            ok++;
                        } else {
                            skip++;
                        }
                    }
                } else {
                    toast("不支持的 JSON 格式");
                    return;
                }
            } else {
                // CSV
                List<String[]> rows = parseCsv(content);
                if (rows.isEmpty()) {
                    toast("文件为空或解析失败");
                    return;
                }
                String[] header = rows.get(0);
                Map<String, Integer> idx = buildCsvIndex(header);
                for (int i = 1; i < rows.size(); i++) {
                    String[] r = rows.get(i);
                    if (r == null || r.length == 0) {
                        continue;
                    }
                    if (applyImportCsv(r, idx)) {
                        ok++;
                    } else {
                        skip++;
                    }
                }
            }
        } catch (Exception e) {
            toast("解析失败：" + e.getMessage());
            return;
        }
        toast("导入完成：成功 " + ok + " 条，跳过 " + skip + " 条");
        load();
    }

    private boolean applyImport(JSONObject o) {
        String code = optStr(o, "code", "编号");
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String type = optStr(o, "type", "类型");
        String brand = optStr(o, "brand", "品牌");
        String model = optStr(o, "model", "型号");
        String ip = optStr(o, "ip", "IP");
        String status = optStr(o, "status", "状态");
        String location = optStr(o, "location", "位置");
        String parentCode = optStr(o, "parentCode", "parent_code", "父级编号");
        String note = optStr(o, "note", "备注");
        long id = dbh.upsertDevice(code, type, brand, model, ip, status, location, parentCode, note);
        return id > 0;
    }

    private boolean applyImportCsv(String[] r, Map<String, Integer> idx) {
        String code = getCsv(r, idx, "code", "编号");
        if (code == null || code.trim().isEmpty()) {
            return false;
        }
        String type = getCsv(r, idx, "type", "类型");
        String brand = getCsv(r, idx, "brand", "品牌");
        String model = getCsv(r, idx, "model", "型号");
        String ip = getCsv(r, idx, "ip", "IP");
        String status = getCsv(r, idx, "status", "状态");
        String location = getCsv(r, idx, "location", "位置");
        String parentCode = getCsv(r, idx, "parentCode", "父级编号");
        String note = getCsv(r, idx, "note", "备注");
        long id = dbh.upsertDevice(code, type, brand, model, ip, status, location, parentCode, note);
        return id > 0;
    }

    /** CSV 按行逗号简单分割（满足模板格式）。 */
    private List<String[]> parseCsv(String content) {
        List<String[]> rows = new ArrayList<>();
        String[] lines = content.split("\n");
        for (String ln : lines) {
            if (ln == null) {
                continue;
            }
            ln = ln.trim();
            if (ln.isEmpty()) {
                continue;
            }
            // 去掉可能的回车
            ln = ln.replace("\r", "");
            rows.add(ln.split(","));
        }
        return rows;
    }

    private Map<String, Integer> buildCsvIndex(String[] header) {
        Map<String, Integer> idx = new HashMap<>();
        if (header == null) {
            return idx;
        }
        for (int i = 0; i < header.length; i++) {
            idx.put(header[i].trim().toLowerCase(), i);
        }
        return idx;
    }

    private String getCsv(String[] r, Map<String, Integer> idx, String... keys) {
        for (String k : keys) {
            Integer i = idx.get(k.toLowerCase());
            if (i != null && i < r.length) {
                return r[i];
            }
        }
        return "";
    }

    private String optStr(JSONObject o, String... keys) {
        for (String k : keys) {
            if (o.has(k)) {
                try {
                    return o.getString(k);
                } catch (Exception e) {
                    return "";
                }
            }
        }
        return null;
    }

    private void writeTemplateToUri(Uri uri) {
        Activity act = getActivity();
        if (act == null) {
            return;
        }
        String template = "编号,类型,品牌,型号,IP,状态,位置,父级编号,备注\n"
                + "S001,camera,海康威视,DS-2CD3346,10.10.10.21,online,1F大堂东侧,,枪机\n"
                + "S003,door,立林,L-A1四门控制器,10.10.20.10,online,1F弱电间,,四门控制器\n"
                + "S004,door,立林,读卡器IC,-,online,1F大门,S003,韦根34读卡器\n";
        try {
            java.io.OutputStream os = act.getContentResolver().openOutputStream(uri);
            if (os == null) {
                toast("无法写入文件");
                return;
            }
            os.write(template.getBytes(StandardCharsets.UTF_8));
            os.close();
            toast("模板已保存");
        } catch (Exception e) {
            toast("保存失败：" + e.getMessage());
        }
    }

    // -------------------------- 小工具 --------------------------

    private Set<Long> safeFaultIds() {
        if (dbh == null) {
            return new HashSet<>();
        }
        try {
            return dbh.getFaultDeviceIds();
        } catch (Exception e) {
            return new HashSet<>();
        }
    }

    private Button btn(Activity act, String text, boolean primary, View.OnClickListener l) {
        Button b = new Button(act);
        b.setText(text);
        b.setTextSize(13);
        if (primary) {
            b.setTextColor(Color.WHITE);
            b.setBackgroundColor(C(act, R.color.primary));
        } else {
            b.setTextColor(C(act, R.color.text));
            b.setBackgroundColor(C(act, R.color.chip));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(act, 8), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private EditText e(Activity act, String hint, String val) {
        EditText et = new EditText(act);
        et.setHint(hint);
        if (val != null) {
            et.setText(val);
        }
        return et;
    }

    private TextView label(Activity act, String t) {
        TextView tv = new TextView(act);
        tv.setText(t);
        tv.setTextSize(12);
        tv.setTextColor(C(act, R.color.muted));
        tv.setPadding(0, 10, 0, 2);
        return tv;
    }

    private int dp(Activity act, int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private int C(Activity act, int resId) {
        return act.getResources().getColor(resId);
    }

    private android.content.Context getActivitySafeContext() {
        Activity a = getActivity();
        return a != null ? a : getContext();
    }

    private static String nv(Cursor c, int i) {
        try {
            String v = c.getString(i);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private void toast(String msg) {
        Activity act = getActivity();
        if (act != null) {
            Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
        }
    }
}

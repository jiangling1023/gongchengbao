package com.safetyrepair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
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

import java.util.List;

/**
 * 故障工单：报修/排查/闭环的增删改查。
 *
 * <p>增强：
 * <ul>
 *   <li>防御性编码：Activity 非空、Cursor try/finally、NPE 兜底。</li>
 *   <li>支持按 device_id 过滤（来自设备页「查看关联工单」）。</li>
 *   <li>新建/编辑时按设备文本回填 device_id，实现设备-工单联动。</li>
 * </ul>
 */
public class TicketsFragment extends Fragment {
    private static final String ARG_DEVICE_ID = "device_id";

    private DBHelper dbh;
    private SQLiteDatabase db;
    private LinearLayout listContainer;
    private long filterDeviceId = -1;
    private static final int REQ_EXPORT = 2001;

    /** 三级权限：管理员/负责人可看全部工单并派单；员工仅看指派给自己的工单。 */
    private boolean canManage;
    /** 当前登录用户名（员工过滤工单用）。 */
    private String currentUsername;

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

        Bundle args = getArguments();
        if (args != null) {
            filterDeviceId = args.getLong(ARG_DEVICE_ID, -1);
        }

        // 三级权限：管理员/负责人全局盯工单；员工只处理指派给自己的
        String role = DBHelper.getCurrentRole(act);
        canManage = DBHelper.ROLE_ADMIN.equals(role) || DBHelper.ROLE_SUPERVISOR.equals(role);
        currentUsername = DBHelper.getCurrentUsername(act);

        ScrollView sv = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView h = new TextView(act);
        h.setText("故障工单");
        h.setTextSize(18);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(C(act, R.color.text));
        h.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button add = btn(act, "+ 报修", true, v -> form(null));
        top.addView(h);
        top.addView(add);
        root.addView(top);

        // 过滤提示栏
        if (filterDeviceId >= 0) {
            LinearLayout filterBar = new LinearLayout(act);
            filterBar.setOrientation(LinearLayout.HORIZONTAL);
            filterBar.setPadding(0, 6, 0, 6);
            TextView ft = new TextView(act);
            ft.setText("已按设备 [" + dbh.getDeviceCode(filterDeviceId) + "] 过滤");
            ft.setTextSize(13);
            ft.setTextColor(C(act, R.color.primary));
            ft.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            Button clear = btn(act, "查看全部", false, v -> {
                filterDeviceId = -1;
                load();
            });
            filterBar.addView(ft);
            filterBar.addView(clear);
            root.addView(filterBar);
        }

        listContainer = new LinearLayout(act);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        // 导出行：Excel / PDF（SAF 自选保存位置）
        LinearLayout expRow = new LinearLayout(act);
        expRow.setOrientation(LinearLayout.HORIZONTAL);
        expRow.setPadding(0, 6, 0, 4);
        Button expXls = btn(act, "导出Excel", false, v -> doExport("excel"));
        Button expPdf = btn(act, "导出PDF", false, v -> doExport("pdf"));
        expRow.addView(expXls);
        expRow.addView(expPdf);
        root.addView(expRow);

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
        String[] headers = ReportData.ticketHeaders();
        List<String[]> rows = ReportData.ticketRows(db);
        Exporter.prepare(format, headers, rows);
        Intent intent = "pdf".equals(format)
                ? Exporter.buildPdfIntent("故障工单报表.pdf")
                : Exporter.buildExcelIntent("故障工单报表.xls");
        startActivityForResult(intent, REQ_EXPORT);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_EXPORT) {
            Exporter.commit(getActivity(), resultCode, data);
        }
    }

    /** 工单列表：读路径改用 DBHelper 安全查询，杜绝裸 rawQuery 抛异常导致「读取工单失败」。 */
    private void load() {
        if (listContainer == null || dbh == null) {
            return;
        }
        listContainer.removeAllViews();
        // 员工仅加载 assigned=自己 的工单；管理员/负责人加载全部
        List<String[]> rows = dbh.queryTickets(filterDeviceId, canManage ? null : currentUsername);
        for (String[] r : rows) {
            if (r == null || r.length < 7) {
                continue;
            }
            try {
                final long id = Long.parseLong(r[0]);
                String code = r[1];
                String dev = r[2];
                String title = r[3];
                String level = r[4];
                String status = r[5];
                String assigned = r[6];
                listContainer.addView(ticketCard(id, code, dev, title, level, status, assigned));
            } catch (Exception e) {
                // 跳过异常行，不中断整页渲染
            }
        }
    }

    private LinearLayout ticketCard(final long id, String code, String dev, String title,
                                    String level, String status, String assigned) {
        Activity act = getActivity();
        if (act == null) {
            return new LinearLayout(getContext());
        }
        boolean closed = "closed".equals(status);

        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(C(act, R.color.card));
        card.setPadding(14, 12, 14, 12);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cp);

        TextView t1 = new TextView(act);
        t1.setText("【" + nz(level) + "】" + nz(title));
        t1.setTextSize(15);
        t1.setTypeface(null, Typeface.BOLD);
        t1.setTextColor(C(act, R.color.text));
        t1.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView t2 = new TextView(act);
        String assignedText = (assigned == null || assigned.isEmpty()) ? "未派单" : ("处理人：" + assigned);
        t2.setText(nz(code) + "  |  " + nz(dev) + "  |  " + (closed ? "已结" : "处理中")
                + "  |  " + assignedText);
        t2.setTextSize(13);
        t2.setTextColor(closed ? C(act, R.color.ok) : C(act, R.color.warn));
        t2.setPadding(0, 4, 0, 0);

        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(0, 8, 0, 0);
        Button edit = btn(act, "编辑/闭环", false, v -> form(id));
        btns.addView(edit);
        if (canManage) {
            // 员工不可删单，仅管理员/负责人可见删除
            Button del = btn(act, "删除", false, v -> {
                if (db != null) {
                    try {
                        db.delete("tickets", "id=?", new String[]{String.valueOf(id)});
                    } catch (Exception e) {
                        toast("删除失败：" + e.getMessage());
                    }
                } else {
                    toast("数据库不可用，无法删除");
                }
                load();
            });
            btns.addView(del);
        }

        card.addView(t1);
        card.addView(t2);
        card.addView(btns);
        return card;
    }

    private void form(final Long id) {
        Activity act = getActivity();
        if (act == null || db == null) {
            return;
        }
        Cursor ex = null;
        String[] v = new String[9]; // device,title,ftype,level,status,description,solution,code,assigned
        if (id != null) {
            try {
                ex = db.rawQuery("SELECT device,title,ftype,level,status,description,solution,code,assigned"
                        + " FROM tickets WHERE id=?", new String[]{String.valueOf(id)});
                if (ex.moveToFirst()) {
                    for (int i = 0; i < 8; i++) {
                        v[i] = ex.getString(i);
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

        final EditText device = e(act, "关联设备/点位", v[0]);
        final EditText title = e(act, "故障标题", v[1]);
        final Spinner ftype = new Spinner(act);
        ftype.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item,
                new String[]{"网络", "权限", "硬件", "软件", "其他"}));
        if (v[2] != null) {
            selByVal(ftype, v[2]);
        }
        final Spinner level = new Spinner(act);
        level.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item,
                new String[]{"P0", "P1", "P2", "P3"}));
        if (v[3] != null) {
            selByVal(level, v[3]);
        }
        final Spinner status = new Spinner(act);
        status.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item,
                new String[]{"processing", "closed"}));
        if (v[4] != null) {
            selByVal(status, v[4]);
        }
        // 派单下拉（仅管理员/负责人可见）：列出可指派的负责人与员工
        final Spinner assigned = new Spinner(act);
        final java.util.List<String> assignUsernames = new java.util.ArrayList<>();
        if (canManage) {
            java.util.List<String> names = new java.util.ArrayList<>();
            names.add("未派单");
            assignUsernames.add("");
            for (DBHelper.User au : dbh.listAssignableUsers()) {
                names.add((au.name == null || au.name.isEmpty() ? au.username : au.name)
                        + "（" + DBHelper.roleLabel(au.role) + "）");
                assignUsernames.add(au.username);
            }
            assigned.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item, names));
            if (v[8] != null) {
                for (int i = 0; i < assignUsernames.size(); i++) {
                    if (assignUsernames.get(i).equals(v[8])) {
                        assigned.setSelection(i);
                        break;
                    }
                }
            }
        }
        final EditText desc = e(act, "故障描述", v[5]);
        final EditText solution = e(act, "处理/解决", v[6]);
        fl.addView(label(act, "关联设备")); fl.addView(device);
        fl.addView(label(act, "故障标题")); fl.addView(title);
        fl.addView(label(act, "类型")); fl.addView(ftype);
        fl.addView(label(act, "级别")); fl.addView(level);
        fl.addView(label(act, "状态")); fl.addView(status);
        if (canManage) {
            fl.addView(label(act, "指派给（派单）")); fl.addView(assigned);
        }
        fl.addView(label(act, "故障描述")); fl.addView(desc);
        fl.addView(label(act, "处理/解决")); fl.addView(solution);
        fs.addView(fl);

        AlertDialog.Builder b = new AlertDialog.Builder(act);
        b.setTitle(id == null ? "新增工单" : "编辑工单");
        b.setView(fs);
        b.setPositiveButton("保存", (d, w) -> {
            if (db == null) {
                return;
            }
            String ft = ftype.getSelectedItem().toString();
            String lv = level.getSelectedItem().toString();
            String st = status.getSelectedItem().toString();
            String devText = device.getText() == null ? "" : device.getText().toString().trim();
            // 回填 device_id：按编号或名称模糊匹配
            long deviceId = (dbh != null) ? dbh.resolveDeviceId(devText) : -1;
            // closed 写入真实日期字符串（修复旧版误写 "date('now')" 字面量的 Bug）
            String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.CHINA)
                    .format(new java.util.Date());
            String closedVal = st.equals("closed") ? today : "";
            if (id == null) {
                String code = "WX-" + System.currentTimeMillis();
                String assignedTo = canManage
                        ? assignUsernames.get(assigned.getSelectedItemPosition()) : "";
                db.execSQL("INSERT INTO tickets(code,device_id,device,title,ftype,level,status,description,solution,created,closed,assigned)"
                                + " VALUES(?,?,?,?,?,?,?,?,?,date('now'),?,?)",
                        new Object[]{code, deviceId, devText, title.getText().toString(), ft, lv, st,
                                desc.getText().toString(), solution.getText().toString(),
                                closedVal, assignedTo});
            } else {
                // 员工编辑时保留原指派人；管理员/负责人以派单下拉为准
                String assignedTo = canManage
                        ? assignUsernames.get(assigned.getSelectedItemPosition())
                        : (v[8] == null ? "" : v[8]);
                db.execSQL("UPDATE tickets SET device_id=?,device=?,title=?,ftype=?,level=?,status=?,description=?,solution=?,closed=?,assigned=?"
                                + " WHERE id=?",
                        new Object[]{deviceId, devText, title.getText().toString(), ft, lv, st,
                                desc.getText().toString(), solution.getText().toString(),
                                closedVal, assignedTo, id});
            }
            load();
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private void selByVal(Spinner sp, String val) {
        ArrayAdapter a = (ArrayAdapter) sp.getAdapter();
        for (int i = 0; i < a.getCount(); i++) {
            if (a.getItem(i).toString().equals(val)) {
                sp.setSelection(i);
                return;
            }
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
            android.widget.Toast.makeText(act, msg, android.widget.Toast.LENGTH_SHORT).show();
        }
    }
}

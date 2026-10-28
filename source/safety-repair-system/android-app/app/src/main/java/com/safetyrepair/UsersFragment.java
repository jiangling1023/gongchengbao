package com.safetyrepair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * 人员管理（仅管理员可达，MainActivity.openModuleByKey 已拦截非管理员）。
 *
 * <p>功能对齐网页版人员管理：
 * <ul>
 *   <li>账号列表：用户名 / 姓名 / 角色徽章 / 手机号 / 状态；</li>
 *   <li>新增账号：用户名 + 初始密码 + 姓名 + 角色（管理员 / 负责人 / 员工）+ 手机号；</li>
 *   <li>重置密码、启用/停用、删除（内置 admin 不可停用/删除/改角色，DBHelper 已兜底）。</li>
 * </ul>
 * 权限分工：管理员把控全局并给下属开通账号；负责人盯员工工单；员工处理指派工单。</p>
 */
public class UsersFragment extends Fragment {

    private DBHelper dbh;
    private LinearLayout listContainer;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        final Activity act = getActivity();
        if (act == null) {
            return new LinearLayout(c != null ? c.getContext() : null);
        }
        try {
            dbh = new DBHelper(act);
        } catch (Exception e) {
            dbh = null;
            toast(act, "数据库打开失败：" + e.getMessage());
        }

        ScrollView sv = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(act, 12), dp(act, 12), dp(act, 12), dp(act, 12));
        sv.addView(root);

        // 顶部：标题 + 新增按钮
        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView h = new TextView(act);
        h.setText("账号与角色");
        h.setTextSize(18);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(Color.parseColor("#1f2328"));
        h.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(act);
        add.setText("+ 新增账号");
        add.setTextSize(13);
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(Color.parseColor("#1f6feb"));
        add.setOnClickListener(v -> showAddDialog(act));
        top.addView(h);
        top.addView(add);
        root.addView(top);

        // 说明条：三级权限分工
        TextView tip = new TextView(act);
        tip.setText("管理员：把控全局、开通账号 ｜ 负责人：盯工单、派单 ｜ 员工：处理指派给自己的工单");
        tip.setTextSize(12);
        tip.setTextColor(Color.parseColor("#6b7280"));
        tip.setPadding(0, dp(act, 6), 0, dp(act, 6));
        root.addView(tip);

        listContainer = new LinearLayout(act);
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        load(act);
        return sv;
    }

    /** 刷新账号列表。 */
    private void load(final Activity act) {
        if (listContainer == null) {
            return;
        }
        listContainer.removeAllViews();
        if (dbh == null) {
            toast(act, "数据库不可用");
            return;
        }
        List<DBHelper.User> users = dbh.listUsers();
        if (users == null || users.isEmpty()) {
            TextView empty = new TextView(act);
            empty.setText("暂无账号");
            empty.setTextSize(13);
            empty.setTextColor(Color.parseColor("#6b7280"));
            empty.setPadding(0, dp(act, 12), 0, 0);
            listContainer.addView(empty);
            return;
        }
        for (final DBHelper.User u : users) {
            listContainer.addView(userCard(act, u));
        }
    }

    private View userCard(final Activity act, final DBHelper.User u) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setBackground(roundDrawable(Color.WHITE, dp(act, 12)));
        card.setPadding(dp(act, 14), dp(act, 12), dp(act, 14), dp(act, 12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(act, 10));
        card.setLayoutParams(lp);

        // 第一行：姓名 + 角色徽章 + 状态
        LinearLayout row1 = new LinearLayout(act);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(act);
        name.setText((u.name == null || u.name.isEmpty() ? u.username : u.name)
                + "（" + u.username + "）");
        name.setTextSize(15);
        name.setTypeface(null, Typeface.BOLD);
        name.setTextColor(Color.parseColor("#1f2328"));
        name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row1.addView(name);
        row1.addView(roleBadge(act, u.role));
        row1.addView(statusBadge(act, u.status));
        card.addView(row1);

        // 第二行：手机号
        TextView phone = new TextView(act);
        phone.setText("手机号：" + (u.phone == null || u.phone.isEmpty() ? "未填写" : u.phone));
        phone.setTextSize(13);
        phone.setTextColor(Color.parseColor("#6b7280"));
        phone.setPadding(0, dp(act, 4), 0, 0);
        card.addView(phone);

        // 第三行：操作按钮
        LinearLayout ops = new LinearLayout(act);
        ops.setOrientation(LinearLayout.HORIZONTAL);
        ops.setPadding(0, dp(act, 8), 0, 0);
        boolean isAdmin = DBHelper.ROLE_ADMIN.equals(u.role);
        if (!isAdmin) {
            ops.addView(smallBtn(act, "重置密码", v -> showResetPassDialog(act, u)));
            ops.addView(smallBtn(act, "active".equals(u.status) ? "停用" : "启用", v -> toggleStatus(act, u)));
            ops.addView(smallBtn(act, "删除", v -> confirmDelete(act, u)));
        } else {
            TextView fixed = new TextView(act);
            fixed.setText("内置管理员，不可修改");
            fixed.setTextSize(12);
            fixed.setTextColor(Color.parseColor("#9aa3ad"));
            fixed.setPadding(0, dp(act, 6), 0, 0);
            ops.addView(fixed);
        }
        card.addView(ops);
        return card;
    }

    /** 新增账号弹窗。 */
    private void showAddDialog(final Activity act) {
        if (dbh == null) {
            toast(act, "数据库不可用");
            return;
        }
        LinearLayout box = dialogBox(act);
        final EditText username = input(act, "登录用户名（唯一）", false);
        final EditText pass = input(act, "初始密码（至少 6 位）", true);
        final EditText name = input(act, "姓名", false);
        final Spinner role = new Spinner(act);
        role.setAdapter(new ArrayAdapter<>(act, android.R.layout.simple_spinner_item,
                new String[]{"员工", "负责人", "管理员"}));
        final EditText phone = input(act, "手机号（选填）", false);
        box.addView(labeled(act, "用户名", username));
        box.addView(labeled(act, "初始密码", pass));
        box.addView(labeled(act, "姓名", name));
        box.addView(labeled(act, "角色", role));
        box.addView(labeled(act, "手机号", phone));

        new AlertDialog.Builder(act)
                .setTitle("新增账号")
                .setView(box)
                .setPositiveButton("创建", (d, w) -> {
                    String un = txt(username), pw = txt(pass), nm = txt(name), ph = txt(phone);
                    if (un.isEmpty() || pw.isEmpty()) {
                        toast(act, "用户名和初始密码必填");
                        return;
                    }
                    if (pw.length() < 6) {
                        toast(act, "初始密码至少 6 位");
                        return;
                    }
                    String roleVal = role.getSelectedItemPosition() == 2 ? DBHelper.ROLE_ADMIN
                            : (role.getSelectedItemPosition() == 1 ? DBHelper.ROLE_SUPERVISOR
                            : DBHelper.ROLE_WORKER);
                    if (dbh.getUserByUsername(un) != null) {
                        toast(act, "用户名已存在");
                        return;
                    }
                    long id = dbh.insertUser(un, pw, nm, roleVal, ph);
                    toast(act, id > 0 ? "账号已创建" : "创建失败");
                    load(act);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 重置密码弹窗。 */
    private void showResetPassDialog(final Activity act, final DBHelper.User u) {
        LinearLayout box = dialogBox(act);
        final EditText p1 = input(act, "新密码（至少 6 位）", true);
        final EditText p2 = input(act, "确认新密码", true);
        box.addView(labeled(act, "新密码", p1));
        box.addView(labeled(act, "确认新密码", p2));
        new AlertDialog.Builder(act)
                .setTitle("重置密码：" + u.username)
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String a = txt(p1), b = txt(p2);
                    if (a.isEmpty() || a.length() < 6) {
                        toast(act, "新密码至少 6 位");
                        return;
                    }
                    if (!a.equals(b)) {
                        toast(act, "两次输入不一致");
                        return;
                    }
                    boolean ok = dbh.resetPassword(u.id, a);
                    toast(act, ok ? "密码已重置" : "重置失败");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 启用 / 停用切换。 */
    private void toggleStatus(final Activity act, final DBHelper.User u) {
        String next = "active".equals(u.status) ? "disabled" : "active";
        boolean ok = dbh.updateUser(u.id, u.name, u.role, u.phone, next);
        toast(act, ok ? ("已" + ("active".equals(next) ? "启用" : "停用")) : "操作失败");
        load(act);
    }

    /** 删除二次确认。 */
    private void confirmDelete(final Activity act, final DBHelper.User u) {
        new AlertDialog.Builder(act)
                .setTitle("删除账号")
                .setMessage("确定删除「" + (u.name == null || u.name.isEmpty() ? u.username : u.name) + "」吗？该操作不可恢复。")
                .setPositiveButton("删除", (d, w) -> {
                    boolean ok = dbh.deleteUser(u.id);
                    toast(act, ok ? "已删除" : "删除失败");
                    load(act);
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // -------------------------- 小工具 --------------------------

    private LinearLayout dialogBox(Activity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 20), dp(act, 10), dp(act, 20), 0);
        return box;
    }

    private LinearLayout labeled(Activity act, String label, View field) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.VERTICAL);
        TextView l = new TextView(act);
        l.setText(label);
        l.setTextSize(12);
        l.setTextColor(Color.parseColor("#6b7280"));
        l.setPadding(0, dp(act, 8), 0, dp(act, 2));
        row.addView(l);
        row.addView(field);
        return row;
    }

    private EditText input(Activity act, String hint, boolean isPass) {
        EditText et = new EditText(act);
        et.setHint(hint);
        et.setSingleLine(true);
        if (isPass) {
            et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        et.setBackgroundColor(Color.parseColor("#f3f5f8"));
        et.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
        return et;
    }

    private Button smallBtn(Activity act, String text, View.OnClickListener l) {
        Button b = new Button(act);
        b.setText(text);
        b.setTextSize(12);
        b.setTextColor(Color.parseColor("#1f2328"));
        b.setBackgroundColor(Color.parseColor("#eef2f7"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(act, 8), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    private TextView roleBadge(Activity act, String role) {
        TextView badge = new TextView(act);
        badge.setText(DBHelper.roleLabel(role));
        badge.setTextSize(11);
        badge.setTextColor(Color.WHITE);
        badge.setPadding(dp(act, 8), dp(act, 2), dp(act, 8), dp(act, 2));
        int color;
        if (DBHelper.ROLE_ADMIN.equals(role)) {
            color = 0xFFD1242F;
        } else if (DBHelper.ROLE_SUPERVISOR.equals(role)) {
            color = 0xFFB45309;
        } else {
            color = 0xFF1E88E5;
        }
        badge.setBackground(roundDrawable(color, dp(act, 9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(act, 6), 0, 0, 0);
        badge.setLayoutParams(lp);
        return badge;
    }

    private TextView statusBadge(Activity act, String status) {
        boolean active = "active".equals(status);
        TextView badge = new TextView(act);
        badge.setText(active ? "正常" : "已停用");
        badge.setTextSize(11);
        badge.setTextColor(Color.parseColor(active ? "#1a7f37" : "#6b7280"));
        badge.setPadding(dp(act, 6), dp(act, 2), dp(act, 6), dp(act, 2));
        badge.setBackground(roundDrawable(active ? 0xFFE7F5EC : 0xFFEEF1F5, dp(act, 9)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(act, 6), 0, 0, 0);
        badge.setLayoutParams(lp);
        return badge;
    }

    private GradientDrawable roundDrawable(int color, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private String txt(EditText et) {
        return et.getText() == null ? "" : et.getText().toString().trim();
    }

    private int dp(Activity act, int v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private void toast(Activity act, String msg) {
        Toast.makeText(act, msg, Toast.LENGTH_SHORT).show();
    }
}

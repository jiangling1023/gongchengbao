package com.safetyrepair;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.Intent;
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
import android.widget.TextView;
import android.widget.Toast;

/**
 * 个人中心（首页右上角齿轮入口）。
 *
 * <p>展示当前登录用户的信息（姓名 / 用户名 / 角色徽章），提供：
 * <ul>
 *   <li>修改自己的密码（校验旧密码，DBHelper.updateOwnPassword）；</li>
 *   <li>退出登录（清会话并回登录页）。</li>
 * </ul>
 * 纯 framework 实现，无 AndroidX / 第三方依赖；所有输入均防御性处理，不闪退。</p>
 */
public class ProfileFragment extends Fragment {

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        final Activity act = getActivity();
        if (act == null) {
            return new LinearLayout(c != null ? c.getContext() : null);
        }

        DBHelper.User u = DBHelper.getCurrentUser(act);
        ScrollView sv = new ScrollView(act);
        LinearLayout root = new LinearLayout(act);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#eef1f5"));
        root.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));
        sv.addView(root);

        if (u == null) {
            // 无会话兜底：理论上 MainActivity 已拦截，这里防御性提示
            TextView tip = new TextView(act);
            tip.setText("未检测到登录会话，请返回登录页重新登录。");
            tip.setTextSize(14);
            tip.setTextColor(Color.parseColor("#6b7280"));
            root.addView(tip);
            return sv;
        }

        // 1) 用户信息卡片
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(act, 18), dp(act, 18), dp(act, 18), dp(act, 18));
        card.setBackground(roundDrawable(Color.WHITE, dp(act, 14)));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dp(act, 12));
        card.setLayoutParams(cardLp);

        // 头像圆块（取姓名首字）
        TextView avatar = new TextView(act);
        String name = u.name == null || u.name.isEmpty() ? u.username : u.name;
        avatar.setText(name.substring(0, 1));
        avatar.setTextSize(22);
        avatar.setTextColor(Color.WHITE);
        avatar.setGravity(Gravity.CENTER);
        avatar.setBackground(roundDrawable(0xFF1E88E5, dp(act, 26)));
        LinearLayout.LayoutParams avLp = new LinearLayout.LayoutParams(dp(act, 52), dp(act, 52));
        avLp.setMargins(0, 0, 0, dp(act, 10));
        avatar.setLayoutParams(avLp);
        card.addView(avatar);

        card.addView(infoRow(act, "姓名", name));
        card.addView(infoRow(act, "用户名", u.username));
        card.addView(infoRow(act, "手机号", u.phone == null || u.phone.isEmpty() ? "未填写" : u.phone));

        // 角色徽章行
        LinearLayout roleRow = new LinearLayout(act);
        roleRow.setOrientation(LinearLayout.HORIZONTAL);
        roleRow.setGravity(Gravity.CENTER_VERTICAL);
        roleRow.setPadding(0, dp(act, 8), 0, dp(act, 2));
        TextView roleKey = new TextView(act);
        roleKey.setText("角色");
        roleKey.setTextSize(13);
        roleKey.setTextColor(Color.parseColor("#6b7280"));
        roleKey.setLayoutParams(new LinearLayout.LayoutParams(dp(act, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView badge = new TextView(act);
        badge.setText(DBHelper.roleLabel(u.role));
        badge.setTextSize(12);
        badge.setTextColor(Color.WHITE);
        badge.setPadding(dp(act, 10), dp(act, 3), dp(act, 10), dp(act, 3));
        badge.setBackground(roundDrawable(roleColor(u.role), dp(act, 10)));
        roleRow.addView(roleKey);
        roleRow.addView(badge);
        card.addView(roleRow);
        root.addView(card);

        // 2) 操作卡片：修改密码 + 退出登录
        LinearLayout ops = new LinearLayout(act);
        ops.setOrientation(LinearLayout.VERTICAL);
        ops.setBackgroundColor(Color.WHITE);
        ops.setPadding(dp(act, 18), dp(act, 18), dp(act, 18), dp(act, 18));
        ops.setBackground(roundDrawable(Color.WHITE, dp(act, 14)));

        Button changePass = bigButton(act, "修改密码", "#1f6feb");
        changePass.setOnClickListener(v -> showChangePassDialog(act));
        ops.addView(changePass);

        Button logout = bigButton(act, "退出登录", "#d1242f");
        LinearLayout.LayoutParams loLp = (LinearLayout.LayoutParams) logout.getLayoutParams();
        loLp.setMargins(0, dp(act, 10), 0, 0);
        logout.setOnClickListener(v -> confirmLogout(act));
        ops.addView(logout);
        root.addView(ops);

        // 3) 版本说明
        TextView ver = new TextView(act);
        ver.setText("安防维修排查 · v1.2.0\n管理员：全局与账号管理 / 负责人：盯工单派单 / 员工：处理指派工单");
        ver.setTextSize(12);
        ver.setTextColor(Color.parseColor("#9aa3ad"));
        ver.setGravity(Gravity.CENTER);
        ver.setPadding(0, dp(act, 16), 0, 0);
        root.addView(ver);

        return sv;
    }

    /** 修改密码弹窗：旧密码 + 新密码 + 确认新密码。 */
    private void showChangePassDialog(final Activity act) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(act, 20), dp(act, 10), dp(act, 20), 0);

        final EditText oldP = passInput(act, "旧密码");
        final EditText newP = passInput(act, "新密码（至少 6 位）");
        final EditText newP2 = passInput(act, "确认新密码");
        box.addView(oldP);
        box.addView(newP);
        box.addView(newP2);

        new AlertDialog.Builder(act)
                .setTitle("修改密码")
                .setView(box)
                .setPositiveButton("保存", (d, w) -> {
                    String o = txt(oldP), n1 = txt(newP), n2 = txt(newP2);
                    if (o.isEmpty() || n1.isEmpty() || n2.isEmpty()) {
                        toast(act, "请填写完整");
                        return;
                    }
                    if (n1.length() < 6) {
                        toast(act, "新密码至少 6 位");
                        return;
                    }
                    if (!n1.equals(n2)) {
                        toast(act, "两次输入的新密码不一致");
                        return;
                    }
                    DBHelper dbh = null;
                    try {
                        dbh = new DBHelper(act);
                    } catch (Exception ignore) {
                        // 数据库异常降级
                    }
                    if (dbh == null) {
                        toast(act, "数据库不可用，请重试");
                        return;
                    }
                    DBHelper.User u = DBHelper.getCurrentUser(act);
                    if (u == null) {
                        toast(act, "会话已失效，请重新登录");
                        return;
                    }
                    boolean ok = dbh.updateOwnPassword(u.id, o, n1);
                    toast(act, ok ? "密码修改成功" : "旧密码不正确");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    /** 退出登录二次确认，确认后清会话回登录页。 */
    private void confirmLogout(final Activity act) {
        new AlertDialog.Builder(act)
                .setTitle("退出登录")
                .setMessage("确定要退出当前账号吗？")
                .setPositiveButton("退出", (d, w) -> {
                    DBHelper.logout(act);
                    try {
                        startActivity(new Intent(act, LoginActivity.class));
                    } catch (Exception ignore) {
                        // 跳转异常也继续结束当前页
                    }
                    act.finish();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    // -------------------------- 小工具 --------------------------

    private EditText passInput(Activity act, String hint) {
        EditText et = new EditText(act);
        et.setHint(hint);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        et.setBackgroundColor(Color.parseColor("#f3f5f8"));
        et.setPadding(dp(act, 12), dp(act, 10), dp(act, 12), dp(act, 10));
        return et;
    }

    private LinearLayout infoRow(Activity act, String key, String val) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(0, dp(act, 8), 0, dp(act, 2));
        TextView k = new TextView(act);
        k.setText(key);
        k.setTextSize(13);
        k.setTextColor(Color.parseColor("#6b7280"));
        k.setLayoutParams(new LinearLayout.LayoutParams(dp(act, 80), ViewGroup.LayoutParams.WRAP_CONTENT));
        TextView v = new TextView(act);
        v.setText(val);
        v.setTextSize(14);
        v.setTextColor(Color.parseColor("#1f2328"));
        row.addView(k);
        row.addView(v);
        return row;
    }

    private Button bigButton(Activity act, String text, String color) {
        Button b = new Button(act);
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private int roleColor(String role) {
        if (DBHelper.ROLE_ADMIN.equals(role)) {
            return 0xFFD1242F;
        }
        if (DBHelper.ROLE_SUPERVISOR.equals(role)) {
            return 0xFFB45309;
        }
        return 0xFF1E88E5;
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

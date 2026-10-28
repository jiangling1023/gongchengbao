package com.safetyrepair;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * 登录页（应用入口 launcher）。
 *
 * <p>蓝色渐变头部风格 + 用户名/密码输入 + 登录按钮 + 提示文字（内置账号 admin/Admin@2026）。
 * 登录成功写入会话并跳转到 {@link MainActivity}；失败给出明确提示。任何异常均防御性处理，避免闪退。</p>
 */
public class LoginActivity extends Activity {

    private DBHelper dbh;
    private EditText etUser;
    private EditText etPass;
    private TextView tvMsg;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 已登录则直接进入主界面（避免重复登录）
        if (DBHelper.getCurrentUser(this) != null) {
            startMain();
            return;
        }

        try {
            dbh = new DBHelper(this);
        } catch (Exception e) {
            dbh = null;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.parseColor("#eef1f5"));
        root.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // 1) 蓝色渐变头部
        TextView header = new TextView(this);
        header.setText("安防维修排查");
        header.setTextSize(24);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        header.setTextColor(0xFFFFFFFF);
        header.setGravity(Gravity.CENTER);
        int padTop = dp(56);
        header.setPadding(dp(16), padTop, dp(16), dp(16));
        GradientDrawable gd = new GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF0E6FBF, 0xFF1565C0, 0xFF1E88E5});
        gd.setCornerRadii(new float[]{0, 0, 0, 0, 0, 0, 0, 0});
        header.setBackground(gd);
        header.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView sub = new TextView(this);
        sub.setText("楼宇智能化 · 运维工作台");
        sub.setTextSize(13);
        sub.setTextColor(0xFFCFE2FF);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(dp(16), 0, dp(16), dp(20));
        sub.setBackgroundColor(0xFF1E88E5);
        root.addView(header);
        root.addView(sub);

        // 2) 登录卡片
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.WHITE);
        card.setPadding(dp(20), dp(20), dp(20), dp(20));
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dp(16), dp(16), dp(16), dp(8));
        card.setLayoutParams(cardLp);

        TextView title = new TextView(this);
        title.setText("账号登录");
        title.setTextSize(18);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setTextColor(Color.parseColor("#1f2328"));
        title.setPadding(0, 0, 0, dp(12));

        etUser = new EditText(this);
        etUser.setHint("用户名");
        etUser.setSingleLine(true);
        styleInput(etUser);

        etPass = new EditText(this);
        etPass.setHint("密码");
        etPass.setSingleLine(true);
        etPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        styleInput(etPass);

        Button login = new Button(this);
        login.setText("登录");
        login.setTextColor(Color.WHITE);
        login.setTextSize(16);
        login.setBackgroundColor(Color.parseColor("#1f6feb"));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(16), 0, 0);
        login.setLayoutParams(lp);
        login.setOnClickListener(v -> doLogin());

        card.addView(title);
        card.addView(etUser);
        card.addView(etPass);
        card.addView(login);
        root.addView(card);

        // 3) 提示文字（内置账号）
        tvMsg = new TextView(this);
        tvMsg.setText("首次使用可使用内置账号登录：\n用户名 admin    密码 Admin@2026");
        tvMsg.setTextSize(12);
        tvMsg.setTextColor(Color.parseColor("#6b7280"));
        tvMsg.setGravity(Gravity.CENTER);
        tvMsg.setPadding(dp(16), dp(8), dp(16), dp(8));
        root.addView(tvMsg);

        setContentView(root);
    }

    private void doLogin() {
        if (dbh == null) {
            toast("数据库初始化失败，请重试");
            return;
        }
        String user = etUser.getText() == null ? "" : etUser.getText().toString().trim();
        String pass = etPass.getText() == null ? "" : etPass.getText().toString();
        if (user.isEmpty() || pass.isEmpty()) {
            toast("请输入用户名和密码");
            return;
        }
        DBHelper.User u = null;
        try {
            u = dbh.verifyLogin(user, pass);
        } catch (Exception e) {
            u = null;
        }
        if (u == null) {
            toast("用户名或密码错误，或账号已被停用");
            return;
        }
        DBHelper.saveSession(this, u);
        startMain();
    }

    private void startMain() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            // 即便跳转异常也结束登录页，避免栈残留
        }
        finish();
    }

    private void styleInput(EditText et) {
        et.setBackgroundColor(Color.parseColor("#f3f5f8"));
        et.setPadding(dp(12), dp(12), dp(12), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, dp(4));
        et.setLayoutParams(lp);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }
}

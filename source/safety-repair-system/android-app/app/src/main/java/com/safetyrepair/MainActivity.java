package com.safetyrepair;

import android.app.Activity;
import android.app.Fragment;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

/**
 * 原生主框架：顶部标题栏（含返回按钮）+ Fragment 容器，全竖版导航。
 * 不依赖 WebView / AndroidX，纯 framework 实现。
 *
 * <p>首页（index 0）为「蓝色宫格工作台」{@link WorkbenchFragment}：此时隐藏标题栏与返回按钮，
 * 由工作台自行提供导航；切换到其它模块时显示标题栏与「← 返回」按钮，点击回工作台。</p>
 *
 * <p>权限体系：本页为 {@code exported=false} 的非入口 Activity；onCreate 检测会话，
 * 无会话则跳回 {@link LoginActivity}（防绕过）。右上角齿轮按钮由工作台路由到 {@link ProfileFragment}。</p>
 */
public class MainActivity extends Activity {
    // index 0 为工作台首页；dashboard 不再占位（仍可通过工作台宫格跳入）。
    private static final String[][] MODULES = {
        {"workbench", "工作台"}, {"devices", "设备台账"}, {"tickets", "故障工单"},
        {"discover", "设备发现"}, {"wizard", "排障向导"}, {"tools", "工程宝"},
        {"inspections", "巡检"}, {"quickref", "速查卡"}, {"kb", "知识库"}, {"data", "数据"},
        {"users", "人员管理"},
    };

    private TextView tvTitle;
    private TextView btnBack;
    /** 跳转到工单页时携带的设备过滤条件（-1 表示不过滤）。 */
    private long pendingTicketDeviceFilter = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 会话校验：无登录会话则回登录页（防绕过）。LoginActivity 已为非入口且 exported=false。
        if (DBHelper.getCurrentUser(this) == null) {
            try {
                startActivity(new Intent(this, LoginActivity.class));
            } catch (Exception ignore) {
                // 忽略跳转异常
            }
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        Discovery.init(this);
        tvTitle = findViewById(R.id.tvTitle);
        btnBack = findViewById(R.id.btnBack);
        // 二级界面返回按钮：回到工作台首页（index 0）。
        btnBack.setOnClickListener(v -> openModule(0));
        openModule(0);
    }

    /** 是否为管理员（用于模块可达性判断）。 */
    public boolean isAdmin() {
        return DBHelper.ROLE_ADMIN.equals(DBHelper.getCurrentRole(this));
    }

    /**
     * 供子 Fragment 调用：切换到「故障工单」页并按设备过滤。
     * @param deviceId 设备 id，-1 表示不过滤
     */
    public void openTicketsForDevice(long deviceId) {
        pendingTicketDeviceFilter = deviceId;
        for (int i = 0; i < MODULES.length; i++) {
            if ("tickets".equals(MODULES[i][0])) {
                openModule(i);
                break;
            }
        }
    }

    /**
     * 公开：按模块下标切换（全竖版，无横版标签栏）。工作台首页隐藏标题栏与返回按钮；
     * 其它模块显示标题栏与「← 返回」按钮，点击返回按钮回工作台。
     */
    public void openModule(int idx) {
        if (idx < 0 || idx >= MODULES.length) {
            idx = 0;
        }
        String key = MODULES[idx][0];
        boolean isHome = "workbench".equals(key);

        tvTitle.setText(MODULES[idx][1]);
        tvTitle.setVisibility(isHome ? View.GONE : View.VISIBLE);
        btnBack.setVisibility(isHome ? View.GONE : View.VISIBLE);

        Fragment f = createFragment(idx);
        getFragmentManager().beginTransaction().replace(R.id.container, f).commit();
    }

    /**
     * 公开：按模块 key 切换。MODULES 中的模块直接定位下标；
     * dashboard 不在 MODULES（仅作工作台宫格入口），单独直接渲染并支持返回。
     * 人员管理仅管理员可达，非管理员静默忽略。
     */
    public void openModuleByKey(String key) {
        if (key == null) {
            return;
        }
        if ("dashboard".equals(key)) {
            openDirect(new DashboardFragment(), "仪表盘");
            return;
        }
        // 三级权限：人员管理、数据备份仅管理员可达，非管理员静默忽略
        if (("users".equals(key) || "data".equals(key)) && !isAdmin()) {
            return;
        }
        for (int i = 0; i < MODULES.length; i++) {
            if (MODULES[i][0].equals(key)) {
                openModule(i);
                return;
            }
        }
    }

    /** 打开个人中心（右上角齿轮入口）。 */
    public void openProfile() {
        tvTitle.setText("个人中心");
        tvTitle.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        getFragmentManager().beginTransaction()
                .replace(R.id.container, new ProfileFragment())
                .commit();
    }

    /** 直接渲染不在标签栏中的模块（如 dashboard），显示标题栏与返回按钮，点击返回回工作台。 */
    private void openDirect(Fragment f, String title) {
        tvTitle.setText(title);
        tvTitle.setVisibility(View.VISIBLE);
        btnBack.setVisibility(View.VISIBLE);
        getFragmentManager().beginTransaction()
                .replace(R.id.container, f)
                .commit();
    }

    /** 按模块下标创建 Fragment。 */
    private Fragment createFragment(int idx) {
        return createFragmentByKey(MODULES[idx][0]);
    }

    /** 按模块 key 创建 Fragment（dashboard / workbench 等统一入口）。 */
    private Fragment createFragmentByKey(String key) {
        switch (key) {
            case "workbench": return new WorkbenchFragment();
            case "dashboard": return new DashboardFragment();
            case "devices": return new DevicesFragment();
            case "tickets": {
                TicketsFragment f = new TicketsFragment();
                if (pendingTicketDeviceFilter >= 0) {
                    android.os.Bundle b = new android.os.Bundle();
                    b.putLong("device_id", pendingTicketDeviceFilter);
                    f.setArguments(b);
                    pendingTicketDeviceFilter = -1;
                }
                return f;
            }
            case "discover": return new DiscoverFragment();
            case "wizard": return new WizardFragment();
            case "tools": return new ToolsFragment();
            case "inspections": return new InspectionsFragment();
            case "quickref": return new QuickRefFragment();
            case "kb": return new KBFragment();
            case "data": return new DataFragment();
            case "users": return new UsersFragment();
        }
        return new DashboardFragment();
    }
}

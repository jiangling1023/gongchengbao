package com.safetyrepair;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 蓝色宫格工作台：作为 App 首页（index 0，替代原横版标签栏）。
 *
 * <p>纯 framework 实现，无任何 AndroidX / WebView / 第三方库依赖。布局完全由 Java 代码构建：
 * 顶部为蓝色三色渐变圆弧头部（含标题、副标题、圆形工具图标块与 4 个高频入口卡），
 * 下方为可滚动的「分组 + 4 列宫格」导航。宫格点击通过 {@link MainActivity#openModuleByKey(String)}
 * 跳转到对应功能模块；「仪表盘」入口直接路由到 {@code DashboardFragment}。</p>
 */
public class WorkbenchFragment extends Fragment {

    /** 顶部渐变三色（上→下）。 */
    private static final int[] HEADER_GRADIENT = {0xFF0E6FBF, 0xFF1565C0, 0xFF1E88E5};
    /** 头部下方页面背景色，弧形下摆用它盖住渐变形成弧线。 */
    private static final int PAGE_BG = 0xFFEEF1F5;
    /** 图标块淡蓝底色。 */
    private static final int ICON_BG = 0xFFE3F2FD;
    /** 分组竖条蓝。 */
    private static final int GROUP_BAR = 0xFF1565C0;
    /** 故障角标红。 */
    private static final int BADGE_RED = 0xFFD1242F;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Context ctx = getActivity();

        ScrollView scroll = new ScrollView(ctx);
        scroll.setBackgroundColor(PAGE_BG);

        LinearLayout root = new LinearLayout(ctx);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(PAGE_BG);
        scroll.addView(root, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT));

        // 1) 蓝色圆弧渐变头部（含标题与 4 个高频入口）
        root.addView(buildHeader(ctx));

        // 2) 分组宫格内容区
        LinearLayout content = new LinearLayout(ctx);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackgroundColor(PAGE_BG);
        content.setPadding(dp(12), dp(8), dp(12), dp(16));
        root.addView(content, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        DBHelper dbh = new DBHelper(ctx);
        int pending = dbh.getProcessingTicketCount();

        // 三级权限：管理员全部可见；负责人/员工隐藏「数据备份」「人员管理」
        boolean admin = DBHelper.ROLE_ADMIN.equals(DBHelper.getCurrentRole(ctx));

        // 总览（人员管理仅管理员可见）
        if (admin) {
            content.addView(buildGroup(ctx, "总览", new Cell[]{
                    new Cell("\uD83D\uDCCA", "仪表盘", "dashboard", 0),
                    new Cell("\uD83D\uDC65", "人员管理", "users", 0),
            }));
        } else {
            content.addView(buildGroup(ctx, "总览", new Cell[]{
                    new Cell("\uD83D\uDCCA", "仪表盘", "dashboard", 0),
            }));
        }

        // 台账管理（数据备份仅管理员可见）
        if (admin) {
            content.addView(buildGroup(ctx, "台账管理", new Cell[]{
                    new Cell("\uD83D\uDCCB", "设备台账", "devices", 0),
                    new Cell("🛠\uFE0F", "故障工单", "tickets", pending),
                    new Cell("\uD83D\uDCDD", "巡检记录", "inspections", 0),
                    new Cell("\uD83D\uDCBE", "数据备份", "data", 0),
            }));
        } else {
            content.addView(buildGroup(ctx, "台账管理", new Cell[]{
                    new Cell("\uD83D\uDCCB", "设备台账", "devices", 0),
                    new Cell("🛠\uFE0F", "故障工单", "tickets", pending),
                    new Cell("\uD83D\uDCDD", "巡检记录", "inspections", 0),
            }));
        }

        // 探测工具
        content.addView(buildGroup(ctx, "探测工具", new Cell[]{
                new Cell("\uD83D\uDCE1", "设备发现", "discover", 0),
                new Cell("🧰", "工程宝", "tools", 0),
        }));

        // 排障助手
        content.addView(buildGroup(ctx, "排障助手", new Cell[]{
                new Cell("🧭", "排障向导", "wizard", 0),
                new Cell("⚡", "速查卡", "quickref", 0),
                new Cell("\uD83D\uDCDA", "知识库", "kb", 0),
        }));

        return scroll;
    }

    // --------------------------------- 头部（渐变 + 弧 + 高频入口） ---------------------------------

    private View buildHeader(Context ctx) {
        HeaderView header = new HeaderView(ctx);
        // 头部高度随内容自适应（wrap_content），任何密度下标题行与入口行顺序排布不重叠。
        header.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // 标题行：标题 + 副标题（纯垂直顺序布局的第一段）
        LinearLayout titleRow = new LinearLayout(ctx);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(18), dp(18), dp(16), dp(8));

        LinearLayout titles = new LinearLayout(ctx);
        titles.setOrientation(LinearLayout.VERTICAL);
        titles.setPadding(dp(6), 0, 0, 0);

        TextView tvTitle = new TextView(ctx);
        tvTitle.setText("安防维修排查");
        tvTitle.setTextSize(21);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvSub = new TextView(ctx);
        tvSub.setText("楼宇智能化 · 运维工作台");
        tvSub.setTextSize(12);
        tvSub.setTextColor(0xFFCFE2FF);
        tvSub.setPadding(0, dp(3), 0, 0);

        titles.addView(tvTitle);
        titles.addView(tvSub);

        // 右上角圆形工具图标块
        TextView gear = new TextView(ctx);
        gear.setText("🔧");
        gear.setTextSize(20);
        gear.setGravity(Gravity.CENTER);
        GradientDrawable gearBg = roundDrawable(0x33FFFFFF, dp(22));
        gear.setBackground(gearBg);
        int gearSize = dp(44);
        gear.setLayoutParams(new LinearLayout.LayoutParams(gearSize, gearSize));
        // Bug 修复：绑定右上角齿轮按钮 → 打开「个人中心」（openProfile 在 MainActivity 中）
        gear.setOnClickListener(v -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openProfile();
            }
        });

        titleRow.addView(titles, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        titleRow.addView(gear);

        // 4 个高频入口横排（半透明白卡）：纯垂直顺序布局的第二段，
        // 仅保留小段顶部内边距形成间距，不再用固定 dp(86) 定位（避免与标题重叠）。
        LinearLayout entries = new LinearLayout(ctx);
        entries.setOrientation(LinearLayout.HORIZONTAL);
        entries.setPadding(dp(16), dp(10), dp(16), dp(16));
        entries.setWeightSum(4f);

        String[][] hot = {
                {"🛠️", "故障工单", "tickets"},
                {"📡", "设备发现", "discover"},
                {"🧰", "工程宝", "tools"},
                {"🧭", "排障向导", "wizard"},
        };
        for (String[] e : hot) {
            entries.addView(buildHotEntry(ctx, e[0], e[1], e[2]));
        }

        header.addView(titleRow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        header.addView(entries, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        return header;
    }

    private View buildHotEntry(Context ctx, String emoji, String label, String key) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(roundDrawable(0xCCFFFFFF, dp(14)));
        card.setPadding(dp(4), dp(12), dp(4), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(lp);

        TextView icon = new TextView(ctx);
        icon.setText(emoji);
        icon.setTextSize(22);
        icon.setGravity(Gravity.CENTER);

        TextView text = new TextView(ctx);
        text.setText(label);
        text.setTextSize(12);
        text.setTextColor(0xFF0B3D73);
        text.setGravity(Gravity.CENTER);
        text.setPadding(0, dp(6), 0, 0);

        card.addView(icon);
        card.addView(text);
        card.setOnClickListener(v -> routeTo(key));
        return card;
    }

    // --------------------------------- 分组 + 宫格 ---------------------------------

    private static class Cell {
        final String emoji;
        final String label;
        final String key;
        final int badge;

        Cell(String emoji, String label, String key, int badge) {
            this.emoji = emoji;
            this.label = label;
            this.key = key;
            this.badge = badge;
        }
    }

    private View buildGroup(Context ctx, String groupName, Cell[] cells) {
        LinearLayout group = new LinearLayout(ctx);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setPadding(0, dp(8), 0, dp(6));

        // 分组标题：左蓝竖条 + 粗体文字
        LinearLayout title = new LinearLayout(ctx);
        title.setOrientation(LinearLayout.HORIZONTAL);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(0, dp(4), 0, dp(10));

        View bar = new View(ctx);
        bar.setBackgroundColor(GROUP_BAR);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(4), dp(16));
        barLp.setMargins(0, 0, dp(8), 0);
        bar.setLayoutParams(barLp);

        TextView tv = new TextView(ctx);
        tv.setText(groupName);
        tv.setTextSize(15);
        tv.setTextColor(0xFF1F2328);
        tv.setTypeface(null, android.graphics.Typeface.BOLD);

        title.addView(bar);
        title.addView(tv);
        group.addView(title);

        // 4 列宫格，每行 4 个，最后一行不足左对齐
        final int COLS = 4;
        List<Cell> list = new ArrayList<>();
        for (Cell c : cells) {
            list.add(c);
        }
        int rows = (list.size() + COLS - 1) / COLS;
        for (int r = 0; r < rows; r++) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setWeightSum(COLS);
            for (int c = 0; c < COLS; c++) {
                int idx = r * COLS + c;
                if (idx < list.size()) {
                    row.addView(buildCell(ctx, list.get(idx)));
                } else {
                    // 占位空白，保持左对齐
                    View placeholder = new View(ctx);
                    LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                    plp.setMargins(dp(4), dp(4), dp(4), dp(4));
                    placeholder.setLayoutParams(plp);
                    row.addView(placeholder);
                }
            }
            group.addView(row);
        }
        return group;
    }

    private View buildCell(Context ctx, Cell cell) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER);
        card.setBackground(roundDrawable(0xFFFFFFFF, dp(16)));
        card.setElevation(dp(2));
        card.setPadding(dp(6), dp(14), dp(6), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(dp(4), dp(4), dp(4), dp(4));
        card.setLayoutParams(lp);

        // 图标块（淡蓝圆角底色 + emoji），右上角可选红色角标
        FrameLayout iconWrap = new FrameLayout(ctx);
        int iconBox = dp(52);
        iconWrap.setLayoutParams(new LinearLayout.LayoutParams(iconBox, iconBox));

        TextView icon = new TextView(ctx);
        icon.setText(cell.emoji);
        icon.setTextSize(24);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(roundDrawable(ICON_BG, dp(14)));
        icon.setLayoutParams(new FrameLayout.LayoutParams(iconBox, iconBox));

        iconWrap.addView(icon);

        if (cell.badge > 0) {
            TextView badge = new TextView(ctx);
            badge.setText(String.valueOf(cell.badge));
            badge.setTextSize(11);
            badge.setTextColor(0xFFFFFFFF);
            badge.setGravity(Gravity.CENTER);
            badge.setBackground(roundDrawable(BADGE_RED, dp(10)));
            int bSize = dp(20);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(bSize, bSize);
            blp.setMargins(dp(34), 0, 0, dp(34));
            badge.setLayoutParams(blp);
            iconWrap.addView(badge);
        }

        TextView label = new TextView(ctx);
        label.setText(cell.label);
        label.setTextSize(13);
        label.setTextColor(0xFF1F2328);
        label.setGravity(Gravity.CENTER);
        label.setPadding(0, dp(8), 0, 0);

        card.addView(iconWrap);
        card.addView(label);
        card.setOnClickListener(v -> routeTo(cell.key));
        return card;
    }

    /** 跳转到对应模块；dashboard 不在 MODULES 中，由 MainActivity 直接路由。 */
    private void routeTo(String key) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openModuleByKey(key);
        }
    }

    // --------------------------------- 工具方法 ---------------------------------

    /** 圆角矩形 drawable。 */
    private static GradientDrawable roundDrawable(int color, int radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radiusPx);
        return d;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    /**
     * 自定义头部视图：在 {@code onDraw} 中绘制蓝色三色垂直渐变，
     * 再用页面背景色覆盖底部「上凸弧」区域，形成弧形下摆（非直角切）。
     */
    private static class HeaderView extends LinearLayout {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arcPath = new Path();

        HeaderView(Context ctx) {
            super(ctx);
            setWillNotDraw(false);
            setOrientation(VERTICAL);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            if (w <= 0 || h <= 0) {
                return;
            }
            // 1) 蓝色三色垂直渐变
            LinearGradient grad = new LinearGradient(0, 0, 0, h,
                    HEADER_GRADIENT, null, Shader.TileMode.CLAMP);
            paint.setShader(grad);
            canvas.drawRect(0, 0, w, h, paint);

            // 2) 上凸弧下摆：底部用页面背景色盖出一条向上凸起的弧线（∩ 形）。
            //    边缘边界低、中心边界高，使渐变在中部向上收，背景色在中部上凸成弧。
            float yEdge = h * 0.90f;   // 两侧渐变截止高度（较低）
            float yCenter = h * 0.74f; // 中部渐变截止高度（较高 → 背景上凸）
            float ctrlY = 2 * yCenter - yEdge; // 二次贝塞尔控制点，使中点恰为 yCenter
            arcPath.reset();
            arcPath.moveTo(0, h);
            arcPath.lineTo(0, yEdge);
            arcPath.quadTo(w / 2f, ctrlY, w, yEdge);
            arcPath.lineTo(w, h);
            arcPath.close();
            paint.setShader(null);
            paint.setColor(PAGE_BG);
            canvas.drawPath(arcPath, paint);
        }
    }
}

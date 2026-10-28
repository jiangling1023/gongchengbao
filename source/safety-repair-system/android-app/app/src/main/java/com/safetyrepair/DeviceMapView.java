package com.safetyrepair;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 缺陷设备地图：轻量物理分布可视化（非真实 GIS 地图）。
 *
 * <p>按楼层分组，用 Canvas 绘制简化的楼栋 / 楼层平面图：
 * <ul>
 *   <li>每个楼层画一个矩形区域，区域内用圆点表示设备；</li>
 *   <li>正常设备灰 / 绿点，有关联「处理中」工单的设备画红色大点并标注编号 / 位置；</li>
 *   <li>顶部显示图例（正常 / 故障）；</li>
 *   <li>若全部设备无位置数据，优雅降级显示提示，不崩溃。</li>
 * </ul>
 * 仅用于让维护人员直观看到哪一层、哪个位置的设备坏了。
 */
public class DeviceMapView extends View {
    /** 单台设备的信息。 */
    public static class Item {
        public final String code;
        public final String location;
        public final String status;
        public final boolean faulty;

        public Item(String code, String location, String status, boolean faulty) {
            this.code = (code == null) ? "" : code;
            this.location = (location == null) ? "" : location;
            this.status = (status == null) ? "" : status;
            this.faulty = faulty;
        }
    }

    private List<Item> items = new ArrayList<>();

    public DeviceMapView(Context context) {
        super(context);
    }

    public DeviceMapView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /** 设置设备数据并刷新。 */
    public void setItems(List<Item> list) {
        items = (list == null) ? new ArrayList<Item>() : new ArrayList<Item>(list);
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = computeHeight(width);
        setMeasuredDimension(width, height);
    }

    private int computeHeight(int width) {
        List<Floor> floors = buildFloors();
        if (floors.isEmpty()) {
            return Math.max(120, dp(120));
        }
        int pad = dp(12);
        int legendH = dp(28);
        int floorHeaderH = dp(26);
        int cellH = dp(34);
        int cols = Math.max(1, (width - 2 * pad) / dp(54));
        int total = legendH + pad;
        for (Floor f : floors) {
            int rows = (int) Math.ceil((double) f.items.size() / cols);
            rows = Math.max(rows, 1);
            total += floorHeaderH + rows * cellH + pad;
        }
        return total + pad;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int pad = dp(12);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        List<Floor> floors = buildFloors();
        if (floors.isEmpty()) {
            p.setColor(Color.parseColor("#6b7280"));
            p.setTextSize(dp(14));
            p.setTextAlign(Paint.Align.CENTER);
            canvas.drawText("暂无位置数据，请在设备台账填写位置", w / 2f, getHeight() / 2f, p);
            p.setTextAlign(Paint.Align.LEFT);
            return;
        }

        // 图例
        int y = pad + dp(4);
        int dotR = dp(6);
        p.setColor(Color.parseColor("#1a7f37"));
        canvas.drawCircle(pad + dotR, y + dotR, dotR, p);
        p.setColor(Color.parseColor("#1f2328"));
        p.setTextSize(dp(13));
        canvas.drawText("正常", pad + dotR * 2 + dp(6), y + dotR + dp(4), p);
        int lx = pad + dp(70);
        p.setColor(Color.parseColor("#d1242f"));
        canvas.drawCircle(lx + dotR, y + dotR, dotR, p);
        p.setColor(Color.parseColor("#1f2328"));
        canvas.drawText("故障（处理中）", lx + dotR * 2 + dp(6), y + dotR + dp(4), p);

        int top = y + dp(20) + pad;
        int floorHeaderH = dp(26);
        int cellH = dp(34);
        int cols = Math.max(1, (w - 2 * pad) / dp(54));
        int cellW = (w - 2 * pad) / cols;

        for (Floor f : floors) {
            int count = f.items.size();
            int rows = (int) Math.ceil((double) count / cols);
            rows = Math.max(rows, 1);
            int blockH = floorHeaderH + rows * cellH;

            // 楼层区域背景 + 边框
            Paint bg = new Paint();
            bg.setColor(Color.parseColor("#f7f9fc"));
            bg.setStyle(Paint.Style.FILL);
            canvas.drawRect(pad, top, w - pad, top + blockH, bg);
            Paint border = new Paint();
            border.setColor(Color.parseColor("#d0d7de"));
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(1);
            canvas.drawRect(pad, top, w - pad, top + blockH, border);

            // 楼层标题（含台数）
            Paint title = new Paint(Paint.ANTI_ALIAS_FLAG);
            title.setColor(Color.parseColor("#1f2328"));
            title.setTextSize(dp(14));
            title.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText(f.name + "（" + count + "台）", pad + dp(8), top + dp(18), title);

            // 设备点
            for (int i = 0; i < count; i++) {
                Item it = f.items.get(i);
                int col = i % cols;
                int row = i / cols;
                int cx = pad + col * cellW + cellW / 2;
                int cy = top + floorHeaderH + row * cellH + cellH / 2;
                boolean online = "online".equals(it.status);
                if (it.faulty) {
                    Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    fp.setColor(Color.parseColor("#d1242f"));
                    canvas.drawCircle(cx, cy, dp(8), fp);
                    Paint lp = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lp.setColor(Color.parseColor("#d1242f"));
                    lp.setTextSize(dp(11));
                    lp.setTypeface(Typeface.DEFAULT_BOLD);
                    String label = it.code + (it.location.isEmpty() ? "" : " " + it.location);
                    canvas.drawText(truncate(label, lp, cellW - dp(16)), cx + dp(12), cy + dp(4), lp);
                } else {
                    Paint np = new Paint(Paint.ANTI_ALIAS_FLAG);
                    np.setColor(online ? Color.parseColor("#1a7f37") : Color.parseColor("#9aa4b2"));
                    canvas.drawCircle(cx, cy, dp(5), np);
                }
            }
            top += blockH + pad;
        }
    }

    /** 按楼层分组（使用 LocationUtil 推断楼层）。 */
    private List<Floor> buildFloors() {
        Map<String, List<Item>> groups = new LinkedHashMap<>();
        for (Item it : items) {
            String floor = LocationUtil.extractFloor(it.location);
            String key = floor.isEmpty() ? "未分层" : floor;
            List<Item> list = groups.get(key);
            if (list == null) {
                list = new ArrayList<>();
                groups.put(key, list);
            }
            list.add(it);
        }
        List<Floor> floors = new ArrayList<>();
        for (Map.Entry<String, List<Item>> e : groups.entrySet()) {
            floors.add(new Floor(e.getKey(), e.getValue()));
        }
        Collections.sort(floors, new Comparator<Floor>() {
            @Override
            public int compare(Floor a, Floor b) {
                return Integer.compare(LocationUtil.floorOrder(a.name), LocationUtil.floorOrder(b.name));
            }
        });
        return floors;
    }

    private static class Floor {
        final String name;
        final List<Item> items;

        Floor(String name, List<Item> items) {
            this.name = name;
            this.items = items;
        }
    }

    /** 按最大宽度截断文本并加省略号。 */
    private String truncate(String s, Paint p, float maxW) {
        if (s == null) {
            s = "";
        }
        if (p.measureText(s) <= maxW) {
            return s;
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() > 1 && p.measureText(sb.toString() + "…") > maxW) {
            sb.deleteCharAt(sb.length() - 1);
        }
        return sb.toString() + "…";
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}

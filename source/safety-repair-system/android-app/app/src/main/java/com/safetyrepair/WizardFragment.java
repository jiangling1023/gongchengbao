package com.safetyrepair;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;

/**
 * 交互式排障向导：把"故障排查决策树"原生化。
 * 摄像头/门禁双入口，点击判断(是/否)或选择现象逐级推进，直达处理动作。
 */
public class WizardFragment extends Fragment {
    private ContentData.WNode[] nodes;
    private LinearLayout content;
    private TextView tvHint;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        tvHint = new TextView(getActivity());
        tvHint.setText("选择系统，开始交互式排障（看现象 → 判断 → 直达处理）");
        tvHint.setTextSize(13);
        tvHint.setTextColor(Color.parseColor("#6b7280"));
        tvHint.setPadding(4, 4, 4, 10);
        root.addView(tvHint);

        LinearLayout sysRow = new LinearLayout(getActivity());
        sysRow.setOrientation(LinearLayout.HORIZONTAL);
        sysRow.addView(sysBtn("📷 摄像头", "#1565c0", v -> start("camera")));
        sysRow.addView(sysBtn("🚪 门禁", "#e65100", v -> start("door")));
        root.addView(sysRow);

        content = new LinearLayout(getActivity());
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        sv.addView(root);
        return sv;
    }

    private void start(String sys) {
        nodes = sys.equals("camera") ? ContentData.CAMERA_WIZARD : ContentData.DOOR_WIZARD;
        tvHint.setText(sys.equals("camera") ? "海康威视摄像头 · 排障向导" : "立林门禁 · 排障向导");
        showNode(sys.equals("camera") ? "c_root" : "d_root");
    }

    private ContentData.WNode find(String id) {
        if (nodes == null) return null;
        for (ContentData.WNode n : nodes) if (n.id.equals(id)) return n;
        return null;
    }

    private void showNode(String id) {
        ContentData.WNode n = find(id);
        content.removeAllViews();
        if (n == null) return;

        if (n.kind.equals("branch")) {
            content.addView(title(n.text, "#222222"));
            for (int i = 0; i < n.opts.length; i++) {
                final String g = n.gotos[i];
                content.addView(btn(n.opts[i], "#1f6feb", v -> showNode(g)));
            }
        } else if (n.kind.equals("judge")) {
            content.addView(title(n.text, "#c62828"));
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.addView(btnFlex("✅ 是", "#1a7f37", v -> showNode(n.yes)));
            row.addView(btnFlex("❌ 否", "#d1242f", v -> showNode(n.no)));
            content.addView(row);
        } else { // fix
            content.addView(fixBox(n.text, n.tag));
            LinearLayout row = new LinearLayout(getActivity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            if (n.next != null && !n.next.isEmpty())
                row.addView(btnFlex("下一步 ▶", "#1f6feb", v -> showNode(n.next)));
            row.addView(btnFlex("↺ 重选系统", "#6b7280", v -> {
                content.removeAllViews();
                tvHint.setText("选择系统，开始交互式排障（看现象 → 判断 → 直达处理）");
            }));
            content.addView(row);
            if (n.tag != null) {
                TextView red = new TextView(getActivity());
                red.setText("⚠ 维修红线：门禁换锁 / 断电开锁测试 / 权限批量恢复须登记+变更窗口+备份原配置；临时旁路须专人值守、修复即恢复。");
                red.setTextColor(Color.parseColor("#d1242f"));
                red.setTextSize(12);
                red.setPadding(12, 10, 12, 10);
                red.setBackgroundColor(Color.parseColor("#fff3f3"));
                LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
                p.setMargins(0, 8, 0, 0);
                red.setLayoutParams(p);
                content.addView(red);
            }
        }
    }

    private TextView title(String text, String color) {
        TextView t = new TextView(getActivity());
        t.setText(text);
        t.setTextSize(16);
        t.setTextColor(Color.parseColor(color));
        t.setTypeface(null, Typeface.BOLD);
        t.setPadding(10, 10, 10, 6);
        return t;
    }

    private TextView fixBox(String text, String tag) {
        TextView t = new TextView(getActivity());
        t.setText((tag != null ? ("【" + tag + "】\n") : "") + text);
        t.setTextSize(15);
        t.setTextColor(Color.parseColor("#1a7f37"));
        t.setPadding(14, 14, 14, 14);
        t.setBackgroundColor(Color.parseColor("#f1f8f1"));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 6, 0, 6);
        t.setLayoutParams(p);
        return t;
    }

    private Button btn(String text, String color, View.OnClickListener l) {
        Button b = new Button(getActivity());
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 6, 0, 6);
        b.setLayoutParams(p);
        return b;
    }

    private Button btnFlex(String text, String color, View.OnClickListener l) {
        Button b = new Button(getActivity());
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, 6, 8, 6);
        b.setLayoutParams(p);
        return b;
    }

    private Button sysBtn(String text, String color, View.OnClickListener l) {
        Button b = new Button(getActivity());
        b.setText(text);
        b.setTextSize(16);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, 0, 8, 12);
        b.setLayoutParams(p);
        return b;
    }
}

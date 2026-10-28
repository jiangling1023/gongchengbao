package com.safetyrepair;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;

/**
 * 知识库：排障方法论、红线、口诀（只读）。
 *
 * <p>增强：防御性编码，遍历 KB 时校验 item 长度，card() 对 body 做 null/空保护。</p>
 */
public class KBFragment extends Fragment {
    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        // 终极防线：onCreateView 时 LayoutInflater 自带 Context 必非 null；
        // 整段包 try/catch，任何意外都不让 App 闪退，而是显示友好提示。
        final android.content.Context ctx = inf.getContext();
        try {
            ScrollView sv = new ScrollView(ctx);
            LinearLayout root = new LinearLayout(ctx);
            root.setOrientation(LinearLayout.VERTICAL);
            root.setPadding(12, 12, 12, 12);

            // 知识库条目：遍历时校验长度，避免数组越界
            if (ContentData.KB != null) {
                for (String[] item : ContentData.KB) {
                    if (item == null || item.length < 2) {
                        continue;
                    }
                    root.addView(card(ctx, item[0], item[1], "#1f2328", "#f7f9fc"));
                }
            }

            root.addView(card(ctx, "摄像头维修红线", safe(ContentData.REDLINE_CAM), "#d1242f", "#fff3f3"));
            root.addView(card(ctx, "门禁维修红线", safe(ContentData.REDLINE_DOOR), "#d1242f", "#fff3f3"));
            root.addView(card(ctx, "快速口诀", safe(ContentData.MANTRA), "#1a7f37", "#f1f8f1"));

            sv.addView(root);
            return sv;
        } catch (Throwable e) {
            // 绝不闪退：返回带错误提示的滚动视图
            TextView tv = new TextView(ctx);
            tv.setText("知识库加载异常，请返回重试。\n(" + (e != null ? e.getMessage() : "unknown") + ")");
            tv.setTextSize(14);
            tv.setPadding(24, 24, 24, 24);
            ScrollView sv = new ScrollView(ctx);
            sv.addView(tv);
            return sv;
        }
    }

    private LinearLayout card(android.content.Context ctx, String title, String body, String titleColor, String bg) {
        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(Color.parseColor(bg));
        card.setPadding(14, 12, 14, 12);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cp.setMargins(0, 0, 0, 10);
        card.setLayoutParams(cp);

        TextView t = new TextView(ctx);
        t.setText(title == null ? "" : title);
        t.setTextSize(15);
        t.setTypeface(null, Typeface.BOLD);
        t.setTextColor(Color.parseColor(titleColor));
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);

        TextView b = new TextView(ctx);
        // body 空串也安全
        b.setText(body == null || body.isEmpty() ? "（暂无内容）" : body);
        b.setTextSize(13);
        b.setTextColor(Color.parseColor("#1f2328"));
        b.setPadding(0, 6, 0, 0);

        card.addView(t);
        card.addView(b);
        return card;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private android.content.Context getActivitySafe() {
        android.app.Activity a = getActivity();
        return a != null ? a : getContext();
    }
}

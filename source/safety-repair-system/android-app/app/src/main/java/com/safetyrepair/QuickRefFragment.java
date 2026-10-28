package com.safetyrepair;

import android.app.Fragment;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import android.view.ViewGroup.LayoutParams;

import java.util.ArrayList;
import java.util.List;

/**
 * 5 分钟应急速查卡（原生化、可搜索）。
 * 摄像头 9 条 + 门禁 8 条：现象 → 先做(前2步) → 若未恢复。
 */
public class QuickRefFragment extends Fragment {
    private String sys = "camera";
    private String[][] data;
    private List<String[]> filtered = new ArrayList<>();
    private LinearLayout listContainer;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        LinearLayout sysRow = new LinearLayout(getActivity());
        sysRow.setOrientation(LinearLayout.HORIZONTAL);
        sysRow.addView(sysBtn("📷 摄像头", "#1565c0", v -> { sys = "camera"; load(); }));
        sysRow.addView(sysBtn("🚪 门禁", "#e65100", v -> { sys = "door"; load(); }));
        root.addView(sysRow);

        EditText search = new EditText(getActivity());
        search.setHint("搜索现象关键词，如：离线 / 刷卡 / 录像");
        search.setTextSize(14);
        search.setPadding(12, 10, 12, 10);
        search.setBackgroundColor(Color.WHITE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sp.setMargins(0, 10, 0, 6);
        search.setLayoutParams(sp);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence a, int b, int c, int d) { }
            public void onTextChanged(CharSequence a, int b, int c, int d) { filter(a.toString()); }
            public void afterTextChanged(Editable e) { }
        });
        root.addView(search);

        listContainer = new LinearLayout(getActivity());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);

        sv.addView(root);
        load();
        return sv;
    }

    private void load() {
        data = sys.equals("camera") ? ContentData.CAMERA_QUICK : ContentData.DOOR_QUICK;
        filter("");
    }

    private void filter(String kw) {
        filtered.clear();
        kw = kw.trim();
        for (String[] r : data) {
            if (kw.isEmpty() || (r[0] + r[1] + r[2]).contains(kw)) filtered.add(r);
        }
        render();
    }

    private void render() {
        listContainer.removeAllViews();
        for (String[] r : filtered) {
            LinearLayout card = new LinearLayout(getActivity());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(14, 12, 14, 12);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);

            TextView t1 = new TextView(getActivity());
            t1.setText("● " + r[0]);
            t1.setTextSize(15);
            t1.setTypeface(null, Typeface.BOLD);
            t1.setTextColor(Color.parseColor("#1f2328"));
            TextView t2 = new TextView(getActivity());
            t2.setText("先做：" + r[1]);
            t2.setTextSize(13);
            t2.setTextColor(Color.parseColor("#1f6feb"));
            t2.setPadding(0, 6, 0, 0);
            TextView t3 = new TextView(getActivity());
            t3.setText("未恢复：" + r[2]);
            t3.setTextSize(13);
            t3.setTextColor(Color.parseColor("#6b7280"));
            t3.setPadding(0, 4, 0, 0);
            card.addView(t1); card.addView(t2); card.addView(t3);
            listContainer.addView(card);
        }
        if (filtered.isEmpty()) {
            TextView empty = new TextView(getActivity());
            empty.setText("无匹配条目");
            empty.setTextColor(Color.parseColor("#6b7280"));
            empty.setPadding(0, 20, 0, 20);
            listContainer.addView(empty);
        }
    }

    private android.widget.Button sysBtn(String text, String color, View.OnClickListener l) {
        android.widget.Button b = new android.widget.Button(getActivity());
        b.setText(text);
        b.setTextSize(15);
        b.setTextColor(Color.WHITE);
        b.setBackgroundColor(Color.parseColor(color));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        p.setMargins(0, 0, 8, 0);
        b.setLayoutParams(p);
        return b;
    }
}

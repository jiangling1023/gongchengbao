package com.safetyrepair;

import android.app.AlertDialog;
import android.app.Fragment;
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

/** 巡检管理：日/周/月巡检记录增删改查。 */
public class InspectionsFragment extends Fragment {
    private DBHelper dbh;
    private SQLiteDatabase db;
    private LinearLayout listContainer;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle s) {
        dbh = new DBHelper(getActivity());
        db = dbh.getWritableDatabase();
        ScrollView sv = new ScrollView(getActivity());
        LinearLayout root = new LinearLayout(getActivity());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);

        LinearLayout top = new LinearLayout(getActivity());
        top.setOrientation(LinearLayout.HORIZONTAL);
        TextView h = new TextView(getActivity());
        h.setText("巡检管理");
        h.setTextSize(18);
        h.setTypeface(null, Typeface.BOLD);
        h.setTextColor(Color.parseColor("#1f2328"));
        h.setLayoutParams(new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        Button add = new Button(getActivity());
        add.setText("+ 新增");
        add.setTextColor(Color.WHITE);
        add.setBackgroundColor(Color.parseColor("#1f6feb"));
        top.addView(h);
        top.addView(add);
        root.addView(top);

        listContainer = new LinearLayout(getActivity());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        root.addView(listContainer);
        sv.addView(root);

        add.setOnClickListener(v -> form(null));
        load();
        return sv;
    }

    private void load() {
        listContainer.removeAllViews();
        Cursor cur = db.rawQuery("SELECT id,title,date,result,note FROM inspections ORDER BY id DESC", null);
        while (cur.moveToNext()) {
            final long id = cur.getLong(0);
            String title = cur.getString(1);
            String date = cur.getString(2);
            String result = cur.getString(3);
            String note = cur.getString(4);
            LinearLayout card = new LinearLayout(getActivity());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.WHITE);
            card.setPadding(14, 12, 14, 12);
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            cp.setMargins(0, 0, 0, 10);
            card.setLayoutParams(cp);
            TextView t1 = new TextView(getActivity());
            t1.setText(title + "  ｜ " + date);
            t1.setTextSize(15);
            t1.setTypeface(null, Typeface.BOLD);
            t1.setTextColor(Color.parseColor("#1f2328"));
            TextView t2 = new TextView(getActivity());
            String rlabel = "正常".equals(result) ? "正常" : ("异常".equals(result) ? "异常" : "待复测");
            t2.setText("结果：" + rlabel + (note != null && !note.isEmpty() ? " ｜ " + note : ""));
            t2.setTextSize(13);
            t2.setTextColor("异常".equals(result) ? Color.parseColor("#d1242f") : Color.parseColor("#6b7280"));
            t2.setPadding(0, 4, 0, 0);
            LinearLayout btns = new LinearLayout(getActivity());
            btns.setOrientation(LinearLayout.HORIZONTAL);
            Button edit = new Button(getActivity());
            edit.setText("编辑");
            edit.setTextSize(13);
            edit.setBackgroundColor(Color.parseColor("#eef2f7"));
            edit.setOnClickListener(v -> form(id));
            Button del = new Button(getActivity());
            del.setText("删除");
            del.setTextSize(13);
            del.setTextColor(Color.WHITE);
            del.setBackgroundColor(Color.parseColor("#d1242f"));
            del.setOnClickListener(v -> { db.delete("inspections", "id=?", new String[]{String.valueOf(id)}); load(); });
            btns.addView(edit);
            btns.addView(del);
            card.addView(t1);
            card.addView(t2);
            card.addView(btns);
            listContainer.addView(card);
        }
        cur.close();
    }

    private void form(final Long id) {
        Cursor ex = id != null ? db.rawQuery("SELECT title,date,result,note FROM inspections WHERE id=?", new String[]{String.valueOf(id)}) : null;
        String[] v = new String[4];
        if (ex != null && ex.moveToFirst()) { for (int i = 0; i < 4; i++) v[i] = ex.getString(i); ex.close(); }
        ScrollView fs = new ScrollView(getActivity());
        LinearLayout fl = new LinearLayout(getActivity());
        fl.setOrientation(LinearLayout.VERTICAL);
        fl.setPadding(20, 20, 20, 20);
        final EditText title = e("巡检标题", v[0]);
        final EditText date = e("日期(YYYY-MM-DD)", v[1]);
        final Spinner result = new Spinner(getActivity());
        String[] rs = {"normal", "abnormal", "recheck"};
        result.setAdapter(new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, new String[]{"正常", "异常", "待复测"}));
        if (v[2] != null) for (int i = 0; i < 3; i++) if (v[2].equals(rs[i])) result.setSelection(i);
        final EditText note = e("备注", v[3]);
        fl.addView(label("标题")); fl.addView(title);
        fl.addView(label("日期")); fl.addView(date);
        fl.addView(label("结果")); fl.addView(result);
        fl.addView(label("备注")); fl.addView(note);
        fs.addView(fl);

        AlertDialog.Builder b = new AlertDialog.Builder(getActivity());
        b.setTitle(id == null ? "新增巡检" : "编辑巡检");
        b.setView(fs);
        b.setPositiveButton("保存", (d, w) -> {
            int ri = result.getSelectedItemPosition();
            if (id == null) db.execSQL("INSERT INTO inspections(title,date,result,note) VALUES(?,?,?,?)",
                    new Object[]{title.getText().toString(), date.getText().toString(), rs[ri], note.getText().toString()});
            else db.execSQL("UPDATE inspections SET title=?,date=?,result=?,note=? WHERE id=?",
                    new Object[]{title.getText().toString(), date.getText().toString(), rs[ri], note.getText().toString(), id});
            load();
        });
        b.setNegativeButton("取消", null);
        b.show();
    }

    private EditText e(String hint, String val) {
        EditText et = new EditText(getActivity());
        et.setHint(hint);
        if (val != null) et.setText(val);
        return et;
    }

    private TextView label(String t) {
        TextView tv = new TextView(getActivity());
        tv.setText(t);
        tv.setTextSize(12);
        tv.setTextColor(Color.parseColor("#6b7280"));
        tv.setPadding(0, 10, 0, 2);
        return tv;
    }
}

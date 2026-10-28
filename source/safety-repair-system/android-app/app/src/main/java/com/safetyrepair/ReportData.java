package com.safetyrepair;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * 报表数据构造：从 SQLite 抽取设备 / 工单列表，供 {@link Exporter} 导出使用。
 * 集中管理行列定义，避免在各 Fragment 中重复。
 */
public final class ReportData {
    private ReportData() {
        // 工具类，禁止实例化
    }

    /** 设备导出表头：编号 / 类型 / 品牌 / 型号 / IP / 状态 / 位置 / 父级 / 备注。 */
    public static String[] deviceHeaders() {
        return new String[]{"编号", "类型", "品牌", "型号", "IP", "状态", "位置", "父级", "备注"};
    }

    public static List<String[]> deviceRows(SQLiteDatabase db, DBHelper dbh) {
        List<String[]> out = new ArrayList<>();
        if (db == null) {
            return out;
        }
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT code,type,brand,model,ip,status,location,parent_id,note"
                    + " FROM devices ORDER BY id ASC", null);
            while (cur != null && cur.moveToNext()) {
                String code = nv(cur, 0);
                String type = nv(cur, 1);
                String brand = nv(cur, 2);
                String model = nv(cur, 3);
                String ip = nv(cur, 4);
                String status = nv(cur, 5);
                String loc = nv(cur, 6);
                long pid = cur.getLong(7);
                String parent = (dbh != null && pid > 0) ? dbh.getDeviceCode(pid) : "";
                String note = nv(cur, 8);
                out.add(new String[]{code, type, brand, model, ip, status, loc, parent, note});
            }
        } catch (Exception e) {
            // 忽略读取异常，返回已收集的数据
        } finally {
            closeQuietly(cur);
        }
        return out;
    }

    /** 工单导出表头：单号 / 类型 / 级别 / 状态 / 设备 / 描述 / 时间。 */
    public static String[] ticketHeaders() {
        return new String[]{"单号", "类型", "级别", "状态", "设备", "描述", "时间"};
    }

    public static List<String[]> ticketRows(SQLiteDatabase db) {
        List<String[]> out = new ArrayList<>();
        if (db == null) {
            return out;
        }
        Cursor cur = null;
        try {
            cur = db.rawQuery("SELECT code,ftype,level,status,device,desc,created"
                    + " FROM tickets ORDER BY id ASC", null);
            while (cur != null && cur.moveToNext()) {
                out.add(new String[]{nv(cur, 0), nv(cur, 1), nv(cur, 2), nv(cur, 3), nv(cur, 4), nv(cur, 5), nv(cur, 6)});
            }
        } catch (Exception e) {
            // 忽略读取异常，返回已收集的数据
        } finally {
            closeQuietly(cur);
        }
        return out;
    }

    private static String nv(Cursor c, int i) {
        try {
            String v = c.getString(i);
            return (v == null) ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    private static void closeQuietly(Cursor c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
                // 忽略关闭异常
            }
        }
    }
}

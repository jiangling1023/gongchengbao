package com.safetyrepair;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 本地持久化：设备台账、故障工单、巡检记录、人员账号(权限体系)。
 * 纯 framework SQLite，无额外依赖。
 *
 * <p>数据库版本迁移：VER 从 1 升到 2，新增设备编号(code)/父级(parent_id)，
 * 以及工单关联设备(device_id)。旧库通过 {@link #onUpgrade} 安全 ALTER 加列并回填种子编码，
 * 保证升级时旧库不崩溃。</p>
 *
 * <p>VER 3 → 4（权限体系）：新增 {@code users} 表（账号/密码哈希/角色/状态），
 * 并为 {@code tickets} 增加 {@code assigned} 列（指派给谁）。密码使用 SHA-256 哈希存储，
 * 不存明文。会话通过 {@link SharedPreferences} 保存当前登录用户。</p>
 */
public class DBHelper extends SQLiteOpenHelper {
    private static final String DB = "safety_repair.db";
    private static final int VER = 4;

    /** 会话 SharedPreferences 文件与键。 */
    private static final String SESSION_PREF = "safety_repair_session";
    private static final String K_ID = "id";
    private static final String K_USERNAME = "username";
    private static final String K_NAME = "name";
    private static final String K_ROLE = "role";

    /** 内置管理员账号与初始密码。 */
    public static final String ADMIN_USERNAME = "admin";
    private static final String ADMIN_PASSWORD = "Admin@2026";

    /** 角色常量。 */
    public static final String ROLE_ADMIN = "admin";
    public static final String ROLE_SUPERVISOR = "supervisor";
    public static final String ROLE_WORKER = "worker";

    public DBHelper(Context c) {
        super(c, DB, null, VER);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE devices("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "code TEXT,"
                + "parent_id INTEGER DEFAULT 0,"
                + "type TEXT, brand TEXT, model TEXT, ip TEXT, status TEXT,"
                + "location TEXT, building TEXT DEFAULT '', floor TEXT DEFAULT '', note TEXT, created TEXT)");
        db.execSQL("CREATE TABLE tickets("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "code TEXT, device_id INTEGER DEFAULT 0, device TEXT, title TEXT, ftype TEXT, level TEXT, status TEXT,"
                + "description TEXT, solution TEXT, created TEXT, closed TEXT, assigned TEXT DEFAULT '')");
        db.execSQL("CREATE TABLE inspections("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "title TEXT, date TEXT, result TEXT, note TEXT)");

        createUsersTable(db);
        seedAdmin(db);

        seedDevices(db);
        seedTickets(db);
        backfillBuildingFloor(db);
    }

    // -------------------------- users 表（权限体系） --------------------------

    private void createUsersTable(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS users("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                + "username TEXT UNIQUE,"
                + "pass_hash TEXT,"
                + "name TEXT,"
                + "role TEXT,"
                + "phone TEXT,"
                + "status TEXT,"
                + "created TEXT)");
    }

    /** 首次安装 / 旧库升级时确保内置管理员存在（SHA-256 哈希入库）。 */
    private void seedAdmin(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT count(*) FROM users WHERE username=?", new String[]{ADMIN_USERNAME});
            if (c.moveToFirst() && c.getInt(0) > 0) {
                return; // 已存在
            }
        } catch (Exception e) {
            // 表可能不存在，忽略后尝试插入
        } finally {
            closeQuietly(c);
        }
        try {
            db.execSQL("INSERT INTO users(username,pass_hash,name,role,phone,status,created)"
                            + " VALUES(?,?,?,?,?,?,date('now'))",
                    new Object[]{ADMIN_USERNAME, hashPassword(ADMIN_PASSWORD), "系统管理员",
                            ROLE_ADMIN, "", "active"});
        } catch (Exception ignore) {
            // 唯一约束冲突等，忽略
        }
    }

    // -------------------------- 种子数据（含编码与父子关系） --------------------------

    private void seedDevices(SQLiteDatabase db) {
        // 立林四门控制器(S003) 作为父节点，读卡器(S004) 挂其下。
        insertDevice(db, "S001", 0, "camera", "海康威视", "DS-2CD3346", "10.10.10.21",
                "online", "1F大堂东侧", "2.8mm枪机", "2026-01-01");
        insertDevice(db, "S002", 0, "camera", "海康威视", "DS-2CD3T46", "10.10.10.22",
                "online", "2F电梯厅", "4mm半球", "2026-01-01");
        insertDevice(db, "S003", 0, "door", "立林", "L-A1四门控制器", "10.10.20.10",
                "online", "1F弱电间", "TCP/IP通讯", "2026-01-01");
        long ctrlId = getDeviceIdByCode(db, "S003");
        insertDevice(db, "S004", ctrlId, "door", "立林", "读卡器IC", "-",
                "online", "1F大门", "韦根34", "2026-01-01");
        insertDevice(db, "S005", 0, "camera", "大华", "DH-IPC-HFW", "10.10.10.31",
                "unknown", "3F走廊", "待核IP", "2026-01-02");
    }

    private void insertDevice(SQLiteDatabase db, String code, long parentId, String type,
                              String brand, String model, String ip, String status,
                              String location, String note, String created) {
        db.execSQL("INSERT INTO devices(code,parent_id,type,brand,model,ip,status,location,note,created)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?)",
                new Object[]{code, parentId, type, brand, model, ip, status, location, note, created});
    }

    private void seedTickets(SQLiteDatabase db) {
        // 按 编号 回填 device_id，使工单与设备联动。
        long d1 = getDeviceIdByCode(db, "S001");
        long d2 = getDeviceIdByCode(db, "S004");
        db.execSQL("INSERT INTO tickets(code,device_id,device,title,ftype,level,status,description,solution,created,closed,assigned)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{"WX-20251001", d1, "1F大堂东侧-海康DS-2CD3346", "设备离线", "网络", "P2",
                        "closed", "ping不通，PoE端口down", "更换网线水晶头，端口恢复", "2025-10-01", "2025-10-01", ""});
        db.execSQL("INSERT INTO tickets(code,device_id,device,title,ftype,level,status,description,solution,created,closed,assigned)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{"WX-20251002", d2, "1F大门-立林读卡器", "刷卡不开门", "权限", "P1",
                        "closed", "卡片过期未续", "平台延期并补卡", "2025-10-02", "2025-10-02", ""});
        // 演示缺陷设备地图：3F 大华摄像头存在「处理中」工单 -> 红色大点。
        long d5 = getDeviceIdByCode(db, "S005");
        db.execSQL("INSERT INTO tickets(code,device_id,device,title,ftype,level,status,description,solution,created,closed,assigned)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
                new Object[]{"WX-20251003", d5, "3F走廊-大华DH-IPC-HFW", "画面卡顿", "硬件", "P2",
                        "processing", "夜间红外画面卡顿，疑似电源老化", "待更换电源适配器", "2025-10-03", null, ""});
    }

    // -------------------------- 安全迁移（旧库不崩） --------------------------

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
        // 逐列 ALTER，列已存在时忽略；保证旧库升级不抛异常。
        addColumnIfNotExists(db, "devices", "code", "TEXT");
        addColumnIfNotExists(db, "devices", "parent_id", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "tickets", "device_id", "INTEGER DEFAULT 0");
        addColumnIfNotExists(db, "devices", "building", "TEXT DEFAULT ''");
        addColumnIfNotExists(db, "devices", "floor", "TEXT DEFAULT ''");

        if (oldV < 2) {
            backfillDeviceCodes(db);
            backfillReaderParent(db);
        }
        if (oldV < 3) {
            backfillBuildingFloor(db);
        }
        if (oldV < 4) {
            createUsersTable(db);
            seedAdmin(db);
            addColumnIfNotExists(db, "tickets", "assigned", "TEXT DEFAULT ''");
        }
    }

    private void addColumnIfNotExists(SQLiteDatabase db, String table, String col, String type) {
        Cursor c = null;
        boolean exists = false;
        try {
            c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
            while (c.moveToNext()) {
                String name = c.getString(1);
                if (col.equalsIgnoreCase(name)) {
                    exists = true;
                    break;
                }
            }
        } catch (Exception e) {
            exists = false;
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignore) {
                    // 忽略关闭异常
                }
            }
        }
        if (!exists) {
            try {
                db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + col + " " + type);
            } catch (Exception e) {
                // 列已存在或其它错误，忽略以保证旧库不崩
            }
        }
    }

    /** 为缺失编码的旧设备按 id 生成 S001 形式编号。 */
    private void backfillDeviceCodes(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id, code FROM devices", null);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String code = c.getString(1);
                if (code == null || code.trim().isEmpty()) {
                    db.execSQL("UPDATE devices SET code=? WHERE id=?",
                            new Object[]{"S" + String.format("%03d", id), id});
                }
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            if (c != null) {
                try {
                    c.close();
                } catch (Exception ignore) {
                    // 忽略
                }
            }
        }
    }

    /** 将读卡器挂到立林控制器下，建立父子关系。 */
    private void backfillReaderParent(SQLiteDatabase db) {
        Cursor ctrl = null;
        Cursor rdr = null;
        try {
            ctrl = db.rawQuery("SELECT id FROM devices WHERE brand=? AND model LIKE ?",
                    new String[]{"立林", "%控制器%"});
            long ctrlId = -1;
            if (ctrl.moveToFirst()) {
                ctrlId = ctrl.getLong(0);
            }
            rdr = db.rawQuery("SELECT id FROM devices WHERE model LIKE ?", new String[]{"%读卡器%"});
            while (rdr.moveToNext()) {
                long rid = rdr.getLong(0);
                if (ctrlId > 0) {
                    db.execSQL("UPDATE devices SET parent_id=? WHERE id=?", new Object[]{ctrlId, rid});
                }
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(ctrl);
            closeQuietly(rdr);
        }
    }

    /** 从 location 解析楼栋/楼层并回填，使地图与分组有结构化数据可用。 */
    private void backfillBuildingFloor(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id, location FROM devices", null);
            while (c.moveToNext()) {
                long id = c.getLong(0);
                String loc = c.getString(1);
                String building = LocationUtil.extractBuilding(loc);
                String floor = LocationUtil.extractFloor(loc);
                db.execSQL("UPDATE devices SET building=?, floor=? WHERE id=?",
                        new Object[]{building, floor, id});
            }
        } catch (Exception e) {
            // 忽略解析/回填异常，保证不阻断初始化
        } finally {
            closeQuietly(c);
        }
    }

    /**
     * 统计「处理中」(status='processing') 工单数量，用于工作台红色角标。
     * 任何异常安全降级为 0。
     */
    public int getProcessingTicketCount() {
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return 0;
        }
        Cursor c = null;
        int n = 0;
        try {
            c = db.rawQuery("SELECT count(*) FROM tickets WHERE status='processing'", null);
            if (c.moveToFirst()) {
                n = c.getInt(0);
            }
        } catch (Exception e) {
            // 表缺失 / 查询异常，安全降级为 0
        } finally {
            closeQuietly(c);
        }
        return n;
    }

    /**
     * 统计「处理中」且指派给指定用户的工单数量，用于员工工作台红色角标。
     * 任何异常安全降级为 0。
     */
    public int getProcessingTicketCountForUser(String username) {
        if (username == null || username.trim().isEmpty()) {
            return 0;
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return 0;
        }
        Cursor c = null;
        int n = 0;
        try {
            c = db.rawQuery("SELECT count(*) FROM tickets WHERE status='processing' AND (assigned=? OR assigned=?)",
                    new String[]{username, username});
            if (c.moveToFirst()) {
                n = c.getInt(0);
            }
        } catch (Exception e) {
            // 表缺失 / 查询异常，安全降级为 0
        } finally {
            closeQuietly(c);
        }
        return n;
    }

    // -------------------------- 运行时查询辅助 --------------------------

    /** 查询「故障设备」集合：存在 processing 工单且已关联 device_id 的设备。 */
    public Set<Long> getFaultDeviceIds() {
        Set<Long> set = new HashSet<>();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return set;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT DISTINCT device_id FROM tickets"
                    + " WHERE status='processing' AND device_id > 0", null);
            while (c.moveToNext()) {
                set.add(c.getLong(0));
            }
        } catch (Exception e) {
            // 表缺失时安全降级
        } finally {
            closeQuietly(c);
        }
        return set;
    }

    /**
     * 安全查询全部设备（用于列表展示）。
     *
     * <p>内部全程 try/catch：任何异常（版本迁移、连接失效、表缺失等）均安全降级为「空列表」，
     * 绝不向上抛出，避免调用方裸 rawQuery 时崩溃并误报「读取设备失败」。</p>
     *
     * <p>每行按以下固定顺序存入 {@code String[]}（供 Fragment 按索引取）：
     * 0=id, 1=code, 2=type, 3=brand, 4=model, 5=ip, 6=status, 7=location, 8=parent_id。</p>
     */
    public List<String[]> queryAllDevices() {
        List<String[]> rows = new ArrayList<>();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return rows;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,code,type,brand,model,ip,status,location,parent_id"
                    + " FROM devices ORDER BY id DESC", null);
            while (c != null && c.moveToNext()) {
                String[] r = new String[9];
                r[0] = str(c, 0);
                r[1] = str(c, 1);
                r[2] = str(c, 2);
                r[3] = str(c, 3);
                r[4] = str(c, 4);
                r[5] = str(c, 5);
                r[6] = str(c, 6);
                r[7] = str(c, 7);
                r[8] = str(c, 8);
                rows.add(r);
            }
        } catch (Exception e) {
            // 任何异常安全降级为空列表，绝不上抛
        } finally {
            closeQuietly(c);
        }
        return rows;
    }

    /**
     * 安全查询工单（用于列表展示）。
     *
     * <p>{@code filterDeviceId >= 0} 时按 {@code device_id} 过滤；
     * {@code assignedTo != null} 时按 {@code assigned} 过滤（员工仅看指派给自己的工单）。
     * 内部全程 try/catch，任何异常均安全降级为「空列表」，绝不向上抛出。</p>
     *
     * <p>每行按以下固定顺序存入 {@code String[]}（供 Fragment 按索引取）：
     * 0=id, 1=code, 2=device, 3=title, 4=level, 5=status, 6=assigned。</p>
     */
    public List<String[]> queryTickets(long filterDeviceId, String assignedTo) {
        List<String[]> rows = new ArrayList<>();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return rows;
        }
        Cursor c = null;
        try {
            StringBuilder sql = new StringBuilder(
                    "SELECT id,code,device,title,level,status,assigned FROM tickets");
            List<String> args = new ArrayList<>();
            boolean hasFilter = false;
            if (filterDeviceId >= 0) {
                sql.append(" WHERE device_id=?");
                args.add(String.valueOf(filterDeviceId));
                hasFilter = true;
            }
            if (assignedTo != null && !assignedTo.trim().isEmpty()) {
                sql.append(hasFilter ? " AND " : " WHERE ");
                sql.append("(assigned=? OR assigned=?)");
                args.add(assignedTo.trim());
                args.add(assignedTo.trim());
            }
            sql.append(" ORDER BY id DESC");
            c = db.rawQuery(sql.toString(), args.toArray(new String[0]));
            while (c != null && c.moveToNext()) {
                String[] r = new String[7];
                r[0] = str(c, 0);
                r[1] = str(c, 1);
                r[2] = str(c, 2);
                r[3] = str(c, 3);
                r[4] = str(c, 4);
                r[5] = str(c, 5);
                r[6] = str(c, 6);
                rows.add(r);
            }
        } catch (Exception e) {
            // 任何异常安全降级为空列表，绝不上抛
        } finally {
            closeQuietly(c);
        }
        return rows;
    }

    /** Cursor 取值：isNull 兜底空串，避免 null 引发的 NPE。 */
    private static String str(Cursor c, int i) {
        try {
            if (c.isNull(i)) {
                return "";
            }
            String v = c.getString(i);
            return v == null ? "" : v;
        } catch (Exception e) {
            return "";
        }
    }

    /** 按编号精确查询设备 id，不存在返回 -1。 */
    public long getDeviceIdByCode(String code) {
        if (code == null) {
            return -1;
        }
        code = code.trim();
        if (code.isEmpty()) {
            return -1;
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return -1;
        }
        Cursor c = null;
        long id = -1;
        try {
            c = db.rawQuery("SELECT id FROM devices WHERE code=?", new String[]{code});
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return id;
    }

    private long getDeviceIdByCode(SQLiteDatabase db, String code) {
        if (code == null) {
            return -1;
        }
        code = code.trim();
        if (code.isEmpty()) {
            return -1;
        }
        Cursor c = null;
        long id = -1;
        try {
            c = db.rawQuery("SELECT id FROM devices WHERE code=?", new String[]{code});
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return id;
    }

    /** 按编号查询设备编码，不存在返回空串。 */
    public String getDeviceCode(long id) {
        if (id <= 0) {
            return "";
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return "";
        }
        Cursor c = null;
        String code = "";
        try {
            c = db.rawQuery("SELECT code FROM devices WHERE id=?", new String[]{String.valueOf(id)});
            if (c.moveToFirst()) {
                code = c.getString(0);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return code == null ? "" : code;
    }

    /** 统计某设备的工单数量。 */
    public int countTicketsForDevice(long deviceId) {
        if (deviceId <= 0) {
            return 0;
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return 0;
        }
        Cursor c = null;
        int n = 0;
        try {
            c = db.rawQuery("SELECT count(*) FROM tickets WHERE device_id=?",
                    new String[]{String.valueOf(deviceId)});
            if (c.moveToFirst()) {
                n = c.getInt(0);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return n;
    }

    /**
     * 将工单的展示文本模糊匹配到设备 id。
     * 优先按编号精确匹配，其次按「品牌+型号 / 位置」模糊匹配；匹配不到返回 -1。
     */
    public long resolveDeviceId(String text) {
        if (text == null || text.trim().isEmpty()) {
            return -1;
        }
        String t = text.trim();
        long id = getDeviceIdByCode(t);
        if (id > 0) {
            return id;
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return -1;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id FROM devices"
                            + " WHERE (brand||' '||model) LIKE ? OR location LIKE ? OR (brand||model) LIKE ? LIMIT 1",
                    new String[]{"%" + t + "%", "%" + t + "%", "%" + t + "%"});
            if (c.moveToFirst()) {
                id = c.getLong(0);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return id;
    }

    /**
     * 导入/新建设备：按编号去重，存在则更新、不存在则插入。
     * @return 设备 id
     */
    public long upsertDevice(String code, String type, String brand, String model,
                             String ip, String status, String location, String parentCode, String note) {
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return -1;
        }
        if (code == null) {
            code = "";
        }
        code = code.trim();
        if (brand == null) {
            brand = "";
        }
        if (model == null) {
            model = "";
        }
        if (ip == null) {
            ip = "";
        }
        if (status == null || status.trim().isEmpty()) {
            status = "unknown";
        }
        if (location == null) {
            location = "";
        }
        if (note == null) {
            note = "";
        }
        if (type == null || (!type.equals("camera") && !type.equals("door"))) {
            type = "camera";
        }
        long parentId = 0;
        if (parentCode != null && !parentCode.trim().isEmpty()) {
            long pid = getDeviceIdByCode(parentCode.trim());
            if (pid > 0) {
                parentId = pid;
            }
        }
        long existing = getDeviceIdByCode(code);
        if (existing > 0) {
            db.execSQL("UPDATE devices SET parent_id=?,type=?,brand=?,model=?,ip=?,status=?,location=?,note=?"
                            + " WHERE id=?",
                    new Object[]{parentId, type, brand, model, ip, status, location, note, existing});
            return existing;
        }
        if (code.isEmpty()) {
            // 无编号不允许插入（编号是唯一键），返回 -1 表示跳过。
            return -1;
        }
        db.execSQL("INSERT INTO devices(code,parent_id,type,brand,model,ip,status,location,note,created)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,date('now'))",
                new Object[]{code, parentId, type, brand, model, ip, status, location, note});
        return getDeviceIdByCode(code);
    }

    // -------------------------- 用户 / 权限体系 --------------------------

    /** 用户实体（权限体系）。 */
    public static class User {
        public long id;
        public String username = "";
        public String passHash = "";
        public String name = "";
        public String role = "";
        public String phone = "";
        public String status = "";
        public String created = "";
    }

    /** SHA-256 哈希（十六进制小写）。任何异常安全降级为空串（调用方需判空）。 */
    public static String hashPassword(String plain) {
        if (plain == null) {
            return "";
        }
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(plain.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 校验登录：密码经 SHA-256 后与库中哈希比对，且账号状态为 active。
     * 成功返回 {@link User}，失败（账号/密码错、被停用）返回 null。
     */
    public User verifyLogin(String username, String pass) {
        if (username == null || pass == null) {
            return null;
        }
        username = username.trim();
        if (username.isEmpty()) {
            return null;
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return null;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,username,pass_hash,name,role,phone,status,created"
                            + " FROM users WHERE username=? AND pass_hash=? AND status='active'",
                    new String[]{username, hashPassword(pass)});
            if (c.moveToFirst()) {
                return cursorToUser(c);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return null;
    }

    /** 按用户名查询用户（含被停用的），不存在返回 null。 */
    public User getUserByUsername(String username) {
        if (username == null || username.trim().isEmpty()) {
            return null;
        }
        username = username.trim();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return null;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,username,pass_hash,name,role,phone,status,created"
                            + " FROM users WHERE username=?", new String[]{username});
            if (c.moveToFirst()) {
                return cursorToUser(c);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return null;
    }

    /** 按 id 查询用户，不存在返回 null。 */
    public User getUserById(long id) {
        if (id <= 0) {
            return null;
        }
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return null;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,username,pass_hash,name,role,phone,status,created"
                            + " FROM users WHERE id=?", new String[]{String.valueOf(id)});
            if (c.moveToFirst()) {
                return cursorToUser(c);
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return null;
    }

    private User cursorToUser(Cursor c) {
        User u = new User();
        u.id = c.getLong(0);
        u.username = str(c, 1);
        u.passHash = str(c, 2);
        u.name = str(c, 3);
        u.role = str(c, 4);
        u.phone = str(c, 5);
        u.status = str(c, 6);
        u.created = str(c, 7);
        return u;
    }

    /** 列出全部用户（含被停用），按 id 升序。 */
    public List<User> listUsers() {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return list;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,username,pass_hash,name,role,phone,status,created"
                            + " FROM users ORDER BY id ASC", null);
            while (c != null && c.moveToNext()) {
                list.add(cursorToUser(c));
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return list;
    }

    /** 列出可指派对象（员工/负责人且状态为 active），用于工单「指派给」下拉。 */
    public List<User> listAssignableUsers() {
        List<User> list = new ArrayList<>();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return list;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT id,username,pass_hash,name,role,phone,status,created"
                            + " FROM users WHERE status='active' AND (role=? OR role=?) ORDER BY role,id ASC",
                    new String[]{ROLE_WORKER, ROLE_SUPERVISOR});
            while (c != null && c.moveToNext()) {
                list.add(cursorToUser(c));
            }
        } catch (Exception e) {
            // 忽略
        } finally {
            closeQuietly(c);
        }
        return list;
    }

    /**
     * 新增账号。
     * @return 新账号 id；用户名已存在或参数非法返回 -1。
     */
    public long insertUser(String username, String pass, String name, String role, String phone) {
        if (username == null || username.trim().isEmpty() || pass == null || pass.isEmpty()) {
            return -1;
        }
        username = username.trim();
        if (!isValidRole(role)) {
            role = ROLE_WORKER;
        }
        if (getUserByUsername(username) != null) {
            return -1; // 用户名已存在
        }
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return -1;
        }
        try {
            db.execSQL("INSERT INTO users(username,pass_hash,name,role,phone,status,created)"
                            + " VALUES(?,?,?,?,?,?,date('now'))",
                    new Object[]{username, hashPassword(pass), name == null ? "" : name.trim(),
                            role, phone == null ? "" : phone.trim(), "active"});
            User u = getUserByUsername(username);
            return u != null ? u.id : -1;
        } catch (Exception e) {
            return -1;
        }
    }

    /**
     * 更新账号资料（姓名/角色/手机号/状态）。禁止修改 admin 的角色与状态（防锁定管理员）。
     * @return 是否成功
     */
    public boolean updateUser(long id, String name, String role, String phone, String status) {
        User u = getUserById(id);
        if (u == null) {
            return false;
        }
        if (ROLE_ADMIN.equals(u.role)) {
            // 管理员：仅允许更新姓名/手机号，角色与状态保持不变
            role = ROLE_ADMIN;
            status = "active";
        } else {
            if (!isValidRole(role)) {
                role = u.role;
            }
            if (!"active".equals(status) && !"disabled".equals(status)) {
                status = u.status;
            }
        }
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return false;
        }
        try {
            db.execSQL("UPDATE users SET name=?,role=?,phone=?,status=? WHERE id=?",
                    new Object[]{name == null ? "" : name.trim(), role,
                            phone == null ? "" : phone.trim(), status, id});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 重置指定账号密码（管理员操作）。禁止重置为无效值。 */
    public boolean resetPassword(long id, String newPass) {
        if (newPass == null || newPass.isEmpty()) {
            return false;
        }
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return false;
        }
        try {
            db.execSQL("UPDATE users SET pass_hash=? WHERE id=?",
                    new Object[]{hashPassword(newPass), id});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 修改当前用户自身密码（需校验旧密码）。 */
    public boolean updateOwnPassword(long id, String oldPass, String newPass) {
        if (oldPass == null || newPass == null || newPass.isEmpty()) {
            return false;
        }
        User u = getUserById(id);
        if (u == null) {
            return false;
        }
        if (!u.passHash.equals(hashPassword(oldPass))) {
            return false; // 旧密码不符
        }
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return false;
        }
        try {
            db.execSQL("UPDATE users SET pass_hash=? WHERE id=?",
                    new Object[]{hashPassword(newPass), id});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 删除账号。禁止删除管理员账号（admin）。
     * @return 是否成功删除
     */
    public boolean deleteUser(long id) {
        User u = getUserById(id);
        if (u == null) {
            return false;
        }
        if (ROLE_ADMIN.equals(u.role)) {
            return false; // 禁止删除管理员
        }
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return false;
        }
        try {
            db.execSQL("DELETE FROM users WHERE id=?", new Object[]{id});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** 设置工单指派对象（按用户名）。 */
    public boolean assignTicket(long ticketId, String assignedUsername) {
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return false;
        }
        try {
            db.execSQL("UPDATE tickets SET assigned=? WHERE id=?",
                    new Object[]{assignedUsername == null ? "" : assignedUsername, ticketId});
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------- 数据同步：用户导出/导入 --------------------------

    /** 导出用户为 JSON 数组（含哈希，可跨端同步）。 */
    public org.json.JSONArray exportUsers() {
        org.json.JSONArray arr = new org.json.JSONArray();
        SQLiteDatabase db = safeReadable();
        if (db == null) {
            return arr;
        }
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT username,pass_hash,name,role,status,phone"
                            + " FROM users ORDER BY id ASC", null);
            while (c != null && c.moveToNext()) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("username", str(c, 0));
                o.put("pass_hash", str(c, 1));
                o.put("name", str(c, 2));
                o.put("role", str(c, 3));
                o.put("status", str(c, 4));
                o.put("phone", str(c, 5));
                arr.put(o);
            }
        } catch (Exception e) {
            // 忽略单条异常
        } finally {
            closeQuietly(c);
        }
        return arr;
    }

    /**
     * 按 username upsert 用户（数据同步）。admin 账号不可被覆盖或禁用，确保内置管理员永不被同步破坏。
     * @return 是否成功写入
     */
    public boolean importUser(org.json.JSONObject o) {
        if (o == null) {
            return false;
        }
        String username = o.optString("username", "").trim();
        if (username.isEmpty()) {
            return false;
        }
        String passHash = o.optString("pass_hash", "");
        String name = o.optString("name", "");
        String role = o.optString("role", ROLE_WORKER);
        String status = o.optString("status", "active");
        String phone = o.optString("phone", "");
        if (!isValidRole(role)) {
            role = ROLE_WORKER;
        }
        // 保护内置管理员：admin 不可被覆盖/禁用
        if (ADMIN_USERNAME.equals(username)) {
            return false;
        }
        SQLiteDatabase db = safeWritable();
        if (db == null) {
            return false;
        }
        try {
            User existing = getUserByUsername(username);
            if (existing != null) {
                db.execSQL("UPDATE users SET pass_hash=?,name=?,role=?,phone=? WHERE username=?",
                        new Object[]{passHash, name, role, phone, username});
            } else {
                db.execSQL("INSERT INTO users(username,pass_hash,name,role,phone,status,created)"
                                + " VALUES(?,?,?,?,?,?,date('now'))",
                        new Object[]{username, passHash, name, role, phone, status});
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isValidRole(String role) {
        return ROLE_ADMIN.equals(role) || ROLE_SUPERVISOR.equals(role) || ROLE_WORKER.equals(role);
    }

    // -------------------------- 会话（SharedPreferences） --------------------------

    /** 登录成功后保存当前用户会话。 */
    public static void saveSession(Context ctx, User u) {
        if (ctx == null || u == null) {
            return;
        }
        SharedPreferences sp = ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE);
        SharedPreferences.Editor ed = sp.edit();
        ed.putLong(K_ID, u.id);
        ed.putString(K_USERNAME, u.username);
        ed.putString(K_NAME, u.name);
        ed.putString(K_ROLE, u.role);
        ed.apply();
    }

    /** 读取当前会话用户；无会话返回 null。 */
    public static User getCurrentUser(Context ctx) {
        if (ctx == null) {
            return null;
        }
        SharedPreferences sp = ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE);
        String username = sp.getString(K_USERNAME, "");
        if (username == null || username.isEmpty()) {
            return null;
        }
        User u = new User();
        u.id = sp.getLong(K_ID, 0);
        u.username = username;
        u.name = sp.getString(K_NAME, "");
        u.role = sp.getString(K_ROLE, "");
        return u;
    }

    /** 当前会话角色；无会话返回空串。 */
    public static String getCurrentRole(Context ctx) {
        if (ctx == null) {
            return "";
        }
        return ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE).getString(K_ROLE, "");
    }

    /** 当前会话用户名；无会话返回空串。 */
    public static String getCurrentUsername(Context ctx) {
        if (ctx == null) {
            return "";
        }
        String v = ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE).getString(K_USERNAME, "");
        return v == null ? "" : v;
    }

    /** 当前会话姓名；无会话返回空串。 */
    public static String getCurrentName(Context ctx) {
        if (ctx == null) {
            return "";
        }
        String v = ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE).getString(K_NAME, "");
        return v == null ? "" : v;
    }

    /** 退出登录：清除会话。 */
    public static void logout(Context ctx) {
        if (ctx == null) {
            return;
        }
        SharedPreferences sp = ctx.getSharedPreferences(SESSION_PREF, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }

    /** 角色中文名映射。 */
    public static String roleLabel(String role) {
        if (ROLE_ADMIN.equals(role)) {
            return "管理员";
        }
        if (ROLE_SUPERVISOR.equals(role)) {
            return "负责人";
        }
        if (ROLE_WORKER.equals(role)) {
            return "员工";
        }
        return role == null ? "" : role;
    }

    // -------------------------- 内部工具 --------------------------

    private SQLiteDatabase safeReadable() {
        try {
            return getReadableDatabase();
        } catch (Exception e) {
            return null;
        }
    }

    private SQLiteDatabase safeWritable() {
        try {
            return getWritableDatabase();
        } catch (Exception e) {
            return null;
        }
    }

    private static void closeQuietly(Cursor c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignore) {
                // 忽略
            }
        }
    }
}

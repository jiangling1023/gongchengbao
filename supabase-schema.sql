-- ============================================================
-- 安防维修排查系统 - Supabase 建表 SQL
-- 创建方式：登录 supabase.com → 选择项目 → SQL Editor → 粘贴执行
-- ============================================================

-- 1) 用户表（三级权限）
CREATE TABLE IF NOT EXISTS users (
  id          BIGSERIAL PRIMARY KEY,
  username    TEXT UNIQUE NOT NULL,
  pass_hash   TEXT NOT NULL,
  name        TEXT NOT NULL DEFAULT '',
  role        TEXT NOT NULL DEFAULT 'worker',  -- admin / supervisor / worker
  phone       TEXT NOT NULL DEFAULT '',
  status      TEXT NOT NULL DEFAULT 'active',  -- active / disabled
  created     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 2) 设备表
CREATE TABLE IF NOT EXISTS devices (
  id          BIGSERIAL PRIMARY KEY,
  code        TEXT UNIQUE NOT NULL,
  device_id   BIGINT NOT NULL DEFAULT 0,
  parent_id   BIGINT REFERENCES devices(id) ON DELETE SET NULL,
  type        TEXT NOT NULL DEFAULT 'camera',  -- camera / door / switch / other
  brand       TEXT NOT NULL DEFAULT '',
  model       TEXT NOT NULL DEFAULT '',
  ip_address  TEXT NOT NULL DEFAULT '',
  status      TEXT NOT NULL DEFAULT 'unknown', -- online / offline / unknown / fault
  location    TEXT NOT NULL DEFAULT '',
  note        TEXT NOT NULL DEFAULT '',
  building    TEXT NOT NULL DEFAULT '',
  floor       TEXT NOT NULL DEFAULT '',
  created     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

-- 3) 故障工单表
CREATE TABLE IF NOT EXISTS tickets (
  id          BIGSERIAL PRIMARY KEY,
  code        TEXT UNIQUE NOT NULL,
  device_id   BIGINT REFERENCES devices(id) ON DELETE SET NULL,
  device      TEXT NOT NULL DEFAULT '',
  title       TEXT NOT NULL DEFAULT '',
  ftype       TEXT NOT NULL DEFAULT '其他',
  level       TEXT NOT NULL DEFAULT 'P3',  -- P0 / P1 / P2 / P3
  status      TEXT NOT NULL DEFAULT 'pending', -- pending / processing / closed
  description TEXT NOT NULL DEFAULT '',
  solution    TEXT NOT NULL DEFAULT '',
  created     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
  closed      TIMESTAMP WITH TIME ZONE,
  assigned    TEXT NOT NULL DEFAULT ''  -- 指派给的用户名
);

-- 4) 巡检记录表
CREATE TABLE IF NOT EXISTS inspections (
  id       BIGSERIAL PRIMARY KEY,
  title    TEXT NOT NULL DEFAULT '',
  date     DATE NOT NULL DEFAULT current_date,
  result   TEXT NOT NULL DEFAULT '',
  note     TEXT NOT NULL DEFAULT ''
);

-- ============================================================
-- 索引（加速常用查询）
-- ============================================================
CREATE INDEX IF NOT EXISTS idx_devices_code ON devices(code);
CREATE INDEX IF NOT EXISTS idx_devices_status ON devices(status);
CREATE INDEX IF NOT EXISTS idx_tickets_status ON tickets(status);
CREATE INDEX IF NOT EXISTS idx_tickets_assigned ON tickets(assigned);
CREATE INDEX IF NOT EXISTS idx_tickets_device ON tickets(device_id);

-- ============================================================
-- RLS（行级安全）：所有表默认关闭（便于初期开发）
-- 生产环境建议开启并按角色设策略
-- ============================================================
ALTER TABLE users ENABLE ROW LEVEL SECURITY;
ALTER TABLE devices ENABLE ROW LEVEL SECURITY;
ALTER TABLE tickets ENABLE ROW LEVEL SECURITY;
ALTER TABLE inspections ENABLE ROW LEVEL SECURITY;

-- 允许所有操作（开发阶段，生产请收紧）
CREATE POLICY "allow_all" ON users FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all" ON devices FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all" ON tickets FOR ALL USING (true) WITH CHECK (true);
CREATE POLICY "allow_all" ON inspections FOR ALL USING (true) WITH CHECK (true);

-- ============================================================
-- 种子数据：内置管理员
-- 密码 Admin@2026 的 SHA-256 哈希（十六进制小写）
-- ============================================================
SELECT pg_catalog.setval('users_id_seq', 1, true);
INSERT INTO users (username, pass_hash, name, role, phone, status, created)
VALUES (
  'admin',
  '298a93bd7be0220e0c833a9c4ae47c1d0f2bbf20bc0a8521e9d42f2e6c0c2806',
  '系统管理员',
  'admin',
  '',
  'active',
  now()
) ON CONFLICT (username) DO NOTHING;

-- 插入示例设备
INSERT INTO devices (code, type, brand, model, ip_address, status, location, created)
VALUES
  ('S001', 'camera', '海康威视', 'DS-2CD3346', '10.10.10.21', 'online', '1F大堂东侧', now()),
  ('S002', 'camera', '海康威视', 'DS-2CD3T46', '10.10.10.22', 'online', '2F电梯厅', now()),
  ('S003', 'door', '立林', 'L-A1四门控制器', '10.10.20.10', 'online', '1F大门', now()),
  ('S004', 'door', '立林', '读卡器IC', '10.10.20.11', 'online', '1F大门', now())
ON CONFLICT (code) DO NOTHING;

-- 插入示例工单
INSERT INTO tickets (code, device_id, device, title, ftype, level, status, description, solution, created)
VALUES
  ('WX-001', 1, 'S001 海康威视 DS-2CD3346', '画面模糊', '硬件', 'P2', 'processing', '监控画面模糊，需调整焦距', '', now()),
  ('WX-002', 3, 'S003 立林 L-A1四门控制器', '刷卡无反应', '权限', 'P1', 'pending', '门禁刷卡后门锁不开', '', now())
ON CONFLICT (code) DO NOTHING;

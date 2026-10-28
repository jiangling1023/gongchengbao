# 工程宝 云端部署指南（完全免费）

## 一、创建 Supabase 项目（5分钟）

1. 打开 https://supabase.com ，用 GitHub 账号登录（免费）
2. 点击 **New Project**，填写：
   - Name: `gongchengbao`
   - Database Password: 设一个强密码（记住它）
   - Region: 选 **Southeast Asia (Singapore)**（离中国最近）
3. 等待 2-3 分钟创建完成

## 二、执行建表 SQL

1. 进入项目后，点击左侧 **SQL Editor**
2. 新建 Query，粘贴 `supabase-schema.sql` 的全部内容
3. 点击 **Run**，看到 `INSERT 0 1` 等成功提示即完成

## 三、获取 API 配置信息

1. 点击左侧 **Settings**（齿轮图标）→ **API**
2. 复制两行：
   - **Project URL**: `https://xxxx.supabase.co`
   - **anon/public key**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

## 四、修改网页版配置

打开 `web-app/index.html`，找到这两行：

```javascript
window.SUPABASE_URL = '';        // ← 填入你的 Project URL
window.SUPABASE_ANON_KEY = '';   // ← 填入你的 anon key
```

替换为实际值，保存。

## 五、部署到 Cloudflare Pages（免费）

### 方式 A：直接用 Git（推荐）
1. 把整个 `safety-repair-system` 推送到 GitHub
2. 登录 https://dash.cloudflare.com → **Workers & Pages** → **Create app** → **Pages**
3. 连接你的 GitHub 仓库，选择分支
4. Build command 留空，Build output directory 填 `source/safety-repair-system/web-app`
5. 添加 Environment Variable：
   - `SUPABASE_URL` = 你的 Project URL
   - `SUPABASE_ANON_KEY` = 你的 anon key
6. 点击 **Save and Deploy**

### 方式 B：手动上传（最快）
1. 登录 https://dash.cloudflare.com → **Workers & Pages** → **Create app** → **Pages**
2. 点击 **Upload assets manually**
3. 把 `web-app` 文件夹整个压缩成 zip 上传
4. 在 Project Settings → Variables 里填入 `SUPABASE_URL` 和 `SUPABASE_ANON_KEY`
5. 部署完成后获得 `https://xxxx.pages.dev` 地址

## 六、安卓端同步配置

安卓端已有 JSON 导入/导出功能。云端数据可通过以下方式同步：

1. 网页版登录后，点击右上角「同步」→ 导出 JSON
2. 安卓端「数据备份」→ 导入该 JSON 文件
3. 以后两端各自编辑，定期手动导出/导入即可

> 注：安卓端实时同步需要额外开发 API 客户端，当前版本支持 JSON 互导作为过渡方案。

## 七、免费额度确认

| 资源 | 免费额度 | 本项目用量预估 |
|------|---------|--------------|
| 数据库 | 500 MB | < 10 MB（1000台设备+5000工单） |
| API 请求 | 无限 | 每天 < 1000 次 |
| 带宽 | 5 GB/月 | 网页版 < 100 MB/月 |
| 存储 | 1 GB | < 50 MB |
| 月活用户 | 50,000 MAU | 满足小团队 |

**结论：完全免费，无隐藏收费。**

## 八、注意事项

1. **免费项目不活跃 1 周会自动暂停**：每周至少访问一次网页版，或每月执行一次 SELECT 查询保持活跃
2. **不要在生产环境使用 free 版**：如需长期运营，升级到 Pro（$25/月）
3. **数据备份**：Supabase 免费版无自动备份，建议每周导出 JSON 保存到本地

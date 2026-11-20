# AgroVision V2.0 后端骨架（Supabase）

> **定位**：本地优先的 Android 端农技顾问 App 的最小化云同步后端
>
> **核心原则**：不存原图，只同步账号元数据 + 地块 + 用药记录 + 检测历史元数据（含 ≤200×200 缩略图）

> **当前状态（2026-08-04）**：Android 端已接入 `SyncService`、`AgroVisionApi` 和 V2.0 同步开关；本目录提供 Supabase schema 与部署说明。未部署兼容后端时，App 会继续本地优先运行，云同步/换机恢复不可用。

---

## 一、技术选型与成本

| 项 | 选择 | 理由 |
|---|---|---|
| 数据库 | Supabase Postgres | 10K 用户内免费；RLS 行级安全原生支持 |
| 鉴权 | Supabase Auth（手机号 + OTP） | 国产用户友好，免自建短信网关 |
| 文件存储 | Supabase Storage | 仅存缩略图，单文件 ≤50KB |
| 函数 | Postgres RPC + Edge Functions（可选） | 批量 upsert 直接用 SQL RPC，无需写后端 |
| 迁移 | Supabase CLI / Dashboard SQL Editor | 单文件 `schema.sql` 全量部署 |

**成本预估**：10K MAU / 1GB DB / 1GB Storage 内全免费；超出后按 Supabase Pro $25/月。

---

## 二、部署步骤

### 1. 创建 Supabase 项目
- 访问 https://supabase.com 注册并创建新项目
- Region 选 `Northeast Asia (Tokyo)` 或 `Southeast Asia (Singapore)`，对国内访问友好
- 记录 `Project URL` 和 `anon key`、`service_role key`

### 2. 启用手机号 Auth
- Dashboard → Authentication → Providers → Phone → 启用
- 配置 SMS Provider（推荐 Twilio 或 阿里云短信，Supabase 文档有指引）
- 关闭 Email signup（避免冗余）

### 3. 执行 Schema
- Dashboard → SQL Editor → New Query
- 全量粘贴 [`schema.sql`](./schema.sql) → Run
- 验证：Tables 应出现 `user_profiles` / `plots` / `detection_history` / `treatments`

### 4. 创建 Storage Bucket
- **`schema.sql` 已自动建好 `thumbnails` bucket（private，256 KB 上限）与三条 `storage.objects` 策略**（用户只能读写 `thumbnails/{user_id}/` 前缀）
- 若当前角色无 `storage` schema 写权限导致该段失败，再到 Dashboard → Storage → New Bucket 手动创建，名称 `thumbnails`、Public 关闭

### 5. 客户端配置（Android 端）
在 `app/build.gradle.kts` 中：
```kotlin
buildConfigField("String", "SUPABASE_URL", "\"https://xxx.supabase.co\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"xxx\"")
```

---

## 三、API 端点映射

客户端 `AgroVisionApi` 当前使用自建 REST 风格路径（`api/v2/...`）。如直接接 Supabase PostgREST，需要在 Retrofit baseUrl/接口层增加适配，或通过 Edge Function/API Gateway 把下表路径转发到 Supabase RPC/表接口。

### Android 客户端当前路径

| 客户端方法 | HTTP | 当前客户端端点 |
|---|---|---|
| `upsertPlots` | POST | `/api/v2/plots/batch` |
| `getPlots` | GET | `/api/v2/plots?page=1&size=50` |
| `upsertTreatments` | POST | `/api/v2/treatments/batch` |
| `getTreatments` | GET | `/api/v2/treatments?page=1&size=50` |
| `uploadDetection` | POST | `/api/v2/detections` |
| `uploadDetections` | POST | `/api/v2/detections/batch` |
| `getDetections` | GET | `/api/v2/detections?page=1&size=20` |

### Supabase 建议映射

| 客户端方法 | HTTP | Supabase 端点 |
|---|---|---|
| `upsertPlots` | POST | `/rest/v1/rpc/upsert_plots` |
| `getPlots` | GET | `/rest/v1/plots?user_id=eq.{uid}` |
| `upsertTreatments` | POST | `/rest/v1/rpc/upsert_treatments` |
| `getTreatments` | GET | `/rest/v1/treatments?user_id=eq.{uid}` |
| `uploadDetection` | POST | `/rest/v1/detection_history` |
| `getDetections` | GET | `/rest/v1/detection_history?user_id=eq.{uid}` |

> 客户端 OkHttp 拦截器当前会自动注入 `Authorization: Bearer {access_token}`。如果直连 Supabase，还需补充 `apikey: {anon_key}` 头，建议在 API Gateway 或专用 Supabase OkHttp interceptor 中处理，避免污染通用业务 API。

---

## 四、客户端启用前检查

- `FeatureFlag.isV2CloudSyncEnabled = true`
- 用户已登录且本地存在 access token
- `BuildConfig.BASE_URL` 指向已部署的 API Gateway 或兼容 REST 服务
- 后端严格执行“不上传原图”策略，检测记录的 `image_path` 在云端应为空或仅保存缩略图引用
- 服务端返回 `RemoteIdMapping(local_id, remote_id)`，否则客户端无法回填同步状态

---

## 五、安全策略

1. **RLS 全表开启 + FORCE**：用户只能 CRUD 自己的行（`auth.uid() = user_id`）；`force row level security` 让表属主同样受策略约束，只有 `service_role`（BYPASSRLS）能绕过
2. **API Key 不下沉到客户端**：`service_role key` 仅用于 Edge Function / 后端脚本；客户端只用 `anon key`
3. **RPC 一律 security invoker**：`upsert_plots` 以内调用者身份执行，RLS 照常生效——即便函数内断言被绕过，也写不进别人名下的行；同时 `set search_path = public, pg_temp` 关闭搜索路径劫持
4. **anon 无表权限**：四张表已 `revoke all ... from anon`，`authenticated` 的权限仍被 RLS 逐行约束；序列同样显式 `grant usage`
5. **缩略图签名 URL**：Storage 用 `createSignedUrl()` 返回 1 小时有效的下载链接；bucket 为 private，单文件 256 KB 上限
6. **不存原图**：客户端压缩后上传缩略图，原图留在本地存储
7. **服务端兜底校验**：`confidence ∈ [0,1]`、经纬度范围、`status` 枚举、`safety_interval_days ∈ [0,365]`、文本长度均有 check 约束——客户端校验不可信，落库前必须再校验一次

---

## 六、与 V3.0 的关系

V2.0 Supabase 后端在用户量 < 10K 时无需迁移；V3.0 引入多智能体、GIS、告警中心后再考虑迁出 Postgres（可平滑迁移到自建 Ktor + Postgres，schema 完全兼容）。

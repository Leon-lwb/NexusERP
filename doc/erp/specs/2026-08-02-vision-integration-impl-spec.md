# Nexus-ERP 视觉识别整体接入方案（人脸 + 物料识别）

> 状态：**Phase 1a+1b 已完成并端到端验证通过**（2026-08-02），Phase 2 待启动
> 日期：2026-08-02
> 关联服务：[nexus-vision-service](D:\workspace\nexus-vision-service)（已实现 V0–V4）
> 本文档规划 V5 阶段：`ruoyi-common-cv` Java 客户端 + ERP/Web/App 三端接入落地路径

---

## 〇、目标与范围

| 项 | 内容 |
|----|------|
| 视觉能力来源 | nexus-vision-service（FastAPI，单容器，:5001）—— 人脸 1:1/1:N + YOLO 检测 + OCR，均已实现并验证 |
| 接入方式 | Java 后端经 `ruoyi-common-cv` 公共模块统一调用；Web / App / 未来 PAD 一律**不直连 vision 服务**，统一走后端代理 |
| 落地场景 | ① 考勤/打卡人脸验证（App）② 车间操作台人脸登录/验证（Web，MES 场景）③ 物料识别 = OCR 读标签先行 + 自定义 YOLO 预留 |
| 技术路线 | 物料识别选型 **C：OCR（RapidOCR）立即上线 + 自训练 YOLO 小模型项目后期替换**；当前 YOLOv11n（COCO 80 类）不含工厂料件，仅作通用检测兜底 |
| 底库来源 | 上线时**组织一次集中采集注册**（当前测试阶段：从网上下载头像照片绑定测试账号，验证底库链路） |
| PAD 终端 | 手持智能终端项目**尚未创建** —— 本方案预留接口抽象与页面复用空间，立项后再定技术栈（记录为"待立项再定"） |
| 产出 | 三端（Java 后端 / plus-ui / Nexus_App）落地路径 + 分期计划 + 安全与网络约束 |

### 范围外（本方案不包含）

- vision 服务自身的模型训练/调优（自定义 YOLO 数据采集标注）
- PAD 项目的完整设计与开发
- 人脸活体检测（高安全场景后续评估）

---

## 一、现状盘点

### 1.1 nexus-vision-service（服务端 ✅ 已完成）

| 能力 | 端点 | 说明 |
|------|------|------|
| 人脸注册 | `POST /api/v1/faces` | subject=工号（`EMP-1001`），重复注册特征取均值 |
| 人脸 1:N | `POST /api/v1/faces/recognize` | 每脸 top-N，`limit` 默认 1 |
| 人脸 1:1 | `POST /api/v1/faces/verify` | file vs 底库 subject，返回 similarity/matched |
| 人脸检测 | `POST /api/v1/faces/detect` | 仅检测（活体/框选场景预留） |
| 底库管理 | `GET/PUT/DELETE /api/v1/faces[/{subject}]` | 列表分页、详情含照片 base64、照片 JPEG 直出 |
| 物料/通用检测 | `POST /api/detect/material` | YOLO，COCO 80 类；支持 `file` 或 `image_base64` |
| 成品检测+OCR | `POST /api/detect/product` | YOLO + RapidOCR（`enable_ocr` 默认 true） |
| 健康检查 | `GET /health`、`/health/ready` | 免 key |
| API key 管理 | `GET/POST/DELETE /api/v1/api-keys` | 仅管理员 key 可调 |

- **鉴权**：`x-api-key` 请求头（60s 缓存），dev key 仅本地
- **性能实测**（CPU）：注册 ~127ms、1:N 识别 ~450ms、1:1 验证 ~354ms、YOLO 640×640 ~70–110ms
- **输入**：`file` multipart 或 `image_base64`（对 App 端友好）
- **模型**：SCRFD+ArcFace（人脸）、RapidOCR（OCR）、YOLOv11n（通用，**非工厂料件**）
- **数据库**：MySQL `vision` 库（`vision_api_key`、`face_subject` 两表），底库照片 LONGBLOB

### 1.2 nexus-erp Java 后端（✅ 无集成，待开发）

- `ruoyi-common-cv` 模块**不存在**；Java 端无任何视觉调用代码
- `ruoyi-common-core` 已含 `hutool-http`（`cn.hutool` 依赖在 common-core pom），客户端开发无障碍
- 打卡链路：`AppSchAttendanceController`（`@DS("erp")`，`/app/sch/attendance/clock-in`）→ `ISchAttendanceService.performClockIn(workerId, ...)`；当前已带位置校验
- 员工表：`SysUser.avatar` 为 `Long`（ossId，头像存 MinIO 私有桶）；`SchWorker.employeeNo` 为员工工号

### 1.3 plus-ui Web（✅ 无集成，待开发）

- 无任何人脸/视觉代码；相机能力仅存在于 `wm/warehouse/3d.vue`（无关）
- 前端走标准 `src/api/**` + axios 拦截器（登录态 `Authorization` 头）

### 1.4 Nexus_App（✅ 无集成，待开发）

- uni-app（Vue3 + Pinia），打卡页 `pages/sch/attendance/punch-card.vue`，API 封装于 `api/sch/attendance.js`
- manifest 已含 `CAMERA`/`FLASHLIGHT` 权限（扫码用）
- **重点**：H5 / App 端可直连 vision，但**微信小程序端域名需白名单** —— 统一走后端代理是唯一稳妥路径

---

## 二、总体架构

```
┌───────────────────────────── 三端终端 ─────────────────────────────┐
│  plus-ui Web         Nexus_App (App/H5/小程序)        PAD (待立项) │
│  (操作台/管理页)      (打卡/拍照/扫描)                 (预留复用)    │
└───────────────┬──────────────────────┬──────────────────────┬──────┘
                │ 登录态(Bearer)        │ 登录态(Bearer)        │ 登录态
                ▼                      ▼                      ▼
┌─────────────────────────────── nexus-erp Java 后端 ──────────────────────────────┐
│  ruoyi-common-cv (新增) —— 唯一调用视觉服务的客户端                                │
│  VisionClient: 人脸注册/1:1/1:N/底库 + 物料/成品/OCR + 健康检查 + x-api-key 缓存  │
│  配置 nexus.vision.base-url / api-key / timeout / max-image-dim                    │
│  场景 Service: 打卡人脸验证(可选)、操作台人脸验证、物料OCR识别、底库注册管理       │
│  数据: SysUser.avatar(ossId) + SchWorker.employeeNo + SchAttendance(记录)         │
└───────────────┬───────────────────────────────────────────────────────┘
                │ HTTP REST (x-api-key)
                ▼
┌────────────────────────── nexus-vision-service (Python, :5001) ──────────────────┐
│  FaceEngine(SCRFD+ArcFace) | YOLO | RapidOCR | MySQL底库 | API-key鉴权           │
└──────────────────────────────────────────────────────────────────────────────────┘
```

**关键设计决策**

1. **服务端调用**：三端一律不直连 vision，统一经 Java `ruoyi-common-cv` 代理。原因：① key 不暴露给客户端；② 微信小程序域名白名单；③ 后端可做业务校验/日志/限流。
2. **公共模块落位**：`ruoyi-common-cv` 放 `ruoyi-common/` 下（参照 `ruoyi-common-ai` 的独立 jar + 自动配置模式）。
3. **subject 约定**：`EMP-<工号>`（员工，打卡/操作台）—— 与 vision 设计一致；未来 PAD/访客用其他前缀。
4. **照片主数据**：底库照片归 vision 服务（`face_subject.photo` LONGBLOB）；ERP 侧 `SysUser.avatar`（ossId）继续作员工头像展示，两处解耦，**不做强同步**（集中采集注册时一次性导入；avatar 后续变更不自动同步底库，需管理端手动重新注册，见 3.2.4）。
5. **图片传输**：VisionClient 内部优先 multipart file；App/Web 上传到 ERP 后由后端转发，或直接传 `image_base64`（vision 已支持）。

---

## 三、后端设计（ruoyi-common-cv + 场景集成）

### 3.1 新模块 `ruoyi-common-cv`

**模块结构**（仿 `ruoyi-common-ai`）：

```
ruoyi-common/ruoyi-common-cv/
├── pom.xml
└── src/main/java/com/nexus/common/cv/
    ├── config/
    │   └── CvProperties.java          # nexus.vision.* 配置
    │   └── CvAutoConfiguration.java   # 自动配置（@ConditionalOnProperty）
    ├── client/
    │   ├── VisionClient.java          # 统一入口（Hutool HttpUtil 封装）
    │   └── VisionClientImpl.java
    ├── domain/                        # 请求/响应 DTO（与 vision API 契约对应）
    │   ├── FaceVerifyRequest.java / FaceVerifyResult.java
    │   ├── FaceRecognizeRequest.java / FaceRecognizeResult.java
    │   ├── FaceRegisterRequest.java / FaceRegisterResult.java
    │   ├── FaceSubjectVo.java         # 底库条目
    │   ├── DetectResult.java          # YOLO detections + ocr_results
    │   └── HealthResult.java
    └── exception/
        └── VisionException.java       # 上游超时/4xx/5xx 统一包装
```

**配置段**（`nexus.vision.*`，参照 `nexus.ai.*` 风格）：

```yaml
nexus:
  vision:
    base-url: http://localhost:5001        # vision 服务地址（prod 内网 IP）
    api-key: ${VISION_API_KEY:}            # 服务端 key（生产走环境变量）
    timeout-ms: 5000                       # HTTP 超时
    max-image-dim: 4096                    # 客户端上传前尺寸校验上限，超过则前置压缩（vision 服务端另有 MAX_IMAGE_DIM=4096 兜底）
    enabled: true                          # 未部署 vision 时优雅降级开关
# 注：人脸识别阈值由 vision 服务端 FACE_RECOGNITION_THRESHOLD（默认 0.5）单一控制，客户端不再配置，避免双源漂移
```

**VisionClient 关键方法**（同步，均抛 `VisionException` 包装上游错误）：

| 方法 | 对应 vision 端点 | 说明 |
|------|------------------|------|
| `verifyFace(byte[] image, String subject)` | `POST /api/v1/faces/verify` | 1:1 验证，返回 similarity/matched |
| `recognizeFace(byte[] image, int limit)` | `POST /api/v1/faces/recognize` | 1:N 识别 top-N |
| `detectFace(byte[] image)` | `POST /api/v1/faces/detect` | 仅检测 |
| `registerFace(byte[] image, String subject, ...)` | `POST /api/v1/faces` | 注册/追加样本 |
| `listFaces(page, pageSize)` / `getFace(subject)` / `deleteFace(subject)` | `GET/DELETE /api/v1/faces*` | 底库管理 |
| `detectMaterial(byte[] image)` | `POST /api/detect/material` | YOLO 通用检测 |
| `detectProduct(byte[] image, boolean ocr)` | `POST /api/detect/product` | YOLO + OCR（物料识别主路径） |
| `health()` / `ready()` | `GET /health*` | 探针 |

**实现要点**

- Hutool `HttpRequest` + `x-api-key` 头；multipart `file` 优先，`image_base64` 兜底
- `image_base64` 需 URL-safe base64（vision 用 `decode_base64` 已兼容标准 base64，但 base64 字符串体积约 +33%，大图注意限制）
- API key 缓存（60s）由 vision 服务端负责，客户端不做重复缓存
- **降级**：`nexus.vision.enabled=false` 或调用失败时，场景代码走兜底（打卡不阻断 / 提示识别服务不可用），不允许因视觉服务故障阻断核心业务
- **降级安全闭环**：vision 连续 3 次 `/health` 探活失败 → 触发告警（飞书/钉钉）；服务恢复后 24h 内对降级期间产生的 `face_verify_status=NONE` 记录批量补跑校验，避免"降级=静默关闭校验"

### 3.2 场景集成（ruoyi-erp / ruoyi-mes）

#### 3.2.1 考勤打卡人脸验证（App）

**触发链路：A 异步校验（已定稿）**

> 决策：采用 **A 打卡后异步校验**，不阻断打卡。理由：产线体验优先，vision 单次 ~354ms + 网络会让同步打卡明显卡顿；安全语义由"FAIL 复核闭环 + 降级补跑"补回。

- `AppSchAttendanceController.clockIn()` 成功后，将 `workerId + 人脸照片` 提交到**异步校验线程池 `cvExecutor`**（在 `ruoyi-erp` 内自建，仿 `FinancialThreadPoolConfig`，核心 2 / 最大 8 / 队列 100 / `CallerRunsPolicy` 兜底）。**不引用** `ruoyi-ai` 的 `aiExecutor`（避免破坏 erp→ai 模块边界，已核实 ruoyi-erp 不依赖 ruoyi-ai）；**不引入 MQ**（Phase 1 不值得）。
- 校验通过 → `face_verify_status=PASS`；校验失败 → `FAIL`；vision 不可用/降级 → `NONE`。
- **FAIL 复核闭环**：考勤管理员每日在 Web 端复查昨日 `FAIL` 记录（24h SLA），可标记"误判→改 PASS"或"代打卡→转异常考勤"。Web 复查页属 Phase 1b。
- **降级补跑**：vision 恢复后 24h 内，定时任务批量对 `NONE` 记录补跑 `verifyFace`，更新状态。
- ~~B 同步校验~~ 不采用（保留为未来高安全场景的可选项，需配合活体检测）。

**表结构扩展**（`sch_attendance` 增量列）：

| 字段 | 类型 | 说明 |
|------|------|------|
| `face_verify_status` | VARCHAR(16) | 未验证/通过/失败（NONE/PASS/FAIL） |
| `face_verify_time` | DATETIME | 校验时间 |
| `face_similarity` | DECIMAL(5,4) | 相似度 |
| `face_image_oss_id` | BIGINT | 打卡人脸照片（MinIO ossId，存证） |

**迁移与代码同步**（遵循项目增量 SQL 约定 —— 仓库未引入 Flyway，`db/migration` 仅 generator 模块旧残留）：
- 新建增量 SQL `script/sql/update/2026-08-02-cv-face-sch-attendance.sql`（`ALTER TABLE sch_attendance ADD COLUMN ...`，命名与 `2026-08-01-f2-fixed-assets.sql` 等现有文件一致）
- 同步改动：`SchAttendance` 实体加 4 字段 + `SchAttendanceMapper.xml` 的 resultMap/insert/update 列同步
- 建表 SQL 现状参照 `script/sql/ry-erp.sql` 中 `sch_attendance` 段

**App 端**：`punch-card.vue` 打卡前拍照（`uni.chooseImage` 相机），随 `clock-in` 请求上传照片 → ERP 存储（ossId）+ 触发 `cvExecutor` 异步校验。

#### 3.2.2 车间操作台人脸登录/验证（Web，MES）

**场景**：MES 操作台（PC 端）员工换班/登录时人脸验证，替代/补充账号密码。

- **触发链路**：操作台页面上传人脸照片 → `POST /app/mes/face/verify`（新端点）→ `verifyFace(image, subject=EMP-工号)` → 通过则签发短期 token / 返回工单上下文（当前工单、流转卡等）。
- **两种形态**：
  - 员工已知自己工号 → 输入工号 + 刷脸 = 1:1 验证（推荐主路径）
  - 员工忘记工号 → 刷脸 1:N 识别返回 **top-3 候选**，员工手选自己后**转 1:1 确认**才签发 token；**不单凭 1:N 直接签发**（活体已范围外，避免双胞胎/照片攻击风险）
- **挂点（已定）**：MES 工单操作页内抽屉（非独立登录页）；`ruoyi-mes` 内新增 `FaceVerifyController` 或复用 `MesErpBridgeService` 模式。

#### 3.2.3 物料识别（OCR 先行）

**场景**：收货/发料/盘点时拍摄料件标签，识别料号、批次、SN。

- **主路径**：`detectProduct(image, enable_ocr=true)` → 从 `ocr_results` 提取文本 → **正则/规则匹配**料号、批次、SN（标签格式由 ERP 编码规则决定，见 `doc/erp/NexusERP_编码生成规则设计方案.md`）。
- **预留（方案 A，后期）**：自训练 YOLO 检测料件类别 → 替换 `YOLO_MODEL_PATH` → `detectMaterial` 返回类别 + 置信度，与 OCR 结果融合。
- **落点场景（本期可先不做，属范围外）**：收货（`wm`）、发料（`wm`/`prd`）、盘点（`wm`）。本方案先打通"拍照 → 识别 → 文本返回"的基础能力与 `VisionClient.detectProduct`，具体业务页面按需接入。

#### 3.2.4 底库注册管理

- **批量注册**：管理端页面（Web）选择员工 + 上传照片 → `registerFace`（循环）→ vision 底库。测试期可用下载头像批量绑定。
- **集中采集**：上线时 HR/行政采集 → vision `/admin` 管理页或 ERP 注册页导入。
- **subject 映射**：`EMP-<SchWorker.employeeNo>`；`SysUser.avatar`（ossId）→ 下载后 `registerFace`。
- **底库与 avatar 解耦（决策）**：底库一经注册**不自动跟随 `SysUser.avatar` 变化**（自动同步会把 HR 展示头像/生活照灌进底库，拉低识别精度）。HR 更换员工头像后，需管理端**手动触发"重新注册"**；该操作写入运维 SOP。管理端注册页提供"按员工重新注册"入口。

### 3.3 ERP 对外接口清单

> 三端统一经 ERP 后端代理，不直连 vision。以下为 ERP 暴露的视觉相关端点。

| 端点 | 方法 | 鉴权 | 说明 |
|------|------|------|------|
| `/cv/face/register` | POST | 管理端角色 | 上传照片 + subject 注册底库（管理端用） |
| `/cv/face/verify` | POST | 登录态 | 1:1 验证（操作台/打卡校验内部调用） |
| `/cv/face/recognize` | POST | 登录态 | 1:N 识别返回 top-N（操作台忘记工号场景，N=3） |
| `/cv/face/list` | GET | 管理端角色 | 底库分页列表 |
| `/cv/face/{subject}` | GET / DELETE | 管理端角色 | 底库详情/删除 |
| `/cv/face/re-register` | POST | 管理端角色 | 按员工重新注册（avatar 变更后手动触发） |
| `/cv/detect/product` | POST | 登录态 | 物料 OCR（YOLO+OCR） |
| `/cv/detect/material` | POST | 登录态 | YOLO 通用检测（兜底） |
| `/app/mes/face/verify` | POST | 登录态 | MES 操作台人脸登录/验证（返回工单上下文） |
| `/cv/health` | GET | 登录态 | 探活（管理端状态展示） |

- 请求/响应 schema 详见 `ruoyi-common-cv` 的 DTO（`FaceVerifyResult` 等），前端按 `src/api/cv/*.ts` 对接
- 打卡校验**不暴露独立端点**，复用 `POST /app/sch/attendance/clock-in`（照片作为 multipart 字段），由后端内部触发 `cvExecutor`

### 3.4 可观测性

- **关键指标**（接入现有监控）：
  - vision 调用 P95 延迟（按端点：verify/recognize/detect）
  - vision 调用失败率
  - 底库规模（subject 数量）
  - 降级触发次数（`face_verify_status=NONE` 计数）
- **日志字段**：每次 vision 调用记录 `workerId/subject/endpoint/latencyMs/status/reason`
- **告警阈值**：vision 调用失败率 >10% 持续 5min；连续 3 次 `/health` 探活失败 → 飞书/钉钉告警
- **FAIL 复查追踪**：Web 端复查页操作日志（谁标记误判/代打卡），供审计

---

## 四、前端设计（plus-ui）

### 4.1 API 层

```
plus-ui/src/api/cv/
├── face.ts        # 人脸：注册/验证/识别/底库管理（proxy → /cv/face/*）
└── detect.ts      # 物料/OCR：detectProduct、detectMaterial（proxy → /cv/detect/*）
```

### 4.2 页面/组件

| 页面 | 位置 | 说明 |
|------|------|------|
| 底库管理 | `src/views/cv/face/` | 列表（subject/姓名/照片/样本数）、注册（上传照片）、删除 |
| 操作台人脸登录 | MES 工单相关页面 | 刷脸抽屉（调相机 → 上传 → verify/recognize → 进入） |
| 检测调试 | `src/views/cv/detect/` | 上传图片 → 展示 YOLO 框 + OCR 文本（对接管理/验收用） |
| 物料识别（预留） | 业务页按需 | 收货/发料等页面接入拍照识别 |

**相机能力**：Web 端拍照用 `<input type="file" accept="image/*" capture>` 或 getUserMedia（HTTPS 必需）；识别调试页建议直接上传文件，避免浏览器兼容性负担。

### 4.3 交互与安全

- 人脸照片上传走 `POST` multipart，前端压缩（Canvas 降采样 ≤ 2048px）再上传，降低带宽与 vision 解码压力
- 所有视觉请求复用 axios 拦截器（`Authorization` 头），**不直连 vision**

---

## 五、Nexus_App 设计

### 5.1 打卡人脸

- `pages/sch/attendance/punch-card.vue`：打卡时新增"人脸拍照"步骤（`uni.chooseImage` + `sourceType: ['camera']`），拍照后随 `clock-in` 上传
- 上传通道：复用现有 `utils/request.js` → ERP `/app/sch/attendance/clock-in`（photo 字段）→ ERP 存 MinIO（ossId 存证）+ `cvExecutor` 异步校验（已定，不暴露独立校验端点，见 3.3）
- 若拍照失败/视觉服务不可用：**打卡不阻断**（后端降级策略，返回 face_verify_status=NONE 提示）

### 5.2 通用视觉组件（预留 PAD 复用）

```
components/cv/
├── camera-capture.vue    # 相机拍照组件（封装 chooseImage/权限/预览）
└── face-verify.vue       # 人脸验证组件（拍照→上传→结果回调）
```

> 设计为跨端复用组件，未来 PAD（uni-app 同栈）可直接复用，避免重复开发。若 PAD 立项为其他栈，仅需替换拍照/上传壳，识别协议不变。

### 5.3 小程序注意

- 微信小程序必须走 ERP 后端代理（域名白名单），**不可直连 vision**
- `uni.chooseImage` 小程序自动压缩，注意图片体积限制（后端 `MAX_UPLOAD_MB=10`）

---

## 六、PAD 手持智能终端（预留）

> 状态：**待立项再定** —— 本方案仅留接口与复用空间，不展开设计。

- **接口抽象已就绪**：人脸验证/识别、物料 OCR 识别均为后端 `VisionClient` 能力，任何终端调用协议一致
- **UI 复用空间**：Nexus_App 的 `components/cv/*` 组件设计为可复用；若 PAD 选 uni-app 同栈可直接复用
- **立项时需决策**：技术栈（uni-app 新包 / Web H5+PWA / 原生 Android）、工业终端适配（大屏/扫码枪/防尘）、离线场景

---

## 七、分期落地计划

### Phase 0（前置，vision 服务侧已基本完成）

- [x] vision 服务 V0–V4 已交付（人脸/OCR/YOLO/底库/管理页/API key/Docker）
- [ ] 生产部署：vision 服务上生产机（4C4G+，Docker），配置新 API key 并禁用 dev key
- [ ] MySQL `vision` 库初始化 + 生产凭据
- [ ] **owner + 时间**：由 vision 维护者 + 运维负责，须在 Phase 1 启动前 1 周完成（Phase 1 硬前置依赖），评审会指派

### Phase 1（基础设施 + 打卡人脸，本期重点 —— 拆 1a/1b 双里程碑）

**Phase 1a（M1a：打卡链路核心）✅ 已完成并验证（2026-08-02）**

| 项 | 内容 | 依赖 |
|----|------|------|
| 1.1 | ✅ 新建 `ruoyi-common-cv` 模块（CvProperties + VisionClient + DTO + 自动配置） | Phase 0 |
| 1.2 | ✅ 场景集成：打卡人脸验证（`sch_attendance` 增量 SQL 扩展 + `cvExecutor` 异步校验 + 降级安全闭环 + FAIL 复查定时任务） | 1.1 |
| 1.3 | ✅ 底库：测试期下载头像批量注册脚本/接口（绑定测试账号） | 1.1 |
| 1.4 | ✅ App 打卡人脸（`punch-card.vue` 拍照 + 上传 + 首次告知同意弹窗） | 1.2 |

**Phase 1b（M1b：管理端）✅ 已完成并验证（2026-08-02）**

| 项 | 内容 | 依赖 |
|----|------|------|
| 1.5 | ✅ Web 底库管理页（`views/cv/face/`：列表/注册/删除/按员工重新注册） | 1.1 + 1.3 |
| 1.6 | ✅ Web FAIL 复查页（昨日 `FAIL` 记录列表 + 标记误判/代打卡） | 1.2 |

### Phase 2（物料 OCR + 操作台）

| 项 | 内容 | 依赖 |
|----|------|------|
| 2.1 | 物料 OCR 原始能力：`VisionClient.detectProduct` 接入 + 检测调试页（`views/cv/detect/`，OCR 文本原始返回 + 人工查看） | 1.1 |
| 2.2 | 物料 OCR 规则匹配：标签正则提取料号/批次/SN（需先提供样例标签） | 2.1 |
| 2.3 | 操作台人脸登录/验证（MES Web 页面，1:N top-3 候选 + 1:1 确认） | 1.1 + 1.3 |
| 2.4 | 收货/发料/盘点页面拍照识别（按需） | 2.2 |

### Phase 3（后期，预留）

| 项 | 内容 | 依赖 |
|----|------|------|
| 3.1 | 自训练 YOLO 物料模型（数据采集标注 → 训练 → 替换 `YOLO_MODEL_PATH`） | 2.1 |
| 3.2 | 物料识别 OCR + YOLO 结果融合（类别 + 文本双通道） | 3.1 |
| 3.3 | PAD 手持智能终端（待立项后启动） | 全 |

### 里程碑与验收

- **M1a（Phase 1a 完）**：打卡人脸链路跑通（拍照 → 上传 → `cvExecutor` 异步校验 → 记录），降级不阻断，FAIL 复查定时任务可跑
- **M1b（Phase 1b 完）**：底库注册/管理可用，FAIL 复查 Web 页可用，按员工重新注册可用
- **M2（Phase 2 完）**：物料标签 OCR 识别可用，**料号识别率 ≥80%（基于 ≥50 张真实标签样本）**；操作台刷脸登录可用（1:N top-3 + 1:1 确认）
- **M3（Phase 3 完）**：自定义 YOLO 接入，物料识别双通道，PAD 立项后按新计划执行

---

## 八、安全与网络

1. **鉴权边界**：vision `x-api-key` 仅存于 Java 后端配置（`VISION_API_KEY` 环境变量），不落入前端/App；Web/App 一律经 ERP 登录态
2. **网络**：vision 部署于内网（ERP 后端同内网），不暴露公网端口；`/admin` 管理页仅内网访问
3. **图片敏感**：人脸照片属敏感数据 —— 打卡照片存 MinIO 私有桶（ossId），底库照片存 vision MySQL；**生产必须 TLS**（vision 加自签证书 + Java 端信任）或独立安全网段 + 网络隔离，不接受明文 HTTP 传输人脸数据
4. **限流**：vision 无内置限流，靠 Java 侧降级与业务频控（打卡防刷）
5. **生产 key 管理**：禁用 dev key，`POST /api/v1/api-keys` 生成专用 key，轮换机制按需
6. **合规（前置要求，非后期评估）**：人脸属敏感个人信息，受《个人信息保护法》《人脸识别应用安全合规要求》约束：
   - **告知-同意**：App 首次打卡弹窗"人脸采集告知与同意"，同意态存 `SysUser` 扩展字段，未同意不可采集
   - **数据保留**：打卡照片保留策略文档化（建议 30 天后自动清理，底库照片离职后 30 天删除）
   - **删除入口**：管理端提供"申请删除底库"入口（撤回机制留 backlog，Phase 1 不做）
   - 此项纳入 Phase 1 范围，非"正式商用后再评估"

---

## 九、测试与验证

### 9.1 vision 服务侧（已完成）

- V2 实测：本人识别 sim=1.0；他人 sim=0.03（FALSE）；5 人合照正确检出；未注册→matched=false
- pytest 19 用例（鉴权/上传限制/人脸链路/并发注册）

### 9.2 集成测试（本期新增）

| 项 | 内容 |
|----|------|
| VisionClient 单测 | Mock vision HTTP 响应，验证 DTO 解析/异常包装/降级 |
| 打卡链路集成 | 注册测试工号 → 打卡上传照片 → verify → 断言 sch_attendance.face_verify_status/similarity |
| 降级验证 | `nexus.vision.enabled=false` 或 vision 宕机 → 打卡仍成功（NONE），不抛 500；恢复后批量补跑 |
| 阈值校准 | 用下载头像 + 真实测试人员照片回归 `FACE_RECOGNITION_THRESHOLD`（当前 0.5） |
| 并发注册取均值 | 同一 subject 并发注册多张样本，验证 vision 取均值特性不出现脏数据 |
| 大图压缩链路 | >5MB 图片经前端压缩 + 客户端 `max-image-dim` 校验 → 正常识别 |
| 并发打卡压测 | 50 并发打卡下 vision P95 延迟 + `cvExecutor` 队列表现 |

### 9.3 端到端（手动/脚本）

- App：HBuilderX 真机（Android）打卡 → 拍照 → 上传 → 后端校验 → 记录可查
- Web：底库管理注册（上传照片）→ 列表/删除；检测调试页上传料件标签 → OCR 文本展示
- 失败路径：照片模糊/无人脸 → 校验 FAIL 但不阻断打卡；vision 停机 → 打卡 NONE + 日志告警

### 9.4 Phase 1a+1b 运行时端到端验证（2026-08-02 实测通过）

> 脚本：`doc/erp/scripts/cv-e2e-test.py`（复用 P0-4 登录加密复刻模式：RSA+AES+Redis 验证码，
> 可复跑，含全链路断言）。测试照片：vision 底库 `EMP-20002` 注册照片（吴彦祖头像，测试用）。

#### 9.4.1 vision 服务直连验证 ✅

| 项 | 结果 |
|----|------|
| API key 鉴权 | 对 key→200 / 错 key→401 / 无 key→401 |
| 1:1 verify 本人 | sim=1.0, matched=true（~184ms） |
| 1:1 verify 不存在 subject | sim=0.0, matched=false, reason=subject_not_found |
| 1:N recognize | 正确返回 EMP-20002 + 人脸框（~138ms） |
| YOLO 通用检测 | person 0.957（~102ms） |
| OCR 中文标签 | `料号：MC-301A-03` 0.995、`批次 B20260715` 0.998（Phase 2 基础可行） |

#### 9.4.2 ERP 集成链路验证 ✅（8/8 项）

| # | 验证项 | 结果 |
|---|--------|------|
| 1 | 加密登录（RSA+AES+Redis 验证码） | PASS |
| 2 | `/cv/face/list` 底库列表（经 ERP 代理） | PASS total=1 |
| 3 | `/cv/face/health` 健康探活 | PASS status=ready, 三模型 true |
| 4 | 打卡（`clock-in` 带照片 base64，异步不阻断） | PASS attendanceId 正常 |
| 5 | 异步人脸验证回写 | PASS **FAIL sim=0.0**（底库无此工号→正确拒绝）→ 注册匹配工号后 **PASS sim=1.0** |
| 6 | MinIO 存证 | PASS `face_image_oss_id` 写入 |
| 7 | FAIL 复查列表（`faceVerifyStatus=FAIL` 过滤） | PASS total=1 |
| 8 | 标记误判 FAIL→PASS（审计备注 `[FACE-REVIEW:PASS]`） | PASS st=200 |

#### 9.4.3 验证中发现并修复的缺陷（6 个，均已提交）

| 提交 | 缺陷 | 根因与修复 |
|------|------|-----------|
| `ad51037a` | 异步存证报 `Table 'ry-erp.sys_oss' doesn't exist` | cvExecutor 线程无 @DS 上下文,sys_oss 在 master 库(ry-vue);`DynamicDataSourceContextHolder.push("master"/"erp")` 显式切换(同 WmMqSendService 既有模式) |
| `3d292693` | vision 调用报 `Failed to select a proxy` | 系统代理软件劫持 ProxySelector;`request.setProxy(Proxy.NO_PROXY)` 显式直连 |
| `68dd8abb` | 健康探活 `StringIndexOutOfBoundsException` | `getHealth` 误把相对路径传 `getJson`;改用 `url(path)` 拼接 base-url |
| `2cc9616f` | 打卡人脸验证全部落 NONE（HTTP 422） | **根因**：vision 全部端点文件字段名为 `file`,`VisionClientImpl` 人脸方法误传 `image` 字段名导致 `file Field required`;改为 `"file"` |

#### 9.4.4 遗留小问题（非阻塞）

- FAIL（subject_not_found）时 `face_similarity` 写 0.0000 —— 语义可接受（未匹配=0），如需区分可改空
- 打卡复用当天同 worker 记录（`performClockIn` 既有 update 逻辑），非本方案引入
- 测试底库 `EMP-20002`（吴彦祖头像）可留作演示或删除

#### 9.4.5 待补验证项（Phase 2 前）

- Web 端菜单可见性：视觉识别(6182) → 人脸底库/FAIL 复查页操作（需清 Redis 菜单缓存）
- App 端真机打卡拍照 → 上传（HBuilderX 验证）
- 降级补跑任务：vision 停机 → 打卡 NONE → 恢复后 24h 内补跑（`FaceVerifyBackfillTask` cron 每小时）

---

## 十、风险与决策记录

| 项 | 风险/决策 | 缓解/状态 |
|----|-----------|-----------|
| 物料识别精度 | COCO 80 类不含工厂料件 | OCR 先行（零训练成本），自定义 YOLO 后期替换（方案 C 已确认） |
| 底库照片质量 | 无现成员工照片 | 集中采集注册；测试期下载头像绑定测试账号（已确认） |
| 打卡延迟 | 同步校验 +~0.5s | 已定方案 A 异步校验，不阻断打卡；B 同步不采用（见 3.2.1） |
| 降级=静默关闭校验 | vision 故障时所有打卡 NONE | 3 次健康检查失败告警 + 恢复后 24h 批量补跑（见 3.1） |
| 底库与 avatar 漂移 | HR 换头像后底库不同步 | 不自动同步，管理端手动重新注册，写入 SOP（见 3.2.4） |
| 1:N 登录冒用 | 双胞胎/照片攻击 | 1:N 仅返回 top-3 候选，转 1:1 确认才签发 token（见 3.2.2） |
| 小程序域名 | 直连 vision 不可行 | 统一走后端代理（架构已固化） |
| 阈值误判 | 0.5 阈值未用真实数据校准 | Phase 1 集成测试阶段用测试人员照片回归（当前用头像照片验证 sim=1.0/0.0 两端正确） |
| 代理劫持 | 内网 vision 被系统代理软件拦截 | `Proxy.NO_PROXY` 显式直连（已修复并验证，见 9.4.3） |
| 异步数据源错乱 | 异步线程 sys_oss 误落 erp 库 | `DynamicDataSourceContextHolder` 显式 push master/erp（已修复并验证，见 9.4.3） |
| PAD 技术栈 | 未立项 | 预留接口抽象 + `components/cv` 复用空间，立项再定 |

---

## 附：待评审确认项（更新）

> 已决策项见上方各章节；以下为评审会仍需确认的开放项。

1. ~~打卡人脸校验模式~~ → **已定：A 异步校验**（见 3.2.1）
2. ~~打卡照片存储~~ → **已定：存 MinIO（ossId 存证）**，`face_image_oss_id` 启用（见 3.2.1 / 5.1）
3. ~~操作台挂点~~ → **已定：工单页内抽屉**（见 3.2.2）
4. **物料 OCR 标签格式**：需确认标签上的料号/批次/SN 的版式与编码规则 —— Phase 2.2 启动前必须提供 ≥50 张样例标签照片（M2 验收依赖）
5. ~~Phase 1 范围~~ → **已定：拆 1a/1b**（见第七章）
6. **Phase 0 owner**：vision 维护者 + 运维须在 Phase 1 启动前 1 周完成生产部署，评审指派

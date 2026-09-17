# Nexus-ERP 电子签章能力建设方案（内部留痕级）

> 状态：**P1 已实施并完成端到端验收（核心路径）** —— 选型结论已定：**纯自建，不融合开源代码、不接 CA/TSA/商业 API**
> 实施分支：`feat/esignature-p1`（未合并）。全仓构建通过；`ruoyi-common-sign` **96 个单测全绿**、`ruoyi-mes` **261 个**；静态一致性审计通过；
> **端到端验收 12/12 全部通过**（真实 MySQL + Redis + 后端），过程中发现并修复 **2 个致命缺陷** —— 见 §10.4。
> 日期：2026-09-15
> 关联规划：MES P0-6「电子签名 + DHR 批记录」（`doc/PROGRESS.md:975`、`doc/system/2026-07-29-system-gap-analysis-vs-mainstream.md:69`）
> 关联文档：[2026-09-06-全项目审查报告.md](../2026-09-06-全项目审查报告.md) 缺失项 #6

---

## 〇、背景、结论与范围

### 0.1 需求

流程审批与合同签署场景下，当前操作人需要能调用**个人签章**或**公司签章**完成签署。

### 0.2 选型结论

| 方案 | 结论 | 决定性依据 |
|------|------|-----------|
| **A. 自建** | ✅ **采用** | 密码学能力已在依赖树（Bouncy Castle 1.83）；审批钩子、文件存储、企业主体信息全部就绪；工作量集中在"串联"而非"造轮子" |
| **B. 融合开源代码** | ❌ **不可行** | ①国内唯一同栈项目开放签**核心是二进制 JAR，无源码可融**；②AGPL-3.0 传染，与商业 ERP 不兼容；③JDK 1.8（<17）+ Spring Boot 2.x + Shiro，与本项目 JDK 17 + Boot 3.5.12 + Sa-Token **差一代** |
| **C. 商业 API（e签宝/法大大等）** | ❌ **本期不需要** | 仅当需要**对外法律效力 / 司法出证**时才必要。本期定位为内部留痕，接商业 API 属过度设计 |
| **D. 开源项目服务级集成** | ⚠️ **不采用** | 技术上可行（AGPL 不传染调用方），但需引入第二套流程引擎 + PowerJob 调度中心 + 独立多租户体系，架构债大于收益 |

### 0.3 范围界定（**关键**）

**本期定位：可追溯、防篡改、可审计的「内部管理凭证」**，而非对抗第三人的法律效力文书。

这一界定直接决定：
- ✅ 不需要外部 CA 数字证书
- ✅ 不需要可信时间戳（TSA）服务
- ✅ 不需要司法存证 / 出证服务
- ✅ **零采购、零外部依赖**

**范围外（本期不做）**：
- 外部对手方参与的双方/多方签署（客户、供应商侧签署入口）
- 对外合同的法律效力保障（CA 证书、TSA、司法存证）
- Word → PDF 转换（Word 不能直接盖章；本期仅接收 PDF，界面引导用户先转换）
- DHR 批记录导出（P0-6 的另一半，独立立项）

### 0.4 前置缺口（必须先补）

> **合同表没有正文文档。** `SoContract` / `PuContract` 仅有 `contractNo` / `contractName` / `totalAmount` / `signDate` / `status` 等结构化字段，**无正文附件字段**。

签章的前提是有一份文档。本方案用 `es_sign_document` 表以 `(biz_type, biz_id)` 通用键补齐任意单据的正文载体，**不改动现有 20 个业务表**。

---

## 一、现状盘点（代码事实，均已核对）

### 1.1 已有资产（可直接复用）

| 资产 | 位置 | 复用方式 |
|------|------|---------|
| **国密 SM2/SM3/SM4** | `bcpkix-jdk18on` 1.83（`ruoyi-common-encrypt/pom.xml`） | 文档哈希 + 哈希链，**零新增依赖** |
| **CMS / PKCS#7** | BC `org.bouncycastle.cms`（随 bcpkix 传递依赖） | PDF 数字签名 |
| **RFC3161 时间戳** | BC `org.bouncycastle.tsp` | P3 预留（本期不用） |
| **审批统一钩子** | `ruoyi-flowable` `WfCallBackServiceI.afterFlowHandle()` | 签名挂载点 |
| **已接入审批的单据** | 约 20 个（销售合同、采购合同、销售订单、退货、发票、采购申请/订单/退货、到货通知、调拨、盘点、出库、工单、固件、SOP、发布包…） | 一次插桩全覆盖 |
| **多租户插件** | `ruoyi-common-tenant` | 印章/授权/签名记录自动隔离 |
| **对象存储** | MinIO 私有桶 + `sys_oss_ext`（`bizType/refId/refType/tags/md5`） | 印章图、正文、盖章件存储 |
| **私有桶 URL 翻译** | `@Translation(type = TransConstant.OSS_ID_TO_URL)` | 临时签名 URL |
| **企业主体信息** | `SysTenant.companyName` / `licenseNumber` / `address` / `contactPhone` | 公司签章主体字段 |
| **人脸能力** | `ruoyi-common-cv` `VisionClient`（1:1 验证） | 二级认证可选项（P2） |
| **短信能力** | `ruoyi-common-sms` | 二级认证可选项 |
| **Redis / Lock4j** | `ruoyi-common-redis` | 哈希链串行化、防并发 |

### 1.2 缺失资产（需新增）

| 缺失 | 影响 | 补齐方式 |
|------|------|---------|
| 任何签章实体/表 | 全部 | 本方案第 3 章 |
| PDF 处理库 | 无法盖章 | 新增 Apache PDFBox 3.x（`Apache-2.0`，**无许可风险**） |
| 前端 PDF 预览 | 无法拖拽定位盖章 | 新增 `pdfjs-dist`（`Apache-2.0`） |
| 用户实名信息 | 强实名做不到 | 本期不做（内部留痕只需"账号 + 二级认证"） |
| 合同正文文档 | 无物可签 | `es_sign_document` 表 |

### 1.3 关键挂载点（已定位）

| 场景 | 代码位置 | 当前状态 |
|------|---------|---------|
| 审批操作 UI | `plus-ui/src/views/workflow/components/ActHandleBtn.vue` | 仅有"处理意见"输入框 |
| 审批提交 | `ruoyi-flowable` `WfTaskServiceImpl.complete()` | 无签名环节 |
| MES SOP 查阅 | `SopViewLogServiceImpl.recordView()` | `signature_id = null`，注释标注"预留 P0-6" |
| MES NCR 处置 | `NcrOrderServiceImpl.bindDispositionSignature()` | 空实现，注释标注"P0-6 预留" |
| 合同详情 | `plus-ui/src/views/erp/so/contract/index.vue`、`.../pu/contract/index.vue` | 仅 `signDate` 日期字段 |

### 1.4 表落位约定

跨模块公共表落 **`ry-vue` 库**（对齐既有 `ai_flow*` 系列：`script/sql/ry-vue.sql`）。

---

## 二、为什么排除「融合开源代码」（证据留档）

以国内技术栈最接近的 **开放签 `kaifangqian-base`**（Java + Vue 3）为例：

### 2.1 核心能力是二进制 JAR，无源码

`kaifangqian-parent/kaifangqian-tools/libs/` 内含 12 个预编译 jar，其中包含：

```
resrun-pdfbox-1.1-SNAPSHOT.jar        ← PDF 签章核心
resrun-certificate-1.1-SNAPSHOT.jar   ← 数字证书核心
core-1.1-SNAPSHOT.jar
common-1.1-SNAPSHOT.jar
```

`pom.xml` 通过 `maven-install-plugin:install-file` 从本地 `libs/*.jar` 安装为依赖。**仓库内不存在这些模块的源码** —— 这是 open-core 商业模式，不是完整开源。

### 2.2 许可证传染

README 原文：

> 本项目基于 AGPLv3 协议开源。
> **重要说明**：根据 AGPLv3 协议，如果您通过网络提供服务（如 Web 应用），必须公开修改后的完整源代码（包括衍生作品）。

Nexus-ERP 为 MIT 商业系统，代码级融合将使整个 ERP 受 AGPL 约束。

### 2.3 技术栈代差

| 维度 | Nexus-ERP | 开放签 |
|------|-----------|--------|
| JDK | **17** | 1.8+（README：*"仅在 jdk1.8+ 下运行和测试"*） |
| Spring Boot | **3.5.12** | 2.x（`javax.*`） |
| 命名空间 | `jakarta.*` | `javax.*` |
| 安全框架 | Sa-Token | Shiro + Spring Security |
| 调度 | SnailJob | **强依赖 PowerJob 4.0.1，不启动则应用起不来** |
| 字体 | — | 需在宿主机安装 `simsun.ttc` 等中文字体 |

### 2.4 信任根仍需采购

> 部署完成后，如需使用认证的 CA 数字证书签署，请联系官方获取云盾服务授权码。

### 2.5 值得借鉴的设计（仅借鉴思路，不复制代码）

- **印章三权分立**：管理权 / 使用权 / 审计权分离 → 本方案 `es_seal_grant` 授权模型参考此思路
- **业务线概念**：按场景配置签署流程
- **关键字定位签署位置**：PDF 中按"甲方（盖章）"文字定位盖章锚点 → 本期用拖拽定位，关键字定位列为 P3 增强

### 2.6 开源生态现状（GitHub 检索实证，2026-09-15）

中文关键词检索结果（`sort=stars`）：

| 项目 | Stars | 语言 | 许可证 | 结论 |
|------|-------|------|--------|------|
| DocuSeal | 18,532 | Ruby | AGPL-3.0 | 西方场景，无国密、无中国 CA/司法存证 |
| Documenso | 15,026 | TypeScript | AGPL-3.0 | 同上 |
| OpenSign | 6,992 | JavaScript | 非标准（NOASSERTION） | 同上 |
| kaifangqian-base（开放签） | 501 | Vue | AGPL-3.0 | 见 2.1-2.4 |
| Contract.Pro / mini-contract（何方签） | 314 / 76 | uni-app x（UTS/uvue） | **无 LICENSE 文件** | 见 2.7 |
| 其余「电子签章/电子印章」检索 | — | — | — | **无任何可用项目**（命中均为无关仓库） |

### 2.7 Contract.Pro / mini-contract（何方签）评估结论 —— 已排除

> 由用户 2026-09-15 提议评估：<https://gitee.com/leepm/mini-contract>（GitHub 镜像 `freeleepm/mini-contract`）。以下为实测证据，结论：**不可用于本项目，且不可作为引擎替代方案。**

**① 它不是一个签章系统，而是一个 uni-app 客户端外壳**

| 证据 | 内容 |
|------|------|
| `ARCHITECTURE.md` 标题 | 「Mini-Contract **前端**业务架构文档」 |
| 根目录结构 | `App.uvue` / `main.uts` / `pages.json` / `uni_modules/` —— 典型 uni-app x 工程 |
| `package.json` | name=`何方签`，依赖仅 `@dcloudio/uni-ui` + `uqrcodejs`；构建走 `@dcloudio/uniapp-cli` |
| `ARCHITECTURE.md` 分层图 | 第 5 层「☁️ 后端服务 Backend Services」为**外部**依赖，不在本仓库 |
| `config/app.config.uts` | `production: '/app-api'`、dev `http://localhost:48080/app-api`、`TENANT_ID='1'` —— 指向一个**未开源的 yudao 系后端** |

**② 真正的签章能力是作者托管的闭源服务（Seal Core）**

`config/app.config.uts` 原文：

```js
export const SEAL_CORE_H5_URLS = {
    'development': 'http://localhost:3100',
    'testing': 'https://test-seal.example.com',
    'production': 'https://h5-sign.leepm.com'   // ← 签章引擎在此，闭源
};
```

`config/seal.config.uts` 原文注释：

> 移动端通过 `<web-view>` 组件加载 **seal-core 的 H5 页面**，本质上是移动端的一部分

并配套 `utils/sealTokenManager.uts`（11.7KB）做 **Seal Token 兑换**。即：开源仓库通过 token 换取对作者托管页面的访问权，**PDF 签章 / 印章管理 / CA 全部在闭源侧**。

**③ 许可证状况：无有效许可证**

README 徽章声称 `Apache License 2.0` 并链接到 `LICENSE`，但实测：

| 检查项 | 结果 |
|--------|------|
| GitHub `contents/LICENSE` | **404** |
| GitHub `license` API（SPDX） | **404**（无许可证被识别） |
| Gitee `contents/LICENSE` | **HTTP 200，body 为 `[]`**（路径不存在） |
| Gitee `raw|blob/master/LICENSE` | **404** |
| Gitee 仓库 `license` / `license_url` 字段 | 均为空 |

**无许可证 = 默认保留所有权利**（著作权法默认原则）。描述中的「永久免费开源」是宣传文案，**不构成授权**。徽章不是法律文件。

**④ 「双模式引擎兼容腾讯电子签/法大大/君子签/e签宝CA标准」是 README 营销文案，非开源实现**

实测关键词命中：`ARCHITECTURE.md` **零命中**；`README.md` 中「法大大 / 君子签 / e签宝」**各仅 1 次**（出现在宣传段落）；代码中不存在任何厂商适配层。适配层在闭源 Seal Core 内。

**⑤ 与本项目定位冲突**

引入它会把已确认的「内部留痕、零采购、零外部依赖」**反转为依赖第三方 SaaS**：签署页面在 `h5-sign.leepm.com`，会话靠 `Seal Token` 兑换。且它是 C 端 App，**不提供 ERP 集成路径**（即便要用其 SaaS，也是对 `s.leepm.com` 的 API 直连，与 fork 此仓库无关）。

**⑥ 技术栈不匹配**：uni-app x（uvue/UTS）移动端 ≠ plus-ui（Vue 3 + Vite + Element Plus）。

**可借鉴之处（仅思路，不可复制代码——无许可证）**：

- **跨端签署会话**：它用 Seal Token 解决「uni-app 原生页 ↔ H5 WebView」的跨域签署会话传递。本项目 Web 同源，无需此机制；**但若未来把签署下沉到 PDA/App 端，需要类似方案**（见第八章 P3）。
- **签署 token 分端设有效期**（移动端 30 天 / PC iframe 2 小时）——
  ⚠️ **本项目不应采纳此模式**：GMP 类内控要求**每次签名独立认证**，长效会话 token 会削弱「只有本人能签」的保证。此处是**反面参考**。
- **个人 KYC / 企业 KYC 交互分离**（`KycModal` / `EnterpriseKycModal`）：与本文档「个人章 / 公司章」分离一致，可作为 P3「对外签署 + 实名认证」阶段的交互参考。

**结论**：中文开源电子签章生态基本空白；可用的成熟项目全为西方场景 + AGPL，在「中国合规」路径上增益为 0，且与本项目已有的 Flowable 审批流**功能重叠**。

---

## 三、数据模型（4 张表，落 `ry-vue`）

> 全部继承 `TenantEntity`（`tenant_id` 由多租户插件自动维护），主键采用项目统一 `ASSIGN_ID`（雪花）。

### 3.1 `es_seal` —— 印章

```sql
CREATE TABLE `es_seal` (
  `seal_id`       bigint       NOT NULL                COMMENT '印章ID',
  `tenant_id`     varchar(20)  NOT NULL                COMMENT '租户ID',
  `seal_type`     char(1)      NOT NULL                COMMENT '印章类型(1个人章 2公司章)',
  `seal_name`     varchar(64)  NOT NULL                COMMENT '印章名称',
  `owner_user_id` bigint       NULL     DEFAULT NULL   COMMENT '个人章归属用户ID(公司章为NULL)',
  `seal_oss_id`   bigint       NOT NULL                COMMENT '印章原图OSS ID(私有桶,仅服务端使用)',
  `preview_oss_id` bigint      NULL     DEFAULT NULL   COMMENT '预览图OSS ID(低清+水印,前端可见)',
  `img_width`     int          NOT NULL                COMMENT '印章原图宽(px)',
  `img_height`    int          NOT NULL                COMMENT '印章原图高(px)',
  `status`        char(1)      NOT NULL DEFAULT '0'    COMMENT '状态(0草稿 1启用 2停用 3销毁)',
  `seal_version`  int          NOT NULL DEFAULT 1      COMMENT '印章版本(更换章面自增)',
  `valid_from`    date         NULL     DEFAULT NULL   COMMENT '生效日期',
  `valid_to`      date         NULL     DEFAULT NULL   COMMENT '失效日期',
  `remark`        varchar(255) NULL     DEFAULT NULL   COMMENT '备注',
  `create_by`     bigint       NULL, `create_time` datetime NULL,
  `update_by`     bigint       NULL, `update_time` datetime NULL,
  `del_flag`      char(1)      NOT NULL DEFAULT '0',
  PRIMARY KEY (`seal_id`),
  KEY `idx_es_seal_tenant_type` (`tenant_id`, `seal_type`),
  KEY `idx_es_seal_owner` (`owner_user_id`)
) COMMENT='电子印章';
```

**约束**：个人章 `owner_user_id` 必填且 `seal_type=1`；公司章 `owner_user_id` 必须为 `NULL` 且 `seal_type=2`。

### 3.2 `es_seal_grant` —— 用章授权（公司章治理核心）

```sql
CREATE TABLE `es_seal_grant` (
  `grant_id`        bigint      NOT NULL              COMMENT '授权ID',
  `tenant_id`       varchar(20) NOT NULL              COMMENT '租户ID',
  `seal_id`         bigint      NOT NULL              COMMENT '印章ID',
  `grantee_user_id` bigint      NULL   DEFAULT NULL   COMMENT '被授权用户ID(用户级授权)',
  `grantee_dept_id` bigint      NULL   DEFAULT NULL   COMMENT '被授权部门ID(部门级授权)',
  `biz_type`        varchar(32) NULL   DEFAULT NULL   COMMENT '限定业务场景(NULL=不限)',
  `can_sign_alone`  char(1)     NOT NULL DEFAULT '0'  COMMENT '可否单独用章(0需会同 1可单独)',
  `valid_from`      datetime    NULL   DEFAULT NULL   COMMENT '授权生效时间',
  `valid_to`        datetime    NULL   DEFAULT NULL   COMMENT '授权失效时间',
  `status`          char(1)     NOT NULL DEFAULT '1'  COMMENT '状态(0停用 1启用)',
  `grant_by`        bigint      NULL   DEFAULT NULL   COMMENT '授权人用户ID',
  `remark`          varchar(255) NULL  DEFAULT NULL,
  `create_by` bigint NULL, `create_time` datetime NULL,
  `update_by` bigint NULL, `update_time` datetime NULL,
  `del_flag`  char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`grant_id`),
  KEY `idx_es_grant_seal` (`seal_id`, `status`),
  KEY `idx_es_grant_user` (`grantee_user_id`, `status`)
) COMMENT='印章使用授权';
```

**约束**：`grantee_user_id` 与 `grantee_dept_id` 至少一个非空（CHECK 由服务层保证）。

### 3.3 `es_sign_document` —— 待签正文载体（补齐"合同无正文"缺口）

```sql
CREATE TABLE `es_sign_document` (
  `doc_id`        bigint       NOT NULL              COMMENT '文档ID',
  `tenant_id`     varchar(20)  NOT NULL              COMMENT '租户ID',
  `biz_type`      varchar(32)  NOT NULL              COMMENT '业务类型(SO_CONTRACT/PU_CONTRACT/...)',
  `biz_id`        bigint       NOT NULL              COMMENT '业务单据ID',
  `file_oss_id`   bigint       NOT NULL              COMMENT '正文文件OSS ID(仅PDF)',
  `file_name`     varchar(255) NOT NULL              COMMENT '原始文件名',
  `file_size`     bigint       NULL DEFAULT NULL     COMMENT '文件大小(字节)',
  `file_hash`     varchar(64)  NOT NULL              COMMENT '上传时SM3值(验签比对基准)',
  `page_count`    int          NULL DEFAULT NULL     COMMENT 'PDF页数(上传时解析)',
  `doc_version`   int          NOT NULL DEFAULT 1    COMMENT '正文版本',
  `is_current`    char(1)      NOT NULL DEFAULT '1'  COMMENT '是否当前生效版本(1是 0否)',
  `signed_oss_id` bigint       NULL DEFAULT NULL     COMMENT '签章后文件OSS ID',
  `sign_status`   char(1)      NOT NULL DEFAULT '0'  COMMENT '签署状态(0未签 1部分签 2已签)',
  `upload_by`     bigint       NULL, `upload_time` datetime NULL,
  `create_by` bigint NULL, `create_time` datetime NULL,
  `update_by` bigint NULL, `update_time` datetime NULL,
  `del_flag`  char(1) NOT NULL DEFAULT '0',
  PRIMARY KEY (`doc_id`),
  KEY `idx_es_doc_biz` (`tenant_id`, `biz_type`, `biz_id`, `is_current`)
) COMMENT='待签文档正文';
```

> **设计说明**：以 `(biz_type, biz_id)` 为通用键，**无需修改任何现有业务表**即可为全部单据类型提供正文载体。`sys_oss_ext` 的 `refType/refId` 字段虽已存在，但缺少版本与"当前生效"语义，且写入时机不可控，故不复用。

### 3.4 `es_signature` —— 签名记录（统一签名域，append-only）

```sql
CREATE TABLE `es_signature` (
  `signature_id`      bigint       NOT NULL              COMMENT '签名ID',
  `tenant_id`         varchar(20)  NOT NULL              COMMENT '租户ID',
  `biz_type`          varchar(32)  NOT NULL              COMMENT '业务类型(APPROVAL/SOP_VIEW/NCR_DISPOSITION/SO_CONTRACT/PU_CONTRACT)',
  `biz_id`            bigint       NULL DEFAULT NULL     COMMENT '业务单据ID',
  `biz_no`            varchar(64)  NULL DEFAULT NULL     COMMENT '业务单据编号(冗余快照)',
  `signer_user_id`    bigint       NOT NULL              COMMENT '签署人用户ID',
  `signer_name`       varchar(64)  NOT NULL              COMMENT '签署人姓名(冗余快照)',
  `signer_dept_id`    bigint       NULL DEFAULT NULL     COMMENT '签署人部门ID(快照)',
  `seal_id`           bigint       NULL DEFAULT NULL     COMMENT '所用印章ID(逻辑签名可空)',
  `seal_type`         char(1)      NULL DEFAULT NULL     COMMENT '印章类型快照(1个人 2公司)',
  `signature_meaning` varchar(128) NOT NULL              COMMENT '签署含义(如"审批通过""已阅确认""处置批准")',
  `auth_method`       varchar(16)  NOT NULL              COMMENT '认证方式(PASSWORD/SMS/FACE/LOGIN_ONLY)',
  `src_doc_oss_id`    bigint       NULL DEFAULT NULL     COMMENT '签署前文档OSS ID',
  `src_doc_hash`      varchar(64)  NULL DEFAULT NULL     COMMENT '签署前文档SM3',
  `doc_oss_id`        bigint       NULL DEFAULT NULL     COMMENT '签署后文档OSS ID',
  `doc_hash`          varchar(64)  NULL DEFAULT NULL     COMMENT '签署后文档SM3',
  `doc_id`            bigint       NULL DEFAULT NULL     COMMENT '关联 es_sign_document.doc_id',
  `signed_at`         datetime     NOT NULL              COMMENT '签署时间(服务端时间)',
  `sign_ip`           varchar(64)  NULL DEFAULT NULL     COMMENT '签署IP',
  `sign_ua`           varchar(512) NULL DEFAULT NULL     COMMENT '签署User-Agent',
  `prev_hash`         varchar(64)  NULL DEFAULT NULL     COMMENT '前一条链哈希(链首为NULL)',
  `chain_hash`        varchar(64)  NOT NULL              COMMENT '本条链哈希(SM3)',
  `remark`            varchar(255) NULL DEFAULT NULL,
  PRIMARY KEY (`signature_id`),
  KEY `idx_es_sig_biz` (`tenant_id`, `biz_type`, `biz_id`),
  KEY `idx_es_sig_signer` (`signer_user_id`, `signed_at`),
  KEY `idx_es_sig_chain` (`tenant_id`, `signature_id`)
) COMMENT='电子签名记录';
```

**append-only 约束**（应用层保证，非数据库强制）：
- Service 层**不暴露任何 UPDATE / DELETE 方法** —— 不可变性由接口层面保证
- 表结构**不含 `update_by` / `update_time` / `del_flag`**；实体类对应字段声明为 `@TableField(exist = false)` 或直接不声明 —— 使"改写"在代码层面无从表达
- 表结构**亦不含 `create_by` / `create_time`**：本表一行即一次签名，`signer_user_id` / `signer_dept_id` / `signed_at`
  已完整承载"谁在何时签署"的语义，再加一对通用审计字段属冗余，且会与 MyBatis-Plus 的
  `MetaObjectHandler` 自动填充交互。实体类**不继承** `BaseEntity`/`TenantEntity`，声明为普通 POJO
  （`tenant_id` 由 MyBatis-Plus 多租户插件在 SQL 层注入，不依赖实体继承）
- 无逻辑删除：签名记录一经写入永久保留
- 数据库账号权限不设限，不可变性由应用层与哈希链共同保证（哈希链提供**事后可检测**能力）

---

## 四、服务设计

### 4.1 模块落位

| 项 | 位置 |
|----|------|
| 后端 | 新建 `ruoyi-common-sign`（对齐 `ruoyi-common-cv` / `ruoyi-common-flow` 跨模块公共层模式） |
| 自动装配 | `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（项目统一约定，非组件扫描） |
| 表脚本 | `script/sql/ry-vue.sql` |
| 前端页面 | `plus-ui/src/views/system/sign/{seal,grant,record}/index.vue` |
| 前端组件 | `plus-ui/src/components/sign/` |
| API 层 | `plus-ui/src/api/system/sign/` |

### 4.2 核心接口

```java
public interface ESignatureService {

    /**
     * 逻辑签名：审批签批 / SOP 查阅 / NCR 处置。
     * 不产出新文件，仅落签名记录并接入哈希链。
     *
     * @return 签名ID（回填业务表的 signature_id 字段）
     */
    Long sign(SignCommand cmd);

    /**
     * 文档签章：在待签 PDF 上盖章 → 产出新文件 + 数字签名 + 签名记录。
     *
     * @return 签名ID
     */
    Long signDocument(DocSignCommand cmd);

    /** 验签：校验哈希链完整性 + 文档是否被篡改 */
    VerifyResult verify(Long signatureId);

    /** 全链校验（管理端巡检） */
    ChainVerifyResult verifyChain(String bizType, Long bizId);

    /** 按业务查询签名列表 */
    List<SignatureVo> listByBiz(String bizType, Long bizId);

    /** 查询当前用户在某业务场景下可用的印章 */
    List<AvailableSealVo> listAvailableSeals(String bizType);
}
```

```java
public record SignCommand(
    String  bizType,           // 必填
    Long    bizId,             // 可空（如纯审批动作）
    String  bizNo,             // 可空
    String  signatureMeaning,  // 必填：签署含义
    Long    sealId,            // 可空：逻辑签名可不盖章
    String  authMethod,        // 必填
    String  credential,        // 二级认证凭证（密码/短信码）
    Long    docId              // 可空：关联正文
) {}

public record DocSignCommand(
    String bizType, Long bizId, Long docId,
    List<SealPlacement> placements,  // 多页多章
    String signatureMeaning, String authMethod, String credential
) {}

public record SealPlacement(
    int   pageIndex,   // 0-based 页码
    float xRatio,      // 归一化左上角 X（0~1，相对页宽）
    float yRatio,      // 归一化左上角 Y（0~1，相对页高）
    float wRatio,      // 归一化宽度
    float hRatio,      // 归一化高度
    int   rotation,    // 旋转角度
    Long  sealId
) {}
```

> **坐标归一化**：前端拖拽用归一化比例存储，规避不同 PDF 页面尺寸 / DPI / 缩放导致的错位。

### 4.3 哈希链算法

```
chain_hash = SM3( prev_hash ‖ signature_id ‖ biz_type ‖ biz_id ‖
                  signer_user_id ‖ seal_id ‖ signature_meaning ‖
                  doc_hash ‖ signed_at_epoch_ms )
```

- **链作用域**：按 `tenant_id` 分别成链（多租户隔离），链首 `prev_hash = NULL`。
  即**同一租户下所有签名共用一条链**，与业务类型无关；`prev_hash` 指向该租户全局顺序上的前一条记录
- **串行化**：写入时用 Lock4j 按 `tenant_id` 加分布式锁，保证链不分支
- **全局链的幂等语义**：任一记录被删除或改动 → 其 `chain_hash` 重算不一致、或后继记录的 `prev_hash`
  对不上 → 可精确断点定位
- **验签语义（重要）**：
  - `verify(signatureId)` —— 校验该记录自身字段与 `chain_hash` 自洽，且其 `prev_hash` 与
    **该租户全局顺序上的真实前驱**的 `chain_hash` 一致
  - `verifyChain(bizType, bizId)` —— 对**业务范围内**的每条记录**逐条独立执行上述校验**。
    ⚠️ **绝不能**简单比较"过滤后列表里相邻两条"的 `prev_hash`/`chain_hash`：由于链是租户全局串的，
    同一业务的两条记录之间可能穿插其它业务的记录，那样会产生**误报"链断裂"**
- **SM3 实现**：Bouncy Castle `SM3Digest`（已在依赖树）

### 4.4 PDF 盖章技术路线（P2）

**两段式**，同时产出**视觉印章**与**数字签名**：

1. **视觉盖章**：PDFBox `PDPageContentStream.drawImage()` 将印章图按归一化坐标绘制到指定页
2. **数字签名**：PDFBox `PDSignature` + `PDDocument.saveIncremental(out)`，用**自签 X.509 证书**做 CMS/PKCS#7 增量签名

> **⚠️ P2 spike 修正（2026-09-16，完整报告见 `2026-09-16-pdfbox-spike-report.md`）** —— 以下几处已被实测推翻，实施以 spike 为准：
> - spec 原文的 `ExternalSigningSupport.saveIncremental()` **API 名是错的**：外部签名流是
>   `PDDocument.saveIncrementalForExternalSigning(out)` → `ExternalSigningSupport`；普通路径是 `PDDocument.saveIncremental(out)`
> - **`saveIncremental` 有硬前置**：全新内存文档直接调会抛 `IllegalStateException: document was not loaded from a file or a stream`。
>   必须先 `save(ByteArrayOutputStream)` 落一次字节、再 `Loader.loadPDF(...)` 重新打开才能增量签名
> - **PDFBox 核心包没有任何验签 API**（无 `SignatureUtils`）→「CMS 验签 + ByteRange 覆盖检查」**必须自己实现**
> - **只做 CMS 验签会漏报篡改**：签名后追加字节或产生增量修订时 `verify()` 仍为 `true`；必须叠加覆盖/修订检查。
>   反向亦然：纯覆盖检查会对尾部裸字节误报。**两步缺一不可**
> - **中文字体是硬依赖**：不嵌字体画中文直接抛 `U+7B7E ('.notdef') is not available in the font Helvetica`；
>   且仅支持 **TrueType(TTF)** —— OTF(CFF) 报 `CFF outlines are not supported`、TTC 报 `'head' table is mandatory`。
>   仓库 `src/main/resources` 下**无任何 CJK 字体**。**P2a 决定不绘制任何文字**（印章是图片、签名域不可见，均不需要字体），
>   字体打包另立项决策 —— 不为一个水印文字往仓库塞 8MB 二进制
> - 已实测可行的组合：`org.apache.pdfbox:pdfbox:3.0.8` + `bcpkix-jdk18on:1.83`（BC 在 PDFBox 的 pom 里是
>   `<optional>true</optional>` 不传递，**仓库现有 BC 声明是必需的**）

**算法选择（重要）**：

| 用途 | 算法 | 理由 |
|------|------|------|
| 哈希链 / 文档哈希 | **SM3** | 国密合规，BC 原生支持，零成本 |
| PDF 数字签名 | **RSA-2048 + SHA-256** | ⚠️ **SM2 签名在 Adobe/Acrobat 中不被识别**，兼容性差；本期目标为"通用可验证的完整性保护"，故用 RSA |

> 自签证书不被 Adobe 计入受信根，Acrobat 会显示"签名有效性未知"，**但完整性校验仍然有效** —— 文档被改动后 Acrobat 会明确报"文档已被修改"。这正是内部留痕所需的效果。

**依赖**：Apache PDFBox 3.x（`Apache-2.0`，无许可风险）。

---

## 五、挂载点设计

### 5.1 流程审批（覆盖 ~20 个单据的关键）

**策略：在前端收集签名意图 → 在 `WfTaskServiceImpl.complete()` 单点插桩**，避免逐个改 20 个业务模块。

```
plus-ui  work/detail.vue（**真实待办审批入口**）
   │  用户点"通过" → 若该流程配置了 needSign，弹出签章框
   │  选择印章 + 填写签署含义 + 二级认证
   ▼
后端  WfTaskServiceImpl.complete(TaskBo)
   │  若 variables 含 signSealId →
   │     先调 ESignatureService.sign(...)
   │     签名失败 → 抛 ServiceException，拒绝 complete   ← 保证"不签章不生效"
   ▼
   继续原流程流转
   ▼
   各业务 afterFlowHandle(WfMyBusiness)  ← 逻辑不变
```

- **是否需签章**：用**流程变量** `needSign`。⚠️ 实测修正：Flowable 的 BPMN 节点扩展属性在服务端从模型读取、
  **不会变成流程变量**，且无任何 API 暴露 —— 故"由 BPMN 节点扩展属性配置"**不可行**（详见 §10.3）。
  当前粒度是**流程级**：同一流程的后续审批人也会看到签章面板
- **`ruoyi-flowable` 新增依赖** `ruoyi-common-sign`（公共层，不反向依赖业务模块，架构方向正确）
- **兼容性**：未配置 `needSign` 的流程节点行为完全不变，**存量流程零影响**

### 5.2 MES P0-6 回填（低成本兑现既有预留）

| 位置 | 改动 |
|------|------|
| `SopViewLogServiceImpl.recordView()` | `log.setSignatureId(null)` → 调 `sign()` 并回填 |
| `NcrOrderServiceImpl.bindDispositionSignature()` | 空实现 → 调 `sign()` 并回填 |

两处字段与方法**均已预留**，改动量极小。

### 5.3 合同盖章

- `SoContract` / `PuContract` 详情页新增「合同正文」区块：上传 PDF → `es_sign_document`
- 上传后进入「盖章」：PDF 预览（`pdfjs-dist`）→ 拖拽定位 → 签章 → 产出 `signed_oss_id`
- 合同列表增加「签署状态」列（未签 / 部分签 / 已签）

### 5.4 前端组件清单

| 组件 | 用途 |
|------|------|
| `SealPicker.vue` | 选章（仅列出当前用户有权使用的章） |
| `SignAuthDialog.vue` | 二级认证（密码 / 短信验证码） |
| `HandwritePad.vue` | 手写签名面板（个人章可选来源，P2） |
| `PdfSealPlacer.vue` | PDF 预览 + 拖拽定位盖章（P2） |
| `SignatureTimeline.vue` | 签名历史时间线 + 验签结果展示 |

---

## 六、安全设计

### 6.1 印章防滥用（**最关键**）

| 风险 | 对策 |
|------|------|
| 印章图被下载后 PS 到任意文件 | ①印章原图存私有桶，**服务端合成，永不返回原图 URL**；②前端仅可获取 `preview_oss_id`（低清 + 水印）；③盖章时原图仅在服务端内存中使用 |
| 越权使用公司章 | 公司章必须 `es_seal_grant` 显式授权；查询可用章时同时校验用户级与部门级授权 + 有效期 + `biz_type` 限定 |
| 印章被撤回后仍在使用 | `status` / `valid_from` / `valid_to` 三重校验；停用/销毁为终态（软删 + 审计） |
| 印章私下更换 | `seal_version` 自增；签名记录快照 `seal_type` 与 `seal_id` |

### 6.2 签署人身份强度

| 方式 | 强度 | 凭证校验方式 | 适用场景 |
|------|------|-------------|---------|
| `LOGIN_ONLY` | 低 | 仅校验登录态与操作权限，无额外凭证 | 低风险"已阅"类动作 |
| `PASSWORD` | 中 | 用户**登录密码再次录入**，服务端用 Sa-Token 的密码编码器校验（BCrypt），**不复用会话票据** | **默认**，审批签批 |
| `SMS` | 中高 | 复用 `ruoyi-common-sms` 发送 6 位验证码，Redis 存 5 分钟 TTL，**校验后立即失效**（防重放） | 高金额合同、关键节点 |
| `FACE` | 高 | P3 预留，复用 `ruoyi-common-cv` 的 `VisionClient` 1:1 验证 | — |

**凭证安全**：二级认证凭证**不落库、不入日志**；校验通过后仅在本次请求内有效，不签发任何持久化令牌。短信验证码连续错误 5 次锁定 10 分钟（Redis 计数）。

**原则**：签署动作必须在登录态下进行，且二级认证失败即拒绝签名（不允许降级）。

### 6.3 不可否认性

- 签名记录含 `signer_user_id` + `signer_name` 快照 + `sign_ip` + `sign_ua` + 服务端 `signed_at`
- `signature_meaning` 明确"签署含义"（GMP 电子签名核心要求之一）
- 哈希链保证事后不可静默篡改

---

## 七、分期与验收

### P1 —— 签名核心 + 印章管理 + 审批挂载（**可独立上线**）

**交付物**
- `ruoyi-common-sign` 模块：`ESignatureService`（`sign` / `verify` / `listByBiz` / `listAvailableSeals`）
- 4 张表 + `ry-vue.sql` 脚本 + `数据库增量变更记录.md` 登记
- 印章管理页（CRUD + 启停 + 预览）、用章授权页、签名记录查询页
- 二级认证（密码 / 短信）
- 哈希链（SM3 + Lock4j 串行化）+ 验签接口
- 审批挂载：`work/detail.vue` 签章入口 + `WfTaskServiceImpl.complete()` 插桩
- MES P0-6 回填：SOP 查阅 + NCR 处置
- 菜单/权限/字典 SQL

**验收标准**（全部已通过 —— 证据见 §10.4）
- [x] 审批节点配置 `needSign` 后，未签章无法提交（后端强制，非前端校验）
- [x] 签名记录含签署含义 / 认证方式 / IP / UA
- [x] `verify(signatureId)` 能检出人为篡改单条记录
- [x] 个人章只能被本人使用；公司章无授权时不可选
      （实测：`admin` 可见「管理员个人章 + Nexus合同章」；`test` 可见 **[]** —— 既看不到他人个人章，也看不到只授权给 admin 的公司章）
- [x] 未配置 `needSign` 的存量流程行为完全不变（回归验证）
- [x] `mvn compile` 通过（全仓 `clean package` exit 0，48 jar）；`npm run lint:eslint` 新增文件 0 问题、仓库既有基线不变

### P2 —— 文档盖章（合同）

**交付物**
- 新增依赖：后端 `Apache PDFBox 3.x`；前端 `pdfjs-dist`
- `es_sign_document` 上传链路（**仅接受 PDF**，非 PDF 直接拒绝并提示）
- `PdfSealPlacer.vue` 拖拽定位 + 多页多章
- `signDocument()`：视觉盖章 + 自签证书数字签名（增量保存）
- 合同详情页「正文 + 盖章」区块；列表页「签署状态」列
- 印章防滥用完整落地（`preview_oss_id` 水印预览）

**验收标准**
- [ ] 盖章后 PDF 在 Acrobat 中可验证签名完整性；改动后提示"文档已被修改"
- [ ] 盖章坐标在不同页面尺寸下位置正确（归一化验证）
- [ ] 前端无论如何都拿不到印章原图 URL
- [ ] 非 PDF 文件被服务端拒绝

### P3 —— 增强（按需立项，**仅预留不实现**）

- `SignTrustProvider` SPI（外部信任根：TSA / CA / 商业 API 适配器）—— **接口预留，本期不实现任何 Provider**
- PDF 关键字定位盖章锚点（借鉴开放签思路）
- 人脸认证（复用 `ruoyi-common-cv`）
- DHR 批记录导出（P0-6 另一半）
- 手写签名板 `HandwritePad.vue`
- **跨端签署会话**：本期签名入口仅在 Web（plus-ui），同源、无跨域问题。若未来把签署下沉到 PDA / App 端
  （如检验员在 PDA 上做 NCR 处置签名），需解决原生页与会话的传递问题——参考 2.7 的 Seal Token 模式。
  ⚠️ 届时**不得**采用其长效 token 策略，仍须逐次二级认证。

> **架构约定**：`ESignatureService` 的信任根通过 SPI 预留，未来接入外部信任根时**不重构核心**。

---

## 八、风险与未决项

| # | 风险 / 未决项 | 影响 | 处置 |
|---|--------------|------|------|
| 1 | **Word 无法直接盖章** | 用户上传 .docx 会失败 | 本期仅收 PDF，界面明确提示"请先另存为 PDF"；服务端拒绝非 PDF。Word 自动转换（需 LibreOffice 无头）列入 P3 |
| 2 | **哈希链与并发** | 高并发下链可能分叉 | Lock4j 按 `tenant_id` 加锁；压测验证 |
| 3 | **自签证书的信任问题** | Acrobat 显示"有效性未知" | 属预期行为；完整性校验有效。文档中明确告知使用者 |
| 4 | **印章图生命周期** | 停用/销毁后历史签名的印章图仍需可查 | 印章不物理删除（软删），历史签名引用的 `seal_oss_id` 永久保留 |
| 5 | **法律效力边界** | 使用者可能误认为具备对外法律效力 | 制度层面明确：本期为内部管理凭证；对外合同如需法律效力须另行接入商业 API |
| 6 | **PDFBox 版本与 BC 兼容性** | PDFBox 3.x 与 BC 1.83 的协作需验证 | P2 开工前做技术验证（spike） |
| 7 | **`ruoyi-flowable` 新增依赖** | 模块依赖关系变化 | 仅依赖公共层 `ruoyi-common-sign`，方向正确；需在 `AutoConfiguration.imports` 中注册 |

---

## 九、参考资料

### 本仓库证据

| 事实 | 位置 |
|------|------|
| Bouncy Castle 1.83 已在依赖树 | `pom.xml:39,308-311`、`ruoyi-common/ruoyi-common-encrypt/pom.xml:26-28` |
| 审批回调统一接口 | `ruoyi-modules/ruoyi-flowable/src/main/java/com/nexus/workflow/service/WfCallBackServiceI.java` |
| 审批提交插桩点 | `ruoyi-modules/ruoyi-flowable/src/main/java/com/nexus/workflow/service/impl/WfTaskServiceImpl.java:204-222` |
| 审批操作 UI 插入点 | `plus-ui/src/views/workflow/work/detail.vue:873-958`（**注意**：`components/ActHandleBtn.vue` 是**死代码**，全仓唯一引用是 `demo/index.vue:113` 的注释。初版方案误指此文件） |
| 合同无正文字段 | `ruoyi-modules/ruoyi-erp/src/main/java/com/nexus/erp/so/domain/SoContract.java` |
| MES SOP 签名预留 | `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/domain/SopViewLog.java:58-59` |
| MES NCR 签名预留 | `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/ncr/service/impl/NcrOrderServiceImpl.java:324-332` |
| 企业主体字段 | `ruoyi-modules/ruoyi-system/src/main/java/com/nexus/system/domain/SysTenant.java:50-60` |
| OSS 扩展字段 | `ruoyi-modules/ruoyi-system/src/main/java/com/nexus/system/domain/SysOssExt.java:23-73` |
| 跨模块表落 ry-vue 的既有先例 | `script/sql/ry-vue.sql:2941`（`ai_flow`） |

### 外部事实（2026-09-15 通过 GitHub API 核实）

| 项目 | 许可证 | Stars | 语言 | 最后推送 |
|------|--------|-------|------|---------|
| [DocuSeal](https://github.com/docusealco/docuseal) | AGPL-3.0 | 18,532 | Ruby | 2026-09-14 |
| [Documenso](https://github.com/documenso/documenso) | AGPL-3.0 | 15,026 | TypeScript | 2026-09-15 |
| [OpenSign](https://github.com/OpenSignLabs/OpenSign) | 非标准 | 6,992 | JavaScript | 2026-08-21 |
| [kaifangqian-base（开放签）](https://github.com/kaifangqian/kaifangqian-base) | AGPL-3.0 | 501 | Vue | 2026-03-26 |
| [Apache PDFBox](https://github.com/apache/pdfbox) | Apache-2.0 | 3,119 | Java | 2026-09-15 |

**待核实项（本环境无法联网检索，需人工复核）**：
- 《中华人民共和国电子签名法》第 13/14 条"可靠的电子签名"法定要件原文 —— 本期定位为内部留痕，不依赖该条款，故不阻塞；若未来转向对外法律效力，**必须先核实法条原文**
- 商业电子签平台（e签宝 / 法大大 / 契约锁 / 腾讯电子签）的计费与私有化报价 —— P3 立项时调研

---

## 十一、P2a 实施与验收结果（2026-09-16）

### 11.1 交付范围

| 批次 | 内容 | 提交 |
|------|------|------|
| P2a-1 | **OSS 内容访问 SPI**（`OssContentAccessor`，接口在 common-sign、实现在 ruoyi-system，与 `SignCredentialChecker` 同模式）**+ 印章水印预览图服务端生成** —— 闭环 §10.2 遗留缺口 | `57b422fc` |
| P2a-2 | **PDF 文档盖章 + 自签证书数字签名 + 双步验签器** | `9f54c565` |
| P2a-3 | **合同正文上传链路 + 6 个 REST 端点**（让 P2a-2 真正可达） | `eecab7d6` |

单测：`ruoyi-common-sign` **209 个全绿**（P1 末 96 → 209）；`ruoyi-system` 11 个。
**spike 报告**：`2026-09-16-pdfbox-spike-report.md`（PDFBox 3.0.8 × BC 1.83 实测，含最小可用配方与 5 条设计级结论）。

### 11.2 端到端实测（真实 MySQL + Redis + MinIO + 运行中的后端）

| P2 验收项 | 结果 | 证据 |
|-----------|------|------|
| 盖章后 PDF 可验证签名完整性 | ✅ | `GET /signature/{id}/verify` → **`verdict:"OK"`**，`cmsPassed=true, coveragePassed=true`，`byteRange` 覆盖至文件末尾 |
| 盖章坐标在不同页面尺寸下位置正确 | ✅ | 单测覆盖 A4 与自定义尺寸（含"忘记翻转 y"的证伪对照）；实测下载的已签 PDF 含 1 个 AcroForm 签名字段 + 页 0 嵌入 240×240 印章图（未盖章原件 **0 张图**） |
| 前端无论如何拿不到印章原图 URL | ✅ | `es_seal.preview_oss_id` 已生成；`GET /seal/{id}/preview` 返回的预览图 **sha256 ≠ 原图**（`C245C1C6…` vs `2B8DA878…`），240×240 PNG。**这是 §10.2 缺口的实测闭环** |
| 非 PDF 文件被服务端拒绝 | ✅ | 文本改名 `.pdf`（魔数校验）与真 `.docx` 均 `code:500` 拒绝；业务键下 **0 行落库、无孤儿 OSS 对象** |
| 改动后提示"文档已被修改" | ✅ | 覆盖区间内等长改 1 字节 → **`MODIFIED_IN_PLACE`**（`CMSSignerDigestMismatchException`）；签名后增量修订 → **`REVISION_APPENDED`**，且该文件 **`cmsPassed=true`** —— 实证"只做 CMS 验签会漏报"这一 spike 结论 |

实测端到端还确认：`es_sign_document.signed_oss_id` / `sign_status=2` 正确回写；`es_signature` 的
`src_doc_oss_id`/`src_doc_hash`/`doc_oss_id`/`doc_hash`/`doc_id` 全部落库；上传的 PDF 与戳记均真实进入对象存储。

### 11.3 关键设计决策

1. **OSS 阻塞用 SPI 解决**，而不是把签章代码搬出 common-sign —— 与 Task 8 的认证 SPI 完全同构，同一个成因。
   该 SPI 一并解决了 P1 遗留的水印预览图（§10.2 曾列出"前端 canvas vs 系统模块 SPI"两条路，最终走 SPI，服务端生成）。
2. **上传只收 multipart 直传，不接客户端 ossId**：ossId 全局自增且任意登录用户可得，凭 ossId 登记等于允许把别人的对象挂成自己的合同。
   服务端在**第一次写入之前**完成：文件名/后缀 → Content-Type → 大小 → `%PDF-` 魔数 → PDFBox 解析取页数（顺带拒掉损坏/加密）。
3. **`bizType`/`bizId` 以文档行为准，不信客户端**（签章端点不接受客户端传入的业务键）。
4. **`is_current` 不变量由一条条件 UPDATE 保证**，且 Lock4j 锁**延迟到事务提交后释放**（`afterCompletion`），否则第二个上传者会在前一个事务提交前读到同一版本号。
5. **证书**：每次签名现生成自签 RSA-2048 X.509 并内嵌进 CMS —— 自包含、不改 schema、无需证书表。
   信任链校验**明确不在范围内**（自签证书在 Acrobat 显示"有效性未知"，但**完整性校验有效**）。
6. **不绘制任何文字、不打包字体**：印章是图片、签名域不可见，都不需要字体。见 11.4。

### 11.4 遗留与待决

| # | 事项 | 说明 |
|---|------|------|
| 1 | **中文字体** | 若将来要在 PDF 上绘制中文文字（如签署人姓名、签署时间），**必须打包一个 TrueType(OFL) 中文字体**：仓库无 CJK 字体，OTF(CFF)/TTC 均不被 PDFBox 支持，子集化后每份 PDF 仅增约 4 KB。**需要你决定是否往仓库加这个二进制** |
| 2 | **Acrobat 实测** | 本机无 Acrobat，P2 验收项 1 是用**编程方式**验签+验覆盖达成的；"Acrobat 面板显示什么文案"仍属推断。建议有 Acrobat 的环境抽查一次（spike 报告里列了样例文件路径） |
| 3 | **跨库原子性** | 签名台账在 `ry-vue`、合同数据在 `ry-erp`，无 XA 则无法真正跨库原子（详见 §10.4 第三处说明） |
| 4 | **P1 存量行不可验签** | 见 11.5 |
| 5 | **P2b 未开始** | 合同详情页 + 路由、`PdfSealPlacer.vue`（需新增 `pdfjs-dist`）、列表「签署状态」列 —— 已与你确认走"新建详情页"方案 |

### 11.5 一条 P1 存量数据的说明（非缺陷）

实测发现 P1 的第 1 条签名（`signature_id=2100052668384714753`）**永久无法通过验签**：
它的 `signed_at=2026-09-16 10:42:28`，**早于**修复提交 `4275ff05`（10:48:39，链哈希取秒级时间戳）。
即该行是用有缺陷的代码写下的、毫秒被截断的历史数据 —— 属**预期的陈旧数据**，不是回归。
第 2–4 条（10:54 / 11:14 / 11:31 写入）单条验签均 `passed=true`。
`SignatureChainCalculator` 自 `fa4b34d9` 起未改动；`diff 4275ff05..HEAD` 在 `ESignatureServiceImpl` 中只有变量重命名。
**无需处理**：它是纯粹的测试期残留；生产环境不存在这类数据（P1 从未上线）。

---

## 附：变更记录

| 日期 | 变更 |
|------|------|
| 2026-09-15 | 初版。选型结论：纯自建（内部留痕级），排除开源代码融合与商业 API |
| 2026-09-15 | 补 §2.7：评估 Contract.Pro / mini-contract（何方签）并记录排除依据（前端外壳 + 引擎闭源托管 + 无 LICENSE 文件）；P3 增加「跨端签署会话」待办 |
| 2026-09-15 | §4.3 明确链作用域与验签语义 —— 修正 `verifyChain` 比较「过滤后相邻记录」会**误报断链**的缺陷（Task 10 反向实验证实） |
| 2026-09-15 | §5.1/§9 修正：真实审批入口是 `work/detail.vue`，`ActHandleBtn.vue` 为死代码；`needSign` 只能是流程变量 |
| 2026-09-15 | **P1 实施完成**，补 §10：14 处计划偏差、SMS 移出 P1、水印预览图的 P1/P2 边界与架构阻塞、待决事项、人工验收清单 |

---

## 十、P1 实施结果（2026-09-15）

### 10.1 交付与验证

| 项 | 结果 |
|---|---|
| 模块 | 新建 `ruoyi-common/ruoyi-common-sign`（`@AutoConfiguration` 注册） |
| 数据表 | `es_seal` / `es_seal_grant` / `es_sign_document` / `es_signature`，落 `ry-vue.sql`；菜单 ID 实为 **4348–4362** |
| 单测 | **88 个全绿**（SM3 / 哈希链 / 印章可用性 / 认证 / 签名 / 验签 / 事务隔离） |
| 全量测试 | 822 run / 4 failures / 9 errors —— 13 项**全部**在 `ruoyi-admin` 的 RuoYi 官方 demo 脚手架（`AssertUnitTest` 故意失败；`DemoUnitTest`/`TagUnitTest` 需真实数据源）。经 `git log 32a83708..HEAD -- ruoyi-admin/src/test/` 为空证实与本特性无关 |
| 构建 | `mvn clean package -DskipTests` 全仓 48 jar，exit 0，无告警 |
| 静态审计 | 前端 16 调用 ↔ 后端 16 端点全匹配；3 菜单路径 ↔ 3 view 文件全匹配；13 权限串全部已种；9 个 `v-hasPermi` 全一致；无循环依赖 |
| 端到端 | ❌ **未做** —— 见 §10.4 |

### 10.2 P1/P2 边界修正：水印预览图

**原方案自相矛盾**：§7 的 P1 验收要求「印章原图不外泄」，但「`preview_oss_id` 水印预览」被排在 P2。而 P1 **没有任何写入 `preview_oss_id` 的代码**（全仓 `setPreviewOssId` 零调用），预览接口只能走服务端回退 —— **返回的是印章原图的 ossId**。

**P1 的真实保证应表述为**：
> 印章原图**不经由任何 OSS 直链或 VO 字段下发**（`SealVo` / `AvailableSealVo` 均无 `sealOssId`），只能经**带权限校验的接口**取得。

这**不等于**「原图永不离开服务端」，后者要等 P2 的降采样 + 水印落地。

**⚠️ 架构阻塞（P2 动工前必读）**：`ruoyi-common-sign` **无法读写 OSS** —— `ISysOssService` / `SysOssVo` / `OssFactory` 都在 `ruoyi-modules/ruoyi-system`，而 `ruoyi-system` 已依赖 `ruoyi-common-sign`，common→system 会成环。故水印图生成**不能放在 sign 模块内**。两条可行路径：

- **(a) 前端生成**：上传时用 canvas 降采样 + 水印，作为 `previewOssId` 一并提交（上传者本就持有原图，无新增泄露面）
- **(b) 事件 / SPI**：在 OSS 可达的模块（如 `ruoyi-system`）监听印章创建后生成回写

### 10.3 实施中对本方案的修正

完整表格见计划文档末尾「实施偏差汇总」。其中会导致**真实故障**的 3 处：

1. **`verifyChain` 会误报「链被篡改」** —— 链是租户全局串的（`appendSignature` 取前驱不带 `bizType` 过滤），比较「过滤后相邻两条」在业务穿插时必然误判。已改为逐条校验自身哈希 + **真实前驱**（§4.3）。
2. **印章根本改不了** —— `SealBo.sealOssId` 的 `@NotNull` 未分校验组，PUT 不重传图片被 400 拒。已改 `AddGroup` / `EditGroup`。
3. **所有 TDD 任务的测试命令是假绿** —— 根 `pom.xml:515` 的 `<groups>${profiles.active}</groups>` 在无 active profile 时匹配不到任何测试，`Tests run: 0` 却 BUILD SUCCESS。**测试命令必须追加 `-Dprofiles.active=`。**

两处**功能性范围收缩**，需知悉：

- **SMS 移出 P1**：短信 API 存在（sms4j），但**手机号取不到** —— `LoginUser` 无 phone 字段，`sys_user` 属 `ruoyi-system`，common 层不能反向依赖。已删除 `/sms-code`，并从 `SignAuthMethod.SUPPORTED` 移除 `SMS`。**P1 实际支持 `LOGIN_ONLY` + `PASSWORD`。** `SmsSignCredentialChecker` 及测试保留待 P2。恢复路径：把发码端点放 `ruoyi-system`，或给 `LoginUser` 加 phone。
- **`needSign` 只能是流程变量**：Flowable 扩展属性在服务端从 BPMN 模型读取、**不会变成流程变量**，无 API 暴露。故「按节点配置扩展属性」不可行。当前为**流程级**粒度 —— 同一流程的后续审批人也会看到签章面板。要按节点粒度控制需后端改造（在任务详情 API 暴露扩展属性）。

### 10.4 端到端验收（2026-09-16 已执行，**发现并修复 1 个致命缺陷**）

环境：本机 MySQL 5.7（`ry-vue`/`ry-erp`/`ry-mes`/`ry-iot` 均存在）+ Redis（密码 `ruoyi123`）+ RabbitMQ，后端以
`java -jar ruoyi-admin.jar --spring.profiles.active=dev --api-decrypt.enabled=false` 启动（**关闭 API 加密仅为 API 级测试的传输层开关，未改任何代码**）。

> **⚠️ 执行前发现：开发库 `ry-vue` 没有签章 schema**，已把 4 张 `es_*` 表与 15 条菜单（4348–4362）应用到开发库（纯新增、可回滚）。
> 另外修正了 **1 条**失效的 `wf_my_business.service_impl_name`（见下"顺带发现的既有缺陷"）。

| # | 验收项 | 结果 | 证据 |
|---|--------|------|------|
| 1 | 后端强制：未签章不可提交 | ✅ | needSign + 错误密码 → `{"code":500,"msg":"签署密码错误"}`，任务**仍在待办**，`es_signature` 无新行 |
| 2 | 签名记录完整 | ✅ | `biz_type=APPROVAL, biz_id=<dataId>, signer_user_id=1, signer_name=系统管理员, signature_meaning=E2E…, auth_method=PASSWORD, signed_at` 均正确 |
| 3 | 篡改可检出 | ✅ | 改 `signature_meaning` → 单条 `passed:false, brokenAt=<该行>`；全链 `链校验在 signatureId=… 处断裂` |
| 4 | 还原后恢复通过 | ✅ | 还原 → `passed:true`，`chain_hash` 未被改动 |
| 5 | 全链验签 | ✅ | `{"passed":true,"chainPassed":true,"checkedCount":1,"messages":["共校验 1 条签名记录，链完整"]}` |
| 6 | 存量流程零影响（无 needSign） | ✅ | 普通审批 `code:200` 正常完成并离开待办，`es_signature` **2 → 2 行未变**，无签章 UI/变量 |
| 7 | 凭证不落库 | ✅ | `act_ru_variable` / `act_hi_varinst` 中 `sign*` key 计数 **0**（失败与成功两条路径均验） |
| 8 | 签章入口可达 | ✅ | 真实入口 `work/detail.vue`（`ActHandleBtn.vue` 为死代码） |
| 9 | 印章原图不外泄 | ⚠️ 静态 | 仅静态审计（无任何接口返回 `sealOssId`）；未做运行时验证 |
| 10 | MES SOP 查阅回填 | ✅ | 登记查阅 → `sop_view_log.signature_id` **非空**；`es_signature` 落 `SOP_VIEW / LOGIN_ONLY / seal_id=NULL` 行（**修复下述数据源缺陷后通过**） || 11 | MES NCR 处置签署 | ✅ | **负向**：`authMethod=LOGIN_ONLY` → `{"code":500,"msg":"NCR 处置签署必须进行二级认证"}`，NCR 仍为 `OPEN`、`disposition_signature_id=NULL`、`NCR_DISPOSITION` 签名数 0。**正向**：`PASSWORD` → `{"code":200}`，`disposition_signature_id` 绑定成功，`es_signature` 落 `NCR_DISPOSITION / '不合格品处置批准：SCRAP' / PASSWORD` 行。**未签署的 `/dispose` 仍可用**（`disposition_signature_id=NULL`，签名数不变） |
| 12 | 前端构建 | ⬜ 未验 | 需 `npm run build:prod` |

#### 🔴 本轮发现的致命缺陷（已修复）

**`signed_at` 为 MySQL `datetime`（秒精度），毫秒在落库时被静默截断，而 `sign()` 是按毫秒计算链哈希的
→ 验签必然失败。** 实测：一条**刚创建、未被任何人改动**的签名，`verify` 直接返回
`passed:false，"签名记录内容与链哈希不一致，疑似被篡改"`。

即：**修复前，系统产出的每一条签名都无法通过自己的校验**（只有约 1/1000 恰好落在整秒的记录能通过）。
88 个单测**全部漏掉**此缺陷 —— 它们从不经数据库往返，只在内存里拿同一个值自比。

修复：`ESignatureServiceImpl.appendSignature` 中把 `signedAt` 归零毫秒后再同时用于**哈希**与**落库**，
保证秒级精度的值可无损往返。提交 `4275ff05`，新增 4 个测试（其中 1 个专门模拟 `datetime(0)` 截断，
已用反向实验证明它在修复前必然失败），单测总数 88 → **92**。

修复后重跑实测：新建签名 → 全链验签 `passed:true` ✅。

#### 🔴 第二个致命缺陷：签章库访问未固定数据源（已修复，提交 `32e27b89`）

签章 4 张表在 **`ry-vue`（主数据源 `master`）**，但签章模块的 DB 访问**没有固定数据源**，于是继承调用方上下文：

```
java.sql.SQLSyntaxErrorException: Table 'ry-mes.es_signature' doesn't exist
    at ESignatureServiceImpl.appendSignature(...)
    at SopViewLogServiceImpl.signSopView(...)   ← MES 模块 @DS("mes")
```

**为什么一直没被发现（两个原因，都值得记）：**
1. 审批路径恰好跑在主数据源上下文里，所以"能跑"——纯属运气；
2. 更糟的是：SOP 路径**按设计是"失败降级不阻断"**，`catch` + `log.warn` 把异常**静默吞掉** —— 接口返回 `{"code":200}`，
   `sop_view_log` 行也写了，但 `signature_id` 是 NULL、`es_signature` 没有行。**功能完全失效却看起来正常。**

修复：三个签章 ServiceImpl 加**类级** `@DS("master")`（dynamic-datasource 4.3.1 支持类级；仓库内约 200 处同类用法，
`WfProcessServiceImpl` 即先例）。新增 `SignDataSourceMasterTest`（4 个测试），
**用 dynamic-datasource 自己的 `DataSourceClassResolver` 校验 19 个接口方法的"生效数据源"**，
并用真实 CGLIB 代理 + 预压 `"mes"` 上下文验证压栈与还原。反向实验证实：移除注解后 4 个测试全部失败。

修复后重跑实测：`sop_view_log.signature_id` 非空，`es_signature` 落 `SOP_VIEW` 行 ✅。

> **残留风险（已记录在实现类 Javadoc）**：`sign()` 是 `REQUIRED` 传播。若将来有调用方在**已绑定 ry-mes/ry-erp 的事务内**调用它，
> 任何 `@DS` 都切不过去（连接已绑定）。届时需改用 `signInNewTransaction()`（`REQUIRES_NEW`，它先切数据源再开事务）。
> 当前唯一调用方 `WfTaskServiceImpl` 无类级 `@DS`，即主数据源，不受影响。

#### 第三个结构性问题：NCR 签署是"死代码"（已修复，提交 `25701fa5`）

Task 16 交付了 `signDisposition`，但**全仓零调用方**（只有接口声明）——与 `ActHandleBtn.vue` 完全同一类缺陷：
功能实现了，但**没有任何入口能触发它**，因此也无法端到端验证。

修复：新增 `PUT /mes/ncr/order/dispose/{ncrId}/sign`，并加服务层 `disposeWithSignature`
（`@Transactional` + `@Lock4j`）：**先签署（内含二级认证校验）再处置**，两者同事务提交/回滚，
杜绝"已处置但未绑定签名"。原未签署的 `/dispose` 端点**保持不变**（P1 决策：签章尚非强制）。

> **⚠️ 跨库原子性限制（已写入实现类 Javadoc）**：签名台账在 `ry-vue`(master)，NCR 数据在 `ry-mes`，
> **不引入 XA 就无法真正跨库原子**。故 `signDisposition` 改用 `signInNewTransaction`（`REQUIRES_NEW`）先提交签名，
> 再由 MES 事务处置。残余窗口：签名已提交而 MES 回滚 ⇒ 留下一条"孤儿授权记录"（方向安全，不会出现"处置了却没签名"）。
> 若将来要求强一致，需引入 XA 或把签名台账落到 `ry-mes`。

#### 顺带发现的既有缺陷（**非本特性引入**）

`wf_my_business.service_impl_name` 存在**失效的 bean 名**，导致对应审批**永久卡死**：

- `wmMaterialReceiveService`（3 条运行中实例）—— 该 bean 已被重命名为 `wmMaterialReceiveCrudService`
- `fwFirmwareService`（1 条运行中实例）—— 该 bean 已不存在

`WfTaskServiceImpl` 在 `SpringContextHolder.getBean(...)` 处直接抛
`NoSuchBeanDefinitionException: No bean named 'wmMaterialReceiveService' available`，
审批既无法通过也无法驳回。**建议**：为流程回调 bean 名加改名兼容（别名 bean 或启动期校验），
并排查线上是否也有同类卡死实例。本轮仅修正了 1 条用于测试。

#### 仍需人工确认的剩余项

以下为自动化与本轮 API 测试**均未能覆盖**的项：

1. **建库导入**：空库按序导入 5 个主脚本，确认 4 张 `es_*` 表、菜单 4348–4362、`sop_view_log.signature_id`、`ncr_order.disposition_signature_id` 均存在且无报错。
2. **审批签章**：配 `needSign=true` 流程变量 → 无权用户见「无可用印章」→ 授权后出现 → **密码错误时审批不推进、任务仍在待办、`es_signature` 无新行**；密码正确则推进且 `verify-chain` 通过。
3. **存量流程零影响**：不配 `needSign` 的流程，无签章 UI、无 `/system/sign/**` 请求、无 `es_signature` 行。
4. **凭证不落库**：`act_ru_variable` / `act_hi_varinst` 中 `NAME_ LIKE 'sign%'` **不含 `signCredential`**。
5. **防篡改**：手工 `UPDATE es_signature SET signature_meaning = CONCAT(...)` → 单条验签 `passed=false`、全链 `brokenAt` 指向该行；还原后恢复 `passed=true`。
6. **印章权限**：无授权不可见 / 授权可见 / 过期不可见（用户级与部门级各验一次）。
7. **MES**：SOP 查看器写入非空 `signature_id`；`signDisposition(LOGIN_ONLY)` 被拒。
8. **⚠️ 事务隔离（最需人工验证）**：让 `signInNewTransaction` 失败，确认 **`sop_view_log` 仍成功落库**、请求不报错、不抛 `UnexpectedRollbackException`。单元测试只能证明「调用了 REQUIRES_NEW 方法」，证明不了外层事务存活。
9. **前端构建**：`plus-ui` 执行 `npm run build:prod`（自动化审计只覆盖后端）。

### 10.5 待决事项（需业务/管理确认，非技术缺陷）

| # | 事项 | 说明 |
|---|------|------|
| 1 | **菜单未授任何角色** | 只种 `sys_menu`，无 `sys_role_menu`。除超管外看不到「电子签章」。本质是「**谁能用公司章**」的治理决策，宜由业务方确认后授权，不由开发猜角色 ID |
| 2 | 死权限 `system:sign:record:query`（菜单 4361） | 已种但全仓零引用。或接线或删除 |
| 3 | 授权页跨权限耦合 | `grant/index.vue` 挂载时调 `listSeal`（需 `system:sign:seal:list`），无守卫、无 catch。仅有授权权限的角色会拿到 403 + 空印章下拉框 |
| 4 | `GET /system/sign/grant/by-seal/{sealId}` | 有 API 封装但无调用方 |
| 5 | 仓库级：全量 `mvn test` 常红 | `ruoyi-admin` demo 脚手架使全量测试无法作为回归信号。**不要把它当作本分支的回归** |

# Nexus-ERP 电子签章能力建设方案（内部留痕级）

> 状态：**方案待评审** —— 选型结论已定：**纯自建，不融合开源代码、不接 CA/TSA/商业 API**
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
| 其余「电子签章/电子印章」检索 | — | — | — | **无任何可用项目**（命中均为无关仓库） |

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
  `create_time`       datetime     NULL, `create_by` bigint NULL,
  PRIMARY KEY (`signature_id`),
  KEY `idx_es_sig_biz` (`tenant_id`, `biz_type`, `biz_id`),
  KEY `idx_es_sig_signer` (`signer_user_id`, `signed_at`),
  KEY `idx_es_sig_chain` (`tenant_id`, `signature_id`)
) COMMENT='电子签名记录';
```

**append-only 约束**（应用层保证，非数据库强制）：
- 实体仍继承 `TenantEntity` 以获得多租户自动处理，但 **Service 层不暴露任何 UPDATE / DELETE 方法**
- 表结构**不含 `update_by` / `update_time` / `del_flag`**；实体类对应字段声明为 `@TableField(exist = false)` 或直接不声明 —— 使"改写"在代码层面无从表达
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

- **链作用域**：按 `tenant_id` 分别成链（多租户隔离），链首 `prev_hash = NULL`
- **串行化**：写入时用 Lock4j 按 `tenant_id` 加分布式锁，保证链不分支
- **篡改检测**：任一条记录被改动 → 其 `chain_hash` 重算不一致 → 其后全链断裂 → 可精确定位断点
- **SM3 实现**：Bouncy Castle `SM3Digest`（已在依赖树）

### 4.4 PDF 盖章技术路线（P2）

**两段式**，同时产出**视觉印章**与**数字签名**：

1. **视觉盖章**：PDFBox `PDPageContentStream.drawImage()` 将印章图按归一化坐标绘制到指定页
2. **数字签名**：PDFBox `PDSignature` + `ExternalSigningSupport.saveIncremental()`，用**自签 X.509 证书**做 CMS/PKCS#7 增量签名

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
plus-ui  ActHandleBtn.vue
   │  用户点"通过/驳回" → 若该节点已配置需签章，弹出签章框
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

- **节点是否需签章**：用流程变量 `needSign`（由 BPMN 节点扩展属性配置）
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
- 审批挂载：`ActHandleBtn.vue` 签章入口 + `WfTaskServiceImpl.complete()` 插桩
- MES P0-6 回填：SOP 查阅 + NCR 处置
- 菜单/权限/字典 SQL

**验收标准**
- [ ] 审批节点配置 `needSign` 后，未签章无法提交（后端强制，非前端校验）
- [ ] 签名记录含签署含义 / 认证方式 / IP / UA
- [ ] `verify(signatureId)` 能检出人为篡改单条记录
- [ ] 个人章只能被本人使用；公司章无授权时不可选
- [ ] 未配置 `needSign` 的存量流程行为完全不变（回归验证）
- [ ] `mvn compile` + `npm run lint:eslint` 通过

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
| 审批操作 UI 插入点 | `plus-ui/src/views/workflow/components/ActHandleBtn.vue:4-26` |
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

## 附：变更记录

| 日期 | 变更 |
|------|------|
| 2026-09-15 | 初版。选型结论：纯自建（内部留痕级），排除开源代码融合与商业 API |

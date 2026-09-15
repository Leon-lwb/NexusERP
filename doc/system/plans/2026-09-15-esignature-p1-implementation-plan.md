# 电子签章 P1 实施计划（签名核心 + 印章管理 + 审批挂载）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在 Nexus-ERP 中建成内部留痕级电子签章能力——当前操作人可调用个人签章或公司签章完成签署，覆盖流程审批（约 20 个单据）与 MES P0-6 既有预留点。

**Architecture:** 新建跨模块公共层 `ruoyi-common-sign`，提供 `ESignatureService` 统一签名域（`sign` / `verify` / `verifyChain`）。签章以 SM3 哈希链保证 append-only 与防篡改；公司章通过 `es_seal_grant` 授权表治理；审批场景在 `WfTaskServiceImpl.complete()` **单点插桩**，避免逐个改动 20 个业务模块。本计划**不含 PDF 文档盖章**（P2 独立计划）。

**Tech Stack:** Java 17 / Spring Boot 3.5.12 / MyBatis-Plus / Sa-Token / Bouncy Castle 1.83（SM3）/ Lock4j（Redisson）/ MinIO / Vue 3.5 + Element Plus + UnoCSS

**设计依据:** `doc/system/specs/2026-09-15-esignature-impl-spec.md`

---

## ⚠️ 执行前必读：测试命令必须追加 `-Dprofiles.active=`

根 `pom.xml:515` 配置了 `<groups>${profiles.active}</groups>`（按打包环境执行对应 `@Tag` 测试）。
**不带 profile 时 `${profiles.active}` 无法解析，surefire 的 groups 过滤会匹配不到任何测试**，
命令会输出 `Tests run: 0` 并 **BUILD SUCCESS** —— 这是**假绿**，不是通过。

| 命令 | 实际结果 |
|------|---------|
| `mvn test -DskipTests=false` | ❌ `Tests run: 0` + BUILD SUCCESS（假绿） |
| `mvn test -DskipTests=false -Dprofiles.active=` | ✅ 真正执行全部测试 |

**本计划所有 Task 的测试命令一律追加 `-Dprofiles.active=`**（PowerShell 中该参数需加引号：
`'-Dprofiles.active='`）。仓库内仅 33/88 个测试类带 `@Tag`，未打标签的测试在默认命令下**不会执行**。

> 该问题由 Task 5 实施时发现并复核（2026-09-15）。仓库既有的 `AGENTS.md` 也写作
> `mvn test -DskipTests=false`，同样存在此陷阱 —— 属仓库层面的既有问题，不在本方案范围内修复。

---

## 范围界定

| 包含（P1） | 不包含（P2/P3） |
|-----------|----------------|
| `ESignatureService.sign()` 逻辑签名 | `signDocument()` PDF 盖章 |
| 印章管理（个人章 / 公司章） | 自签证书数字签名 |
| 用章授权 `es_seal_grant` | 前端 PDF 预览拖拽定位 |
| SM3 哈希链 + 验签 | 手写签名板 |
| 二级认证（密码 / 短信） | 人脸认证、TSA、CA、商业 API |
| 审批挂载 + MES P0-6 回填 | DHR 批记录导出 |

---

## File Structure

### 后端新增（`ruoyi-common/ruoyi-common-sign/`）

| 文件 | 职责 |
|------|------|
| `pom.xml` | 模块依赖声明 |
| `src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` | 自动装配注册（单行 FQN） |
| `.../config/SignAutoConfiguration.java` | 模块装配入口 |
| `.../constants/SignBizType.java` | 业务类型常量 |
| `.../constants/SignAuthMethod.java` | 认证方式常量 |
| `.../constants/SealType.java`、`.../constants/SealStatus.java` | 印章枚举常量 |
| `.../domain/EsSeal.java` | 印章实体 |
| `.../domain/EsSealGrant.java` | 用章授权实体 |
| `.../domain/EsSignDocument.java` | 待签正文实体（P1 仅建表建模，P2 使用） |
| `.../domain/EsSignature.java` | 签名记录实体（append-only） |
| `.../domain/bo/SealBo.java`、`SealGrantBo.java`、`SealQueryBo.java` | 请求 DTO |
| `.../domain/vo/SealVo.java`、`SealGrantVo.java`、`SignatureVo.java`、`AvailableSealVo.java`、`VerifyResultVo.java` | 响应 DTO |
| `.../domain/dto/SignCommand.java` | 签名入参（record） |
| `.../mapper/EsSealMapper.java`、`EsSealGrantMapper.java`、`EsSignDocumentMapper.java`、`EsSignatureMapper.java` | MyBatis-Plus Mapper |
| `.../util/Sm3Utils.java` | SM3 摘要工具（唯一密码学出口） |
| `.../support/SignatureChainCalculator.java` | 链哈希计算（纯函数，可单测） |
| `.../support/SignAuthVerifier.java` | 二级认证校验 |
| `.../support/SealAvailabilityResolver.java` | 印章可用性判定（纯逻辑） |
| `.../service/IESSealService.java` + `.../service/impl/EsSealServiceImpl.java` | 印章服务 |
| `.../service/IESSealGrantService.java` + `.../service/impl/EsSealGrantServiceImpl.java` | 授权服务 |
| `.../service/ESignatureService.java` + `.../service/impl/ESignatureServiceImpl.java` | 签名核心服务 |
| `.../controller/EsSealController.java`、`EsSealGrantController.java`、`EsSignatureController.java` | REST 接口 |

### 后端修改

| 文件 | 改动 |
|------|------|
| `ruoyi-common/pom.xml` | `<module>ruoyi-common-sign</module>` |
| `ruoyi-common/ruoyi-common-bom/pom.xml` | 新增依赖管理条目 |
| `ruoyi-modules/ruoyi-flowable/pom.xml` | 新增 `ruoyi-common-sign` 依赖 |
| `ruoyi-modules/ruoyi-flowable/.../WfTaskServiceImpl.java` | `complete()` 签名插桩 |
| `ruoyi-modules/ruoyi-mes/pom.xml` | 新增 `ruoyi-common-sign` 依赖 |
| `ruoyi-modules/ruoyi-mes/.../SopViewLogServiceImpl.java` | 回填 `signature_id` |
| `ruoyi-modules/ruoyi-mes/.../NcrOrderServiceImpl.java` | 实现 `bindDispositionSignature` |

### 前端新增（`plus-ui/`）

| 文件 | 职责 |
|------|------|
| `src/api/system/sign/seal.ts`、`grant.ts`、`record.ts` | API 层 |
| `src/views/system/sign/seal/index.vue` | 印章管理页 |
| `src/views/system/sign/grant/index.vue` | 用章授权页 |
| `src/views/system/sign/record/index.vue` | 签名记录 + 验签页 |
| `src/components/sign/SealPicker.vue` | 选章组件 |
| `src/components/sign/SignAuthDialog.vue` | 二级认证弹窗 |

### 前端修改

| 文件 | 改动 |
|------|------|
| `src/views/workflow/components/ActHandleBtn.vue` | 签章入口 + 提交时携带签名变量 |

### 数据库 / 文档

| 文件 | 改动 |
|------|------|
| `script/sql/ry-vue.sql` | 4 张表 + 菜单 + 权限 |
| `doc/system/数据库增量变更记录.md` | 登记本次变更 |

---

## Task 1: 模块骨架与自动装配

**Files:**
- Create: `ruoyi-common/ruoyi-common-sign/pom.xml`
- Create: `ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign/config/SignAutoConfiguration.java`
- Create: `ruoyi-common/ruoyi-common-sign/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Modify: `ruoyi-common/pom.xml`
- Modify: `ruoyi-common/ruoyi-common-bom/pom.xml`

- [ ] **Step 1: 创建模块 pom**

`ruoyi-common/ruoyi-common-sign/pom.xml`（结构对齐 `ruoyi-common-cv/pom.xml`）：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.nexus</groupId>
        <artifactId>ruoyi-common</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>ruoyi-common-sign</artifactId>

    <description>
        ruoyi-common-sign 电子签章公共模块
        提供统一签名域(ESignatureService): 印章管理/用章授权/SM3哈希链签名与验签
        供流程审批、MES P0-6 等场景跨模块复用
    </description>

    <dependencies>
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>

        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-mybatis</artifactId>
        </dependency>

        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-tenant</artifactId>
        </dependency>

        <!-- Lock4j(Redisson) 哈希链串行化 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-redis</artifactId>
        </dependency>

        <!-- Sa-Token 登录态/权限校验 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-security</artifactId>
        </dependency>

        <!-- 印章图片/正文文件存储 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-oss</artifactId>
        </dependency>

        <!-- @Translation(OSS_ID_TO_URL) -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-translation</artifactId>
        </dependency>

        <!-- 短信二级认证 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sms</artifactId>
        </dependency>

        <!-- 国密 SM3: bcpkix 传递引入 bcprov-jdk18on(SM3Digest) -->
        <dependency>
            <groupId>org.bouncycastle</groupId>
            <artifactId>bcpkix-jdk18on</artifactId>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

</project>
```

- [ ] **Step 2: 注册模块与依赖管理**

在 `ruoyi-common/pom.xml` 的 `<modules>` 中，`ruoyi-common-cv` 之后追加一行：

```xml
        <module>ruoyi-common-sign</module>
```

在 `ruoyi-common/ruoyi-common-bom/pom.xml` 的 `<dependencies>` 中，`ruoyi-common-cv` 条目之后追加：

```xml
            <dependency>
                <groupId>com.nexus</groupId>
                <artifactId>ruoyi-common-sign</artifactId>
                <version>${revision}</version>
            </dependency>
```

- [ ] **Step 3: 创建自动装配类**

`ruoyi-common-sign/src/main/java/com/nexus/common/sign/config/SignAutoConfiguration.java`：

> **注**：本项目 common 模块的自动配置类统一使用 `@AutoConfiguration`（`ruoyi-common/` 下 7/8 个为
> 此注解，含 `CvAutoConfiguration`），**不是** `@Configuration`。初版计划此处有误，已于 2026-09-15 更正。

```java
package com.nexus.common.sign.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * 电子签章模块自动配置。
 * <p>本模块的 Service 通过 {@code @Service} 声明，由依赖本模块的业务方组件扫描加载；
 * 此类作为 {@code AutoConfiguration.imports} 的注册入口，与项目其它 common 模块保持一致。</p>
 *
 * @author nexus
 * @since 1.0.0
 */
@AutoConfiguration
public class SignAutoConfiguration {
}
```

- [ ] **Step 4: 注册自动装配**

`ruoyi-common-sign/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（单行，末尾无空行）：

```
com.nexus.common.sign.config.SignAutoConfiguration
```

- [ ] **Step 5: 编译验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am compile -DskipTests`
Expected: BUILD SUCCESS，无错误

- [ ] **Step 6: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign ruoyi-common/pom.xml ruoyi-common/ruoyi-common-bom/pom.xml
git commit -m "feat(sign): 新增 ruoyi-common-sign 模块骨架与自动装配"
```

---

## Task 2: 数据库表 + 菜单权限

**Files:**
- Modify: `script/sql/ry-vue.sql`（文件末尾追加）
- Modify: `doc/system/数据库增量变更记录.md`

- [ ] **Step 1: 追加 4 张表到 `script/sql/ry-vue.sql` 末尾**

```sql
-- ----------------------------
-- 电子签章：印章 / 用章授权 / 待签正文 / 签名记录
-- 2026-09-15 新增（方案见 doc/system/specs/2026-09-15-esignature-impl-spec.md）
-- ----------------------------
DROP TABLE IF EXISTS `es_seal`;
CREATE TABLE `es_seal` (
  `seal_id`        bigint(20)   NOT NULL                COMMENT '印章ID',
  `tenant_id`      varchar(20)  NOT NULL                COMMENT '租户ID',
  `seal_type`      char(1)      NOT NULL                COMMENT '印章类型(1个人章 2公司章)',
  `seal_name`      varchar(64)  NOT NULL                COMMENT '印章名称',
  `owner_user_id`  bigint(20)   NULL DEFAULT NULL       COMMENT '个人章归属用户ID(公司章为NULL)',
  `seal_oss_id`    bigint(20)   NOT NULL                COMMENT '印章原图OSS ID(私有桶,仅服务端使用)',
  `preview_oss_id` bigint(20)   NULL DEFAULT NULL       COMMENT '预览图OSS ID(低清+水印,前端可见)',
  `img_width`      int(11)      NOT NULL                COMMENT '印章原图宽(px)',
  `img_height`     int(11)      NOT NULL                COMMENT '印章原图高(px)',
  `status`         char(1)      NOT NULL DEFAULT '0'    COMMENT '状态(0草稿 1启用 2停用 3销毁)',
  `seal_version`   int(11)      NOT NULL DEFAULT 1      COMMENT '印章版本(更换章面自增)',
  `valid_from`     date         NULL DEFAULT NULL       COMMENT '生效日期',
  `valid_to`       date         NULL DEFAULT NULL       COMMENT '失效日期',
  `remark`         varchar(255) NULL DEFAULT NULL       COMMENT '备注',
  `create_dept`    bigint(20)   NULL DEFAULT NULL       COMMENT '创建部门',
  `create_by`      bigint(20)   NULL DEFAULT NULL       COMMENT '创建者',
  `create_time`    datetime     NULL DEFAULT NULL       COMMENT '创建时间',
  `update_by`      bigint(20)   NULL DEFAULT NULL       COMMENT '更新者',
  `update_time`    datetime     NULL DEFAULT NULL       COMMENT '更新时间',
  `del_flag`       char(1)      NOT NULL DEFAULT '0'    COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`seal_id`) USING BTREE,
  KEY `idx_es_seal_tenant_type` (`tenant_id`, `seal_type`) USING BTREE,
  KEY `idx_es_seal_owner` (`owner_user_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电子印章';

DROP TABLE IF EXISTS `es_seal_grant`;
CREATE TABLE `es_seal_grant` (
  `grant_id`        bigint(20)   NOT NULL              COMMENT '授权ID',
  `tenant_id`       varchar(20)  NOT NULL              COMMENT '租户ID',
  `seal_id`         bigint(20)   NOT NULL              COMMENT '印章ID',
  `grantee_user_id` bigint(20)   NULL DEFAULT NULL     COMMENT '被授权用户ID(用户级授权)',
  `grantee_dept_id` bigint(20)   NULL DEFAULT NULL     COMMENT '被授权部门ID(部门级授权)',
  `biz_type`        varchar(32)  NULL DEFAULT NULL     COMMENT '限定业务场景(NULL=不限)',
  `can_sign_alone`  char(1)      NOT NULL DEFAULT '0'  COMMENT '可否单独用章(0否 1是)',
  `valid_from`      datetime     NULL DEFAULT NULL     COMMENT '授权生效时间',
  `valid_to`        datetime     NULL DEFAULT NULL     COMMENT '授权失效时间',
  `status`          char(1)      NOT NULL DEFAULT '1'  COMMENT '状态(0停用 1启用)',
  `grant_by`        bigint(20)   NULL DEFAULT NULL     COMMENT '授权人用户ID',
  `remark`          varchar(255) NULL DEFAULT NULL     COMMENT '备注',
  `create_dept`     bigint(20)   NULL DEFAULT NULL     COMMENT '创建部门',
  `create_by`       bigint(20)   NULL DEFAULT NULL     COMMENT '创建者',
  `create_time`     datetime     NULL DEFAULT NULL     COMMENT '创建时间',
  `update_by`       bigint(20)   NULL DEFAULT NULL     COMMENT '更新者',
  `update_time`     datetime     NULL DEFAULT NULL     COMMENT '更新时间',
  `del_flag`        char(1)      NOT NULL DEFAULT '0'  COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`grant_id`) USING BTREE,
  KEY `idx_es_grant_seal` (`seal_id`, `status`) USING BTREE,
  KEY `idx_es_grant_user` (`grantee_user_id`, `status`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '印章使用授权';

DROP TABLE IF EXISTS `es_sign_document`;
CREATE TABLE `es_sign_document` (
  `doc_id`        bigint(20)   NOT NULL              COMMENT '文档ID',
  `tenant_id`     varchar(20)  NOT NULL              COMMENT '租户ID',
  `biz_type`      varchar(32)  NOT NULL              COMMENT '业务类型',
  `biz_id`        bigint(20)   NOT NULL              COMMENT '业务单据ID',
  `file_oss_id`   bigint(20)   NOT NULL              COMMENT '正文文件OSS ID(仅PDF)',
  `file_name`     varchar(255) NOT NULL              COMMENT '原始文件名',
  `file_size`     bigint(20)   NULL DEFAULT NULL     COMMENT '文件大小(字节)',
  `file_hash`     varchar(64)  NOT NULL              COMMENT '上传时SM3值(验签比对基准)',
  `page_count`    int(11)      NULL DEFAULT NULL     COMMENT 'PDF页数',
  `doc_version`   int(11)      NOT NULL DEFAULT 1    COMMENT '正文版本',
  `is_current`    char(1)      NOT NULL DEFAULT '1'  COMMENT '是否当前生效版本(1是 0否)',
  `signed_oss_id` bigint(20)   NULL DEFAULT NULL     COMMENT '签章后文件OSS ID',
  `sign_status`   char(1)      NOT NULL DEFAULT '0'  COMMENT '签署状态(0未签 1部分签 2已签)',
  `create_dept`   bigint(20)   NULL DEFAULT NULL     COMMENT '创建部门',
  `create_by`     bigint(20)   NULL DEFAULT NULL     COMMENT '创建者',
  `create_time`   datetime     NULL DEFAULT NULL     COMMENT '创建时间',
  `update_by`     bigint(20)   NULL DEFAULT NULL     COMMENT '更新者',
  `update_time`   datetime     NULL DEFAULT NULL     COMMENT '更新时间',
  `del_flag`      char(1)      NOT NULL DEFAULT '0'  COMMENT '删除标志(0存在 1删除)',
  PRIMARY KEY (`doc_id`) USING BTREE,
  KEY `idx_es_doc_biz` (`tenant_id`, `biz_type`, `biz_id`, `is_current`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '待签文档正文';

DROP TABLE IF EXISTS `es_signature`;
CREATE TABLE `es_signature` (
  `signature_id`      bigint(20)   NOT NULL              COMMENT '签名ID',
  `tenant_id`         varchar(20)  NOT NULL              COMMENT '租户ID',
  `biz_type`          varchar(32)  NOT NULL              COMMENT '业务类型',
  `biz_id`            bigint(20)   NULL DEFAULT NULL     COMMENT '业务单据ID',
  `biz_no`            varchar(64)  NULL DEFAULT NULL     COMMENT '业务单据编号(冗余快照)',
  `signer_user_id`    bigint(20)   NOT NULL              COMMENT '签署人用户ID',
  `signer_name`       varchar(64)  NOT NULL              COMMENT '签署人姓名(冗余快照)',
  `signer_dept_id`    bigint(20)   NULL DEFAULT NULL     COMMENT '签署人部门ID(快照)',
  `seal_id`           bigint(20)   NULL DEFAULT NULL     COMMENT '所用印章ID(逻辑签名可空)',
  `seal_type`         char(1)      NULL DEFAULT NULL     COMMENT '印章类型快照(1个人 2公司)',
  `signature_meaning` varchar(128) NOT NULL              COMMENT '签署含义',
  `auth_method`       varchar(16)  NOT NULL              COMMENT '认证方式(PASSWORD/SMS/FACE/LOGIN_ONLY)',
  `src_doc_oss_id`    bigint(20)   NULL DEFAULT NULL     COMMENT '签署前文档OSS ID',
  `src_doc_hash`      varchar(64)  NULL DEFAULT NULL     COMMENT '签署前文档SM3',
  `doc_oss_id`        bigint(20)   NULL DEFAULT NULL     COMMENT '签署后文档OSS ID',
  `doc_hash`          varchar(64)  NULL DEFAULT NULL     COMMENT '签署后文档SM3',
  `doc_id`            bigint(20)   NULL DEFAULT NULL     COMMENT '关联 es_sign_document.doc_id',
  `signed_at`         datetime     NOT NULL              COMMENT '签署时间(服务端时间)',
  `sign_ip`           varchar(64)  NULL DEFAULT NULL     COMMENT '签署IP',
  `sign_ua`           varchar(512) NULL DEFAULT NULL     COMMENT '签署User-Agent',
  `prev_hash`         varchar(64)  NULL DEFAULT NULL     COMMENT '前一条链哈希(链首为NULL)',
  `chain_hash`        varchar(64)  NOT NULL              COMMENT '本条链哈希(SM3)',
  `remark`            varchar(255) NULL DEFAULT NULL     COMMENT '备注',
  PRIMARY KEY (`signature_id`) USING BTREE,
  KEY `idx_es_sig_biz` (`tenant_id`, `biz_type`, `biz_id`) USING BTREE,
  KEY `idx_es_sig_signer` (`signer_user_id`, `signed_at`) USING BTREE,
  KEY `idx_es_sig_chain` (`tenant_id`, `signature_id`) USING BTREE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '电子签名记录(append-only,无更新/删除字段)';
```

> **注意**：`es_signature` **有意不含** `update_by` / `update_time` / `del_flag` —— 使"改写"在表结构层面无从表达。

- [ ] **Step 2: 追加菜单与权限到 `script/sql/ry-vue.sql` 末尾**

```sql
-- 电子签章菜单（挂在「系统管理」下；parent_id 以实际库中「系统管理」目录ID为准，
-- 本仓库该目录为 1，新增前先执行 SELECT menu_id, menu_name FROM sys_menu WHERE parent_id=0; 核对）
INSERT INTO `sys_menu` VALUES (4290, '电子签章', 1, 10, 'sign', NULL, '', 1, 0, 'M', '0', '0', '', 'stamp', 103, 1, SYSDATE(), NULL, NULL, '电子签章目录');
INSERT INTO `sys_menu` VALUES (4291, '印章管理', 4290, 1, 'seal', 'system/sign/seal/index', '', 1, 0, 'C', '0', '0', 'system:sign:seal:list', 'stamp', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4292, '用章授权', 4290, 2, 'grant', 'system/sign/grant/index', '', 1, 0, 'C', '0', '0', 'system:sign:grant:list', 'peoples', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4293, '签名记录', 4290, 3, 'record', 'system/sign/record/index', '', 1, 0, 'C', '0', '0', 'system:sign:record:list', 'form', 103, 1, SYSDATE(), NULL, NULL, '');

-- 印章管理按钮
INSERT INTO `sys_menu` VALUES (4294, '印章查询', 4291, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:seal:query', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4295, '印章新增', 4291, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:seal:add', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4296, '印章修改', 4291, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:seal:edit', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4297, '印章删除', 4291, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:seal:remove', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4298, '印章启停', 4291, 5, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:seal:status', '#', 103, 1, SYSDATE(), NULL, NULL, '');

-- 用章授权按钮
INSERT INTO `sys_menu` VALUES (4299, '授权查询', 4292, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:grant:query', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4300, '授权新增', 4292, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:grant:add', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4301, '授权修改', 4292, 3, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:grant:edit', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4302, '授权删除', 4292, 4, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:grant:remove', '#', 103, 1, SYSDATE(), NULL, NULL, '');

-- 签名记录按钮
INSERT INTO `sys_menu` VALUES (4303, '记录查询', 4293, 1, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:record:query', '#', 103, 1, SYSDATE(), NULL, NULL, '');
INSERT INTO `sys_menu` VALUES (4304, '记录验签', 4293, 2, '', '', '', 1, 0, 'F', '0', '0', 'system:sign:record:verify', '#', 103, 1, SYSDATE(), NULL, NULL, '');
```

> **ID 冲突防护**：执行 `SELECT MAX(menu_id) FROM sys_menu;` 确认 4290-4304 未被占用；若已占用，改用一个未使用的连续区间。

- [ ] **Step 3: 导入验证**

在测试库执行：

```bash
mysql -uroot -p ry-vue < script/sql/ry-vue.sql
```

Expected: 无报错；`SHOW TABLES LIKE 'es_%';` 返回 4 行。

- [ ] **Step 4: 登记数据库变更**

在 `doc/system/数据库增量变更记录.md` 的 `## 2026-09` 表格末尾追加一行：

```markdown
| 09-15 | 电子签章 P1：`es_seal`/`es_seal_grant`/`es_sign_document`/`es_signature` 4 表 + 菜单权限 | ry-vue |
```

- [ ] **Step 5: 提交**

```bash
git add script/sql/ry-vue.sql doc/system/数据库增量变更记录.md
git commit -m "feat(sign): 电子签章 4 张表与菜单权限"
```

---

## Task 3: 枚举常量与实体类

**Files:**
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/constants/{SignBizType,SignAuthMethod,SealType,SealStatus}.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/domain/{EsSeal,EsSealGrant,EsSignDocument,EsSignature}.java`

- [ ] **Step 1: 创建常量类**

`constants/SignBizType.java`：

```java
package com.nexus.common.sign.constants;

/**
 * 签名业务类型。
 *
 * @author nexus
 */
public final class SignBizType {

    private SignBizType() {
    }

    /** 流程审批签批 */
    public static final String APPROVAL = "APPROVAL";
    /** MES SOP 查阅确认 */
    public static final String SOP_VIEW = "SOP_VIEW";
    /** MES NCR 处置签名 */
    public static final String NCR_DISPOSITION = "NCR_DISPOSITION";
    /** 销售合同 */
    public static final String SO_CONTRACT = "SO_CONTRACT";
    /** 采购合同 */
    public static final String PU_CONTRACT = "PU_CONTRACT";
}
```

`constants/SignAuthMethod.java`：

```java
package com.nexus.common.sign.constants;

import java.util.Set;

/**
 * 签署认证方式。
 *
 * @author nexus
 */
public final class SignAuthMethod {

    private SignAuthMethod() {
    }

    /** 仅校验登录态与操作权限 */
    public static final String LOGIN_ONLY = "LOGIN_ONLY";
    /** 登录密码再次录入 */
    public static final String PASSWORD = "PASSWORD";
    /** 短信验证码 */
    public static final String SMS = "SMS";
    /** 人脸 1:1（P3 预留，P1 不支持） */
    public static final String FACE = "FACE";

    private static final Set<String> SUPPORTED = Set.of(LOGIN_ONLY, PASSWORD, SMS);

    /** 是否为本期支持的认证方式 */
    public static boolean isSupported(String method) {
        return SUPPORTED.contains(method);
    }
}
```

`constants/SealType.java`：

```java
package com.nexus.common.sign.constants;

/**
 * 印章类型。
 *
 * @author nexus
 */
public final class SealType {

    private SealType() {
    }

    /** 个人章 */
    public static final String PERSONAL = "1";
    /** 公司章 */
    public static final String COMPANY = "2";
}
```

`constants/SealStatus.java`：

```java
package com.nexus.common.sign.constants;

/**
 * 印章状态。
 *
 * @author nexus
 */
public final class SealStatus {

    private SealStatus() {
    }

    /** 草稿 */
    public static final String DRAFT = "0";
    /** 启用 */
    public static final String ENABLED = "1";
    /** 停用 */
    public static final String DISABLED = "2";
    /** 销毁（终态） */
    public static final String DESTROYED = "3";
}
```

- [ ] **Step 2: 创建印章实体**

`domain/EsSeal.java`：

```java
package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Date;

/**
 * 电子印章。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("es_seal")
public class EsSeal extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 印章ID */
    @TableId(value = "seal_id")
    private Long sealId;

    /** 印章类型(1个人章 2公司章) */
    private String sealType;

    /** 印章名称 */
    private String sealName;

    /** 个人章归属用户ID(公司章为NULL) */
    private Long ownerUserId;

    /** 印章原图OSS ID(私有桶,仅服务端使用) */
    private Long sealOssId;

    /** 预览图OSS ID(低清+水印,前端可见) */
    private Long previewOssId;

    /** 印章原图宽(px) */
    private Integer imgWidth;

    /** 印章原图高(px) */
    private Integer imgHeight;

    /** 状态(0草稿 1启用 2停用 3销毁) */
    private String status;

    /** 印章版本 */
    private Integer sealVersion;

    /** 生效日期 */
    private Date validFrom;

    /** 失效日期 */
    private Date validTo;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
```

- [ ] **Step 3: 创建用章授权实体**

`domain/EsSealGrant.java`：

```java
package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.util.Date;

/**
 * 印章使用授权。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("es_seal_grant")
public class EsSealGrant extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 授权ID */
    @TableId(value = "grant_id")
    private Long grantId;

    /** 印章ID */
    private Long sealId;

    /** 被授权用户ID */
    private Long granteeUserId;

    /** 被授权部门ID */
    private Long granteeDeptId;

    /** 限定业务场景(NULL=不限) */
    private String bizType;

    /** 可否单独用章(0否 1是) */
    private String canSignAlone;

    /** 授权生效时间 */
    private Date validFrom;

    /** 授权失效时间 */
    private Date validTo;

    /** 状态(0停用 1启用) */
    private String status;

    /** 授权人用户ID */
    private Long grantBy;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
```

- [ ] **Step 4: 创建待签正文实体**

`domain/EsSignDocument.java`：

```java
package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serial;

/**
 * 待签文档正文（P1 建表建模，P2 盖章链路使用）。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("es_sign_document")
public class EsSignDocument extends TenantEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 文档ID */
    @TableId(value = "doc_id")
    private Long docId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 正文文件OSS ID */
    private Long fileOssId;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小 */
    private Long fileSize;

    /** 上传时SM3值 */
    private String fileHash;

    /** PDF页数 */
    private Integer pageCount;

    /** 正文版本 */
    private Integer docVersion;

    /** 是否当前生效版本(1是 0否) */
    private String isCurrent;

    /** 签章后文件OSS ID */
    private Long signedOssId;

    /** 签署状态(0未签 1部分签 2已签) */
    private String signStatus;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}
```

- [ ] **Step 5: 创建签名记录实体（append-only）**

`domain/EsSignature.java`：

```java
package com.nexus.common.sign.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 电子签名记录。
 * <p><b>append-only</b>：有意不继承 {@code BaseEntity}/{@code TenantEntity}，
 * 不提供 update_by/update_time/del_flag 字段，使"改写"在实体层面无从表达。
 * tenant_id 由 MyBatis-Plus 多租户插件在 insert 时自动注入。</p>
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@TableName("es_signature")
public class EsSignature implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 签名ID */
    @TableId(value = "signature_id")
    private Long signatureId;

    /** 租户ID(由多租户插件注入) */
    private String tenantId;

    /** 业务类型 */
    private String bizType;

    /** 业务单据ID */
    private Long bizId;

    /** 业务单据编号(冗余快照) */
    private String bizNo;

    /** 签署人用户ID */
    private Long signerUserId;

    /** 签署人姓名(冗余快照) */
    private String signerName;

    /** 签署人部门ID(快照) */
    private Long signerDeptId;

    /** 所用印章ID */
    private Long sealId;

    /** 印章类型快照 */
    private String sealType;

    /** 签署含义 */
    private String signatureMeaning;

    /** 认证方式 */
    private String authMethod;

    /** 签署前文档OSS ID */
    private Long srcDocOssId;

    /** 签署前文档SM3 */
    private String srcDocHash;

    /** 签署后文档OSS ID */
    private Long docOssId;

    /** 签署后文档SM3 */
    private String docHash;

    /** 关联 es_sign_document.doc_id */
    private Long docId;

    /** 签署时间(服务端时间) */
    private Date signedAt;

    /** 签署IP */
    private String signIp;

    /** 签署User-Agent */
    private String signUa;

    /** 前一条链哈希(链首为NULL) */
    private String prevHash;

    /** 本条链哈希(SM3) */
    private String chainHash;

    /** 备注 */
    private String remark;
}
```

> **关于 `tenantId` 字段类型**：本仓库 `tenant_id` 已统一为 `varchar(20)`（见 `数据库增量变更记录.md` 07-10 条目），故实体用 `String`。`TenantEntity` 中的 `tenantId` 同样是 `String`。

- [ ] **Step 6: 编译验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 7: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign
git commit -m "feat(sign): 电子签章常量与 4 个实体"
```

---

## Task 4: Mapper 与 BO/VO

**Files:**
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/mapper/{EsSealMapper,EsSealGrantMapper,EsSignDocumentMapper,EsSignatureMapper}.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/domain/dto/SignCommand.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/domain/bo/{SealBo,SealQueryBo,SealGrantBo}.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/domain/vo/{SealVo,SealGrantVo,SignatureVo,AvailableSealVo,VerifyResultVo}.java`

- [ ] **Step 1: 创建 Mapper**

`mapper/EsSealMapper.java`：

```java
package com.nexus.common.sign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.sign.domain.EsSeal;

/**
 * 印章 Mapper。
 *
 * @author nexus
 */
public interface EsSealMapper extends BaseMapper<EsSeal> {
}
```

`mapper/EsSealGrantMapper.java`：

```java
package com.nexus.common.sign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.sign.domain.EsSealGrant;

/**
 * 用章授权 Mapper。
 *
 * @author nexus
 */
public interface EsSealGrantMapper extends BaseMapper<EsSealGrant> {
}
```

`mapper/EsSignDocumentMapper.java`：

```java
package com.nexus.common.sign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.sign.domain.EsSignDocument;

/**
 * 待签正文 Mapper。
 *
 * @author nexus
 */
public interface EsSignDocumentMapper extends BaseMapper<EsSignDocument> {
}
```

`mapper/EsSignatureMapper.java`：

```java
package com.nexus.common.sign.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.nexus.common.sign.domain.EsSignature;

/**
 * 签名记录 Mapper。
 * <p>append-only：仅使用 insert / select，禁止调用 updateById / deleteById。</p>
 *
 * @author nexus
 */
public interface EsSignatureMapper extends BaseMapper<EsSignature> {
}
```

> **Mapper 扫描**：本仓库 Mapper 由 `@MapperScan` 统一扫描（`ruoyi-common-mybatis` 的 `MybatisPlusConfig`）。若新建模块未被扫描到，需在 `ruoyi-common-sign` 的 `SignAutoConfiguration` 上加 `@MapperScan("com.nexus.common.sign.mapper")`。**验证方法**：Task 5 编译后启动应用，若报 `No qualifying bean of type 'EsSealMapper'` 则补加该注解。

- [ ] **Step 2: 创建签名入参 DTO**

`domain/dto/SignCommand.java`：

```java
package com.nexus.common.sign.domain.dto;

/**
 * 签名入参。
 *
 * @param bizType          业务类型（必填，见 SignBizType）
 * @param bizId            业务单据ID（可空，如纯审批动作）
 * @param bizNo            业务单据编号（可空）
 * @param signatureMeaning 签署含义（必填，如"审批通过"）
 * @param sealId           所用印章ID（可空=仅逻辑签名不盖章）
 * @param authMethod       认证方式（必填，见 SignAuthMethod）
 * @param credential       二级认证凭证（LOGIN_ONLY 时可为空）
 * @author nexus
 */
public record SignCommand(
    String bizType,
    Long bizId,
    String bizNo,
    String signatureMeaning,
    Long sealId,
    String authMethod,
    String credential
) {
}
```

- [ ] **Step 3: 创建 BO**

`domain/bo/SealBo.java`：

```java
package com.nexus.common.sign.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 印章新增/修改入参。
 *
 * @author nexus
 */
@Data
public class SealBo {

    private Long sealId;

    /** 印章类型(1个人 2公司) */
    @NotBlank(message = "印章类型不能为空")
    private String sealType;

    /** 印章名称 */
    @NotBlank(message = "印章名称不能为空")
    private String sealName;

    /** 个人章归属用户ID(个人章必填) */
    private Long ownerUserId;

    /** 印章原图OSS ID */
    @NotNull(message = "印章图片不能为空")
    private Long sealOssId;

    /** 印章原图宽 */
    @NotNull(message = "印章图片宽度不能为空")
    private Integer imgWidth;

    /** 印章原图高 */
    @NotNull(message = "印章图片高度不能为空")
    private Integer imgHeight;

    /** 生效日期 */
    private Date validFrom;

    /** 失效日期 */
    private Date validTo;

    private String remark;
}
```

`domain/bo/SealQueryBo.java`：

```java
package com.nexus.common.sign.domain.bo;

import lombok.Data;

/**
 * 印章查询条件。
 *
 * @author nexus
 */
@Data
public class SealQueryBo {

    private String sealType;

    private String sealName;

    private String status;

    /** 仅查当前用户可用的印章（个人章 + 已授权公司章） */
    private Boolean onlyAvailable;
}
```

`domain/bo/SealGrantBo.java`：

```java
package com.nexus.common.sign.domain.bo;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

/**
 * 用章授权新增/修改入参。
 *
 * @author nexus
 */
@Data
public class SealGrantBo {

    private Long grantId;

    @NotNull(message = "印章不能为空")
    private Long sealId;

    /** 被授权用户ID（与 granteeDeptId 至少填一个） */
    private Long granteeUserId;

    /** 被授权部门ID（与 granteeUserId 至少填一个） */
    private Long granteeDeptId;

    /** 限定业务场景(NULL=不限) */
    private String bizType;

    /** 可否单独用章(0否 1是) */
    private String canSignAlone;

    private Date validFrom;

    private Date validTo;

    private String status;

    private String remark;
}
```

- [ ] **Step 4: 创建 VO**

`domain/vo/SealVo.java`：

```java
package com.nexus.common.sign.domain.vo;

import com.nexus.common.sign.domain.EsSeal;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 印章视图对象。
 * <p>注意：<b>不包含</b> sealOssId（印章原图）—— 原图绝不下发前端。</p>
 *
 * @author nexus
 */
@Data
@AutoMapper(target = EsSeal.class)
public class SealVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long sealId;

    private String sealType;

    private String sealName;

    private Long ownerUserId;

    /** 预览图OSS ID（低清+水印；前端经 /system/sign/seal/{id}/preview 获取） */
    private Long previewOssId;

    private Integer imgWidth;

    private Integer imgHeight;

    private String status;

    private Integer sealVersion;

    private Date validFrom;

    private Date validTo;

    private String remark;

    private Date createTime;

    private Date updateTime;
}
```

`domain/vo/AvailableSealVo.java`：

```java
package com.nexus.common.sign.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 当前用户可用印章。
 *
 * @author nexus
 */
@Data
public class AvailableSealVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long sealId;

    private String sealType;

    private String sealName;

    private Long previewOssId;

    private Integer imgWidth;

    private Integer imgHeight;
}
```

`domain/vo/SignatureVo.java`：

```java
package com.nexus.common.sign.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 签名记录视图对象。
 *
 * @author nexus
 */
@Data
public class SignatureVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long signatureId;

    private String bizType;

    private Long bizId;

    private String bizNo;

    private Long signerUserId;

    private String signerName;

    private Long signerDeptId;

    private Long sealId;

    private String sealType;

    private String signatureMeaning;

    private String authMethod;

    private Long docOssId;

    private String docHash;

    private Long docId;

    private Date signedAt;

    private String signIp;

    private String signUa;

    private String prevHash;

    private String chainHash;

    private String remark;
}
```

`domain/vo/VerifyResultVo.java`：

```java
package com.nexus.common.sign.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 验签结果。
 *
 * @author nexus
 */
@Data
public class VerifyResultVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 整体是否通过 */
    private Boolean passed;

    /** 链校验是否通过 */
    private Boolean chainPassed;

    /** 断链位置（首个不一致的 signatureId；无断链为 null） */
    private Long brokenAt;

    /** 校验的记录条数 */
    private Integer checkedCount;

    /** 明细说明 */
    private List<String> messages = new ArrayList<>();
}
```

`domain/vo/SealGrantVo.java`：

```java
package com.nexus.common.sign.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 用章授权视图对象。
 *
 * @author nexus
 */
@Data
public class SealGrantVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private Long grantId;

    private Long sealId;

    /** 印章名称（联表翻译） */
    private String sealName;

    private Long granteeUserId;

    /** 被授权用户昵称（联表翻译） */
    private String granteeUserName;

    private Long granteeDeptId;

    /** 被授权部门名称（联表翻译） */
    private String granteeDeptName;

    private String bizType;

    private String canSignAlone;

    private Date validFrom;

    private Date validTo;

    private String status;

    private Long grantBy;

    private String remark;

    private Date createTime;
}
```

- [ ] **Step 5: 编译验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 6: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src/main/java/com/nexus/common/sign
git commit -m "feat(sign): 电子签章 Mapper / BO / VO"
```

---

## Task 5: SM3 工具与哈希链计算（TDD）

**Files:**
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/util/Sm3Utils.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/support/SignatureChainCalculator.java`
- Test: `ruoyi-common-sign/src/test/java/com/nexus/common/sign/util/Sm3UtilsTest.java`
- Test: `ruoyi-common-sign/src/test/java/com/nexus/common/sign/support/SignatureChainCalculatorTest.java`

- [ ] **Step 1: 编写失败测试 —— SM3 工具**

`Sm3UtilsTest.java`：

```java
package com.nexus.common.sign.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SM3 摘要工具测试。
 *
 * @author nexus
 */
class Sm3UtilsTest {

    @Test
    @DisplayName("hashHex: 空输入产生合法 64 位十六进制")
    void hashHex_emptyInput_returns64Hex() {
        String r = Sm3Utils.hashHex();
        assertEquals(64, r.length());
        assertTrue(r.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("hashHex: 国密标准测试向量 'abc' -> 66c7f0f4...")
    void hashHex_abc_matchesGmStandardVector() {
        // GB/T 32905-2016 标准测试向量
        String r = Sm3Utils.hashHex("abc");
        assertEquals("66c7f0f462eeedd9d1f2d46bdc10e4e24167c4875cf2f7a2297da02b8f4ba8e0", r);
    }

    @Test
    @DisplayName("hashHex: 分隔符避免拼接歧义 —— [ab,c] != [a,bc]")
    void hashHex_separatorPreventsAmbiguity() {
        assertNotEquals(Sm3Utils.hashHex("ab", "c"), Sm3Utils.hashHex("a", "bc"));
    }

    @Test
    @DisplayName("hashHex: 相同输入稳定")
    void hashHex_deterministic() {
        assertEquals(Sm3Utils.hashHex("x", "y"), Sm3Utils.hashHex("x", "y"));
    }

    @Test
    @DisplayName("hashHex: null 与空串等价且不抛异常")
    void hashHex_nullTreatedAsEmpty() {
        assertEquals(Sm3Utils.hashHex((String) null), Sm3Utils.hashHex(""));
    }

    @Test
    @DisplayName("hashBytesHex: 与 hashHex 对同一 UTF-8 字节一致")
    void hashBytesHex_consistentWithHashHex() {
        String s = "合同A";
        assertEquals(Sm3Utils.hashHex(s), Sm3Utils.hashHexBytes(s.getBytes(StandardCharsets.UTF_8)));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=Sm3UtilsTest`
Expected: 编译失败 —— `Sm3Utils` 不存在

- [ ] **Step 3: 实现 Sm3Utils**

`util/Sm3Utils.java`：

```java
package com.nexus.common.sign.util;

import org.bouncycastle.crypto.digests.SM3Digest;
import org.bouncycastle.util.encoders.Hex;

import java.nio.charset.StandardCharsets;

/**
 * SM3 摘要工具（国密，GB/T 32905-2016）。
 * <p>本模块唯一的密码学出口；哈希链、文档摘要统一走此处。</p>
 *
 * @author nexus
 */
public final class Sm3Utils {

    /** 字段分隔符：避免 ["ab","c"] 与 ["a","bc"] 产生相同摘要 */
    private static final byte SEPARATOR = 0x1F;

    private Sm3Utils() {
    }

    /**
     * 对多个字符串字段求 SM3，字段间插入分隔符防拼接歧义。
     *
     * @param parts 待摘要字段（null 视为空串）
     * @return 64 位小写十六进制摘要
     */
    public static String hashHex(String... parts) {
        SM3Digest digest = new SM3Digest();
        if (parts != null) {
            for (String part : parts) {
                byte[] b = (part == null ? "" : part).getBytes(StandardCharsets.UTF_8);
                digest.update(b, 0, b.length);
                digest.update(SEPARATOR);
            }
        }
        return doFinalHex(digest);
    }

    /**
     * 对字节数组求 SM3。
     *
     * @param data 字节数组（null 视为空数组）
     * @return 64 位小写十六进制摘要
     */
    public static String hashHexBytes(byte[] data) {
        SM3Digest digest = new SM3Digest();
        if (data != null && data.length > 0) {
            digest.update(data, 0, data.length);
        }
        return doFinalHex(digest);
    }

    private static String doFinalHex(SM3Digest digest) {
        byte[] out = new byte[digest.getDigestSize()];
        digest.doFinal(out, 0);
        return Hex.toHexString(out);
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=Sm3UtilsTest`
Expected: Tests run: 6, Failures: 0, Errors: 0

- [ ] **Step 5: 编写失败测试 —— 哈希链计算器**

`SignatureChainCalculatorTest.java`：

```java
package com.nexus.common.sign.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 哈希链计算器测试。
 *
 * @author nexus
 */
class SignatureChainCalculatorTest {

    private static final long T = 1_755_000_000_000L;

    @Test
    @DisplayName("compute: 稳定可复现，返回 64 位十六进制")
    void compute_deterministic() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, 9L, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, 9L, "审批通过", null, T);
        assertEquals(a, b);
        assertTrue(a.matches("[0-9a-f]{64}"));
    }

    @Test
    @DisplayName("compute: 任一字段变化都改变结果（签署含义）")
    void compute_meaningChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批驳回", null, T);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: 任一字段变化都改变结果（签署人）")
    void compute_signerChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 8L, null, "审批通过", null, T);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: 时间变化改变结果")
    void compute_timeChange_altersHash() {
        String a = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T);
        String b = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批通过", null, T + 1);
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("compute: prevHash 参与计算，链式可验证")
    void compute_prevHashParticipates() {
        String first = SignatureChainCalculator.compute(null, 1L, "APPROVAL", 100L, 7L, null, "审批", null, T);
        String secondA = SignatureChainCalculator.compute(first, 2L, "APPROVAL", 100L, 7L, null, "审批", null, T + 1);
        String secondB = SignatureChainCalculator.compute(null, 2L, "APPROVAL", 100L, 7L, null, "审批", null, T + 1);
        assertNotEquals(secondA, secondB);
    }

    @Test
    @DisplayName("compute: 空值与空串等价")
    void compute_nullAndEmptyEquivalent() {
        assertEquals(
            SignatureChainCalculator.compute(null, 1L, "APPROVAL", null, 7L, null, "审批", null, T),
            SignatureChainCalculator.compute("", 1L, "APPROVAL", null, 7L, null, "审批", "", T));
    }
}
```

- [ ] **Step 6: 运行测试确认失败**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=SignatureChainCalculatorTest`
Expected: 编译失败 —— `SignatureChainCalculator` 不存在

- [ ] **Step 7: 实现 SignatureChainCalculator**

`support/SignatureChainCalculator.java`：

```java
package com.nexus.common.sign.support;

import com.nexus.common.sign.util.Sm3Utils;

/**
 * 签名哈希链计算（纯函数，无副作用，便于单测）。
 *
 * <pre>
 * chain_hash = SM3( prev_hash ‖ signature_id ‖ biz_type ‖ biz_id ‖
 *                   signer_user_id ‖ seal_id ‖ signature_meaning ‖
 *                   doc_hash ‖ signed_at_epoch_ms )
 * </pre>
 *
 * @author nexus
 */
public final class SignatureChainCalculator {

    private SignatureChainCalculator() {
    }

    /**
     * 计算链哈希。
     *
     * @param prevHash      前一条链哈希（链首传 null）
     * @param signatureId   本条签名ID
     * @param bizType       业务类型
     * @param bizId         业务单据ID（可空）
     * @param signerUserId  签署人用户ID
     * @param sealId        印章ID（可空）
     * @param meaning       签署含义
     * @param docHash       文档 SM3（可空）
     * @param signedAtEpochMs 签署时间戳（毫秒）
     * @return 64 位小写十六进制链哈希
     */
    public static String compute(String prevHash,
                                 Long signatureId,
                                 String bizType,
                                 Long bizId,
                                 Long signerUserId,
                                 Long sealId,
                                 String meaning,
                                 String docHash,
                                 long signedAtEpochMs) {
        return Sm3Utils.hashHex(
            prevHash,
            str(signatureId),
            bizType,
            str(bizId),
            str(signerUserId),
            str(sealId),
            meaning,
            docHash,
            str(signedAtEpochMs)
        );
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
```

- [ ] **Step 8: 运行测试确认通过**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest='Sm3UtilsTest,SignatureChainCalculatorTest'`
Expected: Tests run: 12, Failures: 0, Errors: 0

- [ ] **Step 9: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src
git commit -m "feat(sign): SM3 工具与哈希链计算器（含国密标准向量测试）"
```

---

## Task 6: 印章可用性判定与印章服务（TDD）

**Files:**
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/support/SealAvailabilityResolver.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/IESSealService.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/impl/EsSealServiceImpl.java`
- Test: `ruoyi-common-sign/src/test/java/com/nexus/common/sign/support/SealAvailabilityResolverTest.java`

- [ ] **Step 1: 编写失败测试 —— 印章可用性判定**

`SealAvailabilityResolverTest.java`：

```java
package com.nexus.common.sign.support;

import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 印章可用性判定测试。
 *
 * @author nexus
 */
class SealAvailabilityResolverTest {

    private static final long ME = 7L;
    private static final long MY_DEPT = 100L;
    private static final String BIZ = "APPROVAL";

    private static Date daysFromNow(int d) {
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_MONTH, d);
        return c.getTime();
    }

    private static EsSeal personal(Long owner, String status) {
        EsSeal s = new EsSeal();
        s.setSealId(1L);
        s.setSealType(SealType.PERSONAL);
        s.setOwnerUserId(owner);
        s.setStatus(status);
        return s;
    }

    private static EsSeal company(String status) {
        EsSeal s = new EsSeal();
        s.setSealId(2L);
        s.setSealType(SealType.COMPANY);
        s.setOwnerUserId(null);
        s.setStatus(status);
        return s;
    }

    private static EsSealGrant grant(Long userId, Long deptId, String bizType, String status, Date from, Date to) {
        EsSealGrant g = new EsSealGrant();
        g.setGrantId(50L);
        g.setSealId(2L);
        g.setGranteeUserId(userId);
        g.setGranteeDeptId(deptId);
        g.setBizType(bizType);
        g.setStatus(status);
        g.setValidFrom(from);
        g.setValidTo(to);
        return g;
    }

    @Test
    @DisplayName("个人章：本人 + 启用 -> 可用")
    void personal_ownerAndEnabled_available() {
        assertTrue(SealAvailabilityResolver.isUsable(
            personal(ME, SealStatus.ENABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("个人章：非本人 -> 不可用")
    void personal_notOwner_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            personal(999L, SealStatus.ENABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("个人章：停用 -> 不可用")
    void personal_disabled_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            personal(ME, SealStatus.DISABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("个人章：销毁 -> 不可用")
    void personal_destroyed_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            personal(ME, SealStatus.DESTROYED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：无授权 -> 不可用")
    void company_noGrant_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), List.of(), ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：用户级授权 + 启用 -> 可用")
    void company_userGrant_available() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：部门级授权 -> 可用")
    void company_deptGrant_available() {
        List<EsSealGrant> grants = List.of(
            grant(null, MY_DEPT, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权已过期 -> 不可用")
    void company_expiredGrant_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(-10), daysFromNow(-1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权未生效 -> 不可用")
    void company_futureGrant_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(1), daysFromNow(10)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权已停用 -> 不可用")
    void company_disabledGrant_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "0", daysFromNow(-1), daysFromNow(1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权限定其他场景 -> 不可用")
    void company_grantOtherBizType_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, "SO_CONTRACT", "1", daysFromNow(-1), daysFromNow(1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权限定本场景 -> 可用")
    void company_grantSameBizType_available() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, BIZ, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权不限场景(NULL) -> 可用")
    void company_grantNullBizType_available() {
        List<EsSealGrant> grants = List.of(
            grant(ME, null, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：授权人非我且部门不匹配 -> 不可用")
    void company_grantOtherUserAndDept_unavailable() {
        List<EsSealGrant> grants = List.of(
            grant(888L, 999L, null, "1", daysFromNow(-1), daysFromNow(1)));
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：无有效期限制(均为NULL) -> 可用")
    void company_noValidityWindow_available() {
        List<EsSealGrant> grants = List.of(grant(ME, null, null, "1", null, null));
        assertTrue(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), grants, ME, MY_DEPT, BIZ));
    }

    @Test
    @DisplayName("公司章：grants 为 null -> 不可用且不抛异常")
    void company_nullGrants_unavailable() {
        assertFalse(SealAvailabilityResolver.isUsable(
            company(SealStatus.ENABLED), null, ME, MY_DEPT, BIZ));
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=SealAvailabilityResolverTest`
Expected: 编译失败 —— `SealAvailabilityResolver` 不存在

- [ ] **Step 3: 实现 SealAvailabilityResolver**

`support/SealAvailabilityResolver.java`：

```java
package com.nexus.common.sign.support;

import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * 印章可用性判定（纯逻辑，无 IO）。
 *
 * <p>规则：</p>
 * <ul>
 *   <li>印章必须为「启用」状态</li>
 *   <li>个人章：仅归属人本人可用</li>
 *   <li>公司章：必须存在一条有效授权（用户级或部门级匹配、状态启用、
 *       当前时间落在有效期内、bizType 为空或与本次业务类型一致）</li>
 * </ul>
 *
 * <p><b>注意</b>：本类只做授权判定，<b>不校验</b>印章自身的 validFrom/validTo
 * 日期窗口 —— 该窗口由 {@code EsSealServiceImpl} 在入库/启停时校验，
 * 避免同一规则两处实现。</p>
 *
 * @author nexus
 */
public final class SealAvailabilityResolver {

    /** 授权启用状态 */
    private static final String GRANT_ENABLED = "1";

    private SealAvailabilityResolver() {
    }

    /**
     * 判断印章对指定用户在某业务场景下是否可用。
     *
     * @param seal       印章
     * @param grants     该印章的授权列表（可空）
     * @param userId     当前用户ID
     * @param deptId     当前用户部门ID
     * @param bizType    本次业务类型
     * @return 可用返回 true
     */
    public static boolean isUsable(EsSeal seal, List<EsSealGrant> grants,
                                   Long userId, Long deptId, String bizType) {
        if (seal == null || !SealStatus.ENABLED.equals(seal.getStatus())) {
            return false;
        }
        if (SealType.PERSONAL.equals(seal.getSealType())) {
            return Objects.equals(seal.getOwnerUserId(), userId);
        }
        if (!SealType.COMPANY.equals(seal.getSealType())) {
            return false;
        }
        if (grants == null || grants.isEmpty()) {
            return false;
        }
        Date now = new Date();
        for (EsSealGrant g : grants) {
            if (!GRANT_ENABLED.equals(g.getStatus())) {
                continue;
            }
            if (g.getValidFrom() != null && now.before(g.getValidFrom())) {
                continue;
            }
            if (g.getValidTo() != null && now.after(g.getValidTo())) {
                continue;
            }
            if (g.getBizType() != null && !g.getBizType().isBlank()
                && !g.getBizType().equals(bizType)) {
                continue;
            }
            boolean userMatch = g.getGranteeUserId() != null
                && Objects.equals(g.getGranteeUserId(), userId);
            boolean deptMatch = g.getGranteeDeptId() != null
                && Objects.equals(g.getGranteeDeptId(), deptId);
            if (userMatch || deptMatch) {
                return true;
            }
        }
        return false;
    }
}
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=SealAvailabilityResolverTest`
Expected: Tests run: 16, Failures: 0, Errors: 0

- [ ] **Step 5: 定义印章服务接口**

`service/IESSealService.java`：

```java
package com.nexus.common.sign.service;

import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.domain.bo.SealQueryBo;
import com.nexus.common.sign.domain.vo.AvailableSealVo;
import com.nexus.common.sign.domain.vo.SealVo;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;

import java.util.List;

/**
 * 印章服务。
 *
 * @author nexus
 */
public interface IESSealService {

    /** 分页查询印章 */
    TableDataInfo<SealVo> selectPageList(SealQueryBo bo, PageQuery pageQuery);

    /** 查询印章列表 */
    List<SealVo> selectList(SealQueryBo bo);

    /** 按ID查询印章 */
    SealVo selectById(Long sealId);

    /** 新增印章 */
    Long insertByBo(SealBo bo);

    /** 修改印章 */
    int updateByBo(SealBo bo);

    /** 批量删除印章（逻辑删除） */
    int deleteByIds(List<Long> sealIds);

    /** 启停/销毁印章 */
    int changeStatus(Long sealId, String status);

    /** 查询当前用户在指定业务场景下可用的印章 */
    List<AvailableSealVo> listAvailableSeals(String bizType);

    /** 读取印章预览图字节（低清+水印）；前端唯一可获取的图像入口 */
    byte[] loadPreviewImage(Long sealId);
}
```

- [ ] **Step 6: 实现印章服务**

`service/impl/EsSealServiceImpl.java`：

```java
package com.nexus.common.sign.service.impl;

import cn.hutool.core.util.ObjectUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.domain.bo.SealQueryBo;
import com.nexus.common.sign.domain.vo.AvailableSealVo;
import com.nexus.common.sign.domain.vo.SealVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.service.IESSealService;
import com.nexus.common.sign.support.SealAvailabilityResolver;
import com.nexus.common.sign.util.Sm3Utils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 印章服务实现。
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EsSealServiceImpl implements IESSealService {

    private final EsSealMapper baseMapper;
    private final EsSealGrantMapper grantMapper;

    @Override
    public TableDataInfo<SealVo> selectPageList(SealQueryBo bo, PageQuery pageQuery) {
        Page<SealVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<SealVo> selectList(SealQueryBo bo) {
        return baseMapper.selectVoList(buildWrapper(bo));
    }

    @Override
    public SealVo selectById(Long sealId) {
        return baseMapper.selectVoById(sealId);
    }

    @Override
    public Long insertByBo(SealBo bo) {
        validateSealBo(bo, null);
        EsSeal entity = MapstructUtils.convert(bo, EsSeal.class);
        entity.setStatus(SealStatus.DRAFT);
        entity.setSealVersion(1);
        baseMapper.insert(entity);
        return entity.getSealId();
    }

    @Override
    public int updateByBo(SealBo bo) {
        EsSeal existing = baseMapper.selectById(bo.getSealId());
        if (existing == null) {
            throw new ServiceException("印章不存在");
        }
        if (SealStatus.DESTROYED.equals(existing.getStatus())) {
            throw new ServiceException("印章已销毁，不可修改");
        }
        validateSealBo(bo, existing);
        EsSeal entity = MapstructUtils.convert(bo, EsSeal.class);
        boolean faceChanged = !ObjectUtil.equal(existing.getSealOssId(), bo.getSealOssId());
        if (faceChanged) {
            entity.setSealVersion(existing.getSealVersion() + 1);
        }
        return baseMapper.updateById(entity);
    }

    @Override
    public int deleteByIds(List<Long> sealIds) {
        for (Long id : sealIds) {
            EsSeal seal = baseMapper.selectById(id);
            if (seal == null) {
                continue;
            }
            if (SealStatus.ENABLED.equals(seal.getStatus())) {
                throw new ServiceException("印章【{}】处于启用状态，请先停用", seal.getSealName());
            }
        }
        return baseMapper.deleteByIds(sealIds);
    }

    @Override
    public int changeStatus(Long sealId, String status) {
        EsSeal seal = baseMapper.selectById(sealId);
        if (seal == null) {
            throw new ServiceException("印章不存在");
        }
        if (SealStatus.DESTROYED.equals(seal.getStatus())) {
            throw new ServiceException("印章已销毁，状态不可再变更");
        }
        if (SealStatus.DESTROYED.equals(status)) {
            // 销毁为终态：先校验历史签名仍可引用（印章不物理删除，非阻断条件），仅记录
            log.warn("印章销毁: sealId={}, name={}, operator={}", sealId, seal.getSealName(),
                LoginHelper.getUserId());
        }
        EsSeal update = new EsSeal();
        update.setSealId(sealId);
        update.setStatus(status);
        return baseMapper.updateById(update);
    }

    @Override
    public List<AvailableSealVo> listAvailableSeals(String bizType) {
        Long userId = LoginHelper.getUserId();
        Long deptId = LoginHelper.getDeptId();

        // 1. 个人章：本人 + 启用
        List<EsSeal> personal = baseMapper.selectList(
            Wrappers.<EsSeal>lambdaQuery()
                .eq(EsSeal::getSealType, SealType.PERSONAL)
                .eq(EsSeal::getOwnerUserId, userId)
                .eq(EsSeal::getStatus, SealStatus.ENABLED));

        // 2. 公司章：已授权的启用章
        List<EsSeal> company = baseMapper.selectList(
            Wrappers.<EsSeal>lambdaQuery()
                .eq(EsSeal::getSealType, SealType.COMPANY)
                .eq(EsSeal::getStatus, SealStatus.ENABLED));

        List<AvailableSealVo> result = new ArrayList<>();
        personal.forEach(s -> result.add(toAvailable(s)));
        for (EsSeal seal : company) {
            List<EsSealGrant> grants = grantMapper.selectList(
                Wrappers.<EsSealGrant>lambdaQuery().eq(EsSealGrant::getSealId, seal.getSealId()));
            if (SealAvailabilityResolver.isUsable(seal, grants, userId, deptId, bizType)) {
                result.add(toAvailable(seal));
            }
        }
        return result;
    }

    @Override
    public byte[] loadPreviewImage(Long sealId) {
        EsSeal seal = baseMapper.selectById(sealId);
        if (seal == null) {
            throw new ServiceException("印章不存在");
        }
        Long ossId = seal.getPreviewOssId() != null ? seal.getPreviewOssId() : seal.getSealOssId();
        // P1 直接从 OSS 读字节；previewOssId 由上传流程生成（低清+水印）。
        // 若 previewOssId 为空则回退原图，生产环境上传流程必须生成预览图。
        log.debug("读取印章预览图: sealId={}, ossId={}, hash={}",
            sealId, ossId, Sm3Utils.hashHex(String.valueOf(ossId)));
        return com.nexus.common.oss.service.SysOssServiceSupport.readBytes(ossId);
    }

    private AvailableSealVo toAvailable(EsSeal s) {
        AvailableSealVo vo = new AvailableSealVo();
        vo.setSealId(s.getSealId());
        vo.setSealType(s.getSealType());
        vo.setSealName(s.getSealName());
        vo.setPreviewOssId(s.getPreviewOssId());
        vo.setImgWidth(s.getImgWidth());
        vo.setImgHeight(s.getImgHeight());
        return vo;
    }

    private void validateSealBo(SealBo bo, EsSeal existing) {
        if (SealType.PERSONAL.equals(bo.getSealType())) {
            if (bo.getOwnerUserId() == null) {
                throw new ServiceException("个人章必须指定归属用户");
            }
        } else if (SealType.COMPANY.equals(bo.getSealType())) {
            if (bo.getOwnerUserId() != null) {
                throw new ServiceException("公司章不允许指定归属用户");
            }
        } else {
            throw new ServiceException("印章类型非法：" + bo.getSealType());
        }
        if (bo.getValidFrom() != null && bo.getValidTo() != null
            && bo.getValidFrom().after(bo.getValidTo())) {
            throw new ServiceException("生效日期不能晚于失效日期");
        }
        assertSealNameUnique(bo, existing);
    }

    private void assertSealNameUnique(SealBo bo, EsSeal existing) {
        Long excludeId = existing == null ? null : existing.getSealId();
        Long count = baseMapper.selectCount(
            Wrappers.<EsSeal>lambdaQuery()
                .eq(EsSeal::getSealName, bo.getSealName())
                .ne(excludeId != null, EsSeal::getSealId, excludeId));
        if (count != null && count > 0) {
            throw new ServiceException("印章名称【{}】已存在", bo.getSealName());
        }
    }

    private LambdaQueryWrapper<EsSeal> buildWrapper(SealQueryBo bo) {
        LambdaQueryWrapper<EsSeal> lqw = Wrappers.lambdaQuery();
        lqw.eq(StringUtils.isNotBlank(bo.getSealType()), EsSeal::getSealType, bo.getSealType());
        lqw.like(StringUtils.isNotBlank(bo.getSealName()), EsSeal::getSealName, bo.getSealName());
        lqw.eq(StringUtils.isNotBlank(bo.getStatus()), EsSeal::getStatus, bo.getStatus());
        if (Boolean.TRUE.equals(bo.getOnlyAvailable())) {
            Long userId = LoginHelper.getUserId();
            lqw.and(w -> w
                .and(p -> p.eq(EsSeal::getSealType, SealType.PERSONAL).eq(EsSeal::getOwnerUserId, userId))
                .or(p -> p.eq(EsSeal::getSealType, SealType.COMPANY)));
        }
        lqw.orderByDesc(EsSeal::getCreateTime);
        return lqw;
    }
}
```

> **`SysOssServiceSupport`**：`loadPreviewImage` 需要读取 OSS 字节。本仓库既有读法见
> `CvFaceBatchRegisterService.loadAvatar()` 与 `FaceVerifyBackfillTask`（通过 `sysOssService.getById(ossId)`
> 拿到 `SysOssVo` 再取流）。**实施时按该既有写法改写 `loadPreviewImage`**，不要引入不存在的工具类：
>
> ```java
> SysOssVo ossVo = sysOssService.getById(ossId);
> if (ossVo == null) {
>     throw new ServiceException("印章图片不存在");
> }
> return sysOssService.downloadBytes(ossId);
> ```
> 若 `sysOssService` 无 `downloadBytes`，参照 `SchAttendanceController.facePhoto()` 的
> `sysOssService.download(ossId, response)` 改为控制器直出流（见 Task 9 Step 3）。

- [ ] **Step 7: 编译验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am compile -DskipTests`
Expected: BUILD SUCCESS（若因 `SysOssServiceSupport` 报错，按 Step 6 备注改用 `sysOssService`）

- [ ] **Step 8: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src
git commit -m "feat(sign): 印章可用性判定与印章服务"
```

---

## Task 7: 用章授权服务

**Files:**
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/IESSealGrantService.java`
- Create: `ruoyi-common-sign/src/main/java/com/nexus/common/sign/service/impl/EsSealGrantServiceImpl.java`

- [ ] **Step 1: 定义授权服务接口**

`service/IESSealGrantService.java`：

```java
package com.nexus.common.sign.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.domain.vo.SealGrantVo;

import java.util.List;

/**
 * 用章授权服务。
 *
 * @author nexus
 */
public interface IESSealGrantService {

    TableDataInfo<SealGrantVo> selectPageList(SealGrantBo bo, PageQuery pageQuery);

    List<SealGrantVo> selectListBySeal(Long sealId);

    Long insertByBo(SealGrantBo bo);

    int updateByBo(SealGrantBo bo);

    int deleteByIds(List<Long> grantIds);
}
```

- [ ] **Step 2: 实现授权服务**

`service/impl/EsSealGrantServiceImpl.java`：

```java
package com.nexus.common.sign.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.domain.vo.SealGrantVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.service.IESSealGrantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用章授权服务实现。
 *
 * @author nexus
 */
@Service
@RequiredArgsConstructor
public class EsSealGrantServiceImpl implements IESSealGrantService {

    private final EsSealGrantMapper baseMapper;
    private final EsSealMapper sealMapper;

    @Override
    public TableDataInfo<SealGrantVo> selectPageList(SealGrantBo bo, PageQuery pageQuery) {
        Page<SealGrantVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<SealGrantVo> selectListBySeal(Long sealId) {
        return baseMapper.selectVoList(
            Wrappers.<EsSealGrant>lambdaQuery()
                .eq(EsSealGrant::getSealId, sealId)
                .orderByDesc(EsSealGrant::getCreateTime));
    }

    @Override
    public Long insertByBo(SealGrantBo bo) {
        validate(bo, null);
        EsSealGrant entity = MapstructUtils.convert(bo, EsSealGrant.class);
        entity.setGrantBy(LoginHelper.getUserId());
        if (entity.getStatus() == null) {
            entity.setStatus("1");
        }
        baseMapper.insert(entity);
        return entity.getGrantId();
    }

    @Override
    public int updateByBo(SealGrantBo bo) {
        EsSealGrant existing = baseMapper.selectById(bo.getGrantId());
        if (existing == null) {
            throw new ServiceException("授权记录不存在");
        }
        validate(bo, existing);
        return baseMapper.updateById(MapstructUtils.convert(bo, EsSealGrant.class));
    }

    @Override
    public int deleteByIds(List<Long> grantIds) {
        return baseMapper.deleteByIds(grantIds);
    }

    private void validate(SealGrantBo bo, EsSealGrant existing) {
        if (bo.getGranteeUserId() == null && bo.getGranteeDeptId() == null) {
            throw new ServiceException("被授权用户与被授权部门至少填写一项");
        }
        if (bo.getValidFrom() != null && bo.getValidTo() != null
            && bo.getValidFrom().after(bo.getValidTo())) {
            throw new ServiceException("授权生效时间不能晚于失效时间");
        }
        EsSeal seal = sealMapper.selectById(bo.getSealId());
        if (seal == null) {
            throw new ServiceException("印章不存在");
        }
        if (!com.nexus.common.sign.constants.SealType.COMPANY.equals(seal.getSealType())) {
            throw new ServiceException("仅公司章需要授权，个人章仅归属人本人可用");
        }
        if (SealStatus.DESTROYED.equals(seal.getStatus())) {
            throw new ServiceException("印章已销毁，不可再授权");
        }
    }

    private LambdaQueryWrapper<EsSealGrant> buildWrapper(SealGrantBo bo) {
        LambdaQueryWrapper<EsSealGrant> lqw = Wrappers.lambdaQuery();
        lqw.eq(bo.getSealId() != null, EsSealGrant::getSealId, bo.getSealId());
        lqw.eq(bo.getGranteeUserId() != null, EsSealGrant::getGranteeUserId, bo.getGranteeUserId());
        lqw.eq(bo.getGranteeDeptId() != null, EsSealGrant::getGranteeDeptId, bo.getGranteeDeptId());
        lqw.eq(org.apache.commons.lang3.StringUtils.isNotBlank(bo.getStatus()),
            EsSealGrant::getStatus, bo.getStatus());
        lqw.orderByDesc(EsSealGrant::getCreateTime);
        return lqw;
    }
}
```

> **`SealGrantVo` 的 `sealName` / `granteeUserName` / `granteeDeptName`**：用本仓库既有的
> `@Translation` 机制在 VO 上翻译（参照 `BdClientVo` 的 `@Translation(type = TransConstant.OSS_ID_TO_URL, ...)`
> 写法，用户/部门用 `TransConstant.USER_ID_TO_NAME` / `DEPT_ID_TO_NAME`）。
> 若新增翻译类型需实现 `TranslationInterface` —— **实施时先查 `ruoyi-common-translation` 现有翻译器清单**，
> 存在则直接注解，不存在则本期改为在 Service 层用 `SysUserService`/`SysDeptService` 手工填充，不新增翻译器。

- [ ] **Step 3: 编译验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 4: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src
git commit -m "feat(sign): 用章授权服务"
```

---

## Task 8: 二级认证（SPI 设计，TDD）

> **模块边界约束**：`ruoyi-common-sign` **不可依赖** `ruoyi-system`（后者是业务模块，依赖方向必须保持 common → 不依赖 module）。
> 密码校验需要 `sys_user.password`，故采用 **SPI**：接口在 `ruoyi-common-sign`，密码实现放 `ruoyi-system`。

**Files:**
- Create: `ruoyi-common-sign/.../support/SignCredentialChecker.java`
- Create: `ruoyi-common-sign/.../support/SignAuthVerifier.java`
- Create: `ruoyi-common-sign/.../support/SmsSignCredentialChecker.java`
- Create: `ruoyi-modules/ruoyi-system/.../sign/PasswordSignCredentialChecker.java`
- Modify: `ruoyi-modules/ruoyi-system/pom.xml`（新增 `ruoyi-common-sign` 依赖）
- Test: `ruoyi-common-sign/src/test/java/com/nexus/common/sign/support/SignAuthVerifierTest.java`

- [ ] **Step 1: 定义 SPI 接口**

`support/SignCredentialChecker.java`：

```java
package com.nexus.common.sign.support;

/**
 * 签署凭证校验器 SPI。
 * <p>由具备相应数据的模块提供实现（如密码校验需 {@code sys_user}，实现放 ruoyi-system）。</p>
 *
 * @author nexus
 */
public interface SignCredentialChecker {

    /**
     * 支持的认证方式，取值见 {@code SignAuthMethod}。
     */
    String authMethod();

    /**
     * 校验凭证，失败抛 {@code ServiceException}。
     *
     * @param userId     签署人用户ID
     * @param credential 凭证（明文，仅本次请求内存中使用，不落库不记日志）
     */
    void verify(Long userId, String credential);
}
```

- [ ] **Step 2: 编写失败测试 —— 认证调度器**

`SignAuthVerifierTest.java`：

```java
package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 二级认证调度器测试。
 *
 * @author nexus
 */
class SignAuthVerifierTest {

    private static final Long UID = 7L;

    /** 记录被调用情况的假实现 */
    private static class FakeChecker implements SignCredentialChecker {
        final String method;
        final AtomicBoolean called = new AtomicBoolean(false);
        final boolean shouldFail;

        FakeChecker(String method, boolean shouldFail) {
            this.method = method;
            this.shouldFail = shouldFail;
        }

        @Override
        public String authMethod() {
            return method;
        }

        @Override
        public void verify(Long userId, String credential) {
            called.set(true);
            if (shouldFail) {
                throw new ServiceException("凭证错误");
            }
        }
    }

    @Test
    @DisplayName("LOGIN_ONLY: 不调用任何 checker，直接通过")
    void loginOnly_passesWithoutChecker() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, false);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertDoesNotThrow(() -> v.verify(UID, SignAuthMethod.LOGIN_ONLY, null));
        assertFalse(c.called.get(), "LOGIN_ONLY 不应调用其它 checker");
    }

    @Test
    @DisplayName("PASSWORD: 委派给对应 checker 并传参")
    void password_delegatesToChecker() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, false);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertDoesNotThrow(() -> v.verify(UID, SignAuthMethod.PASSWORD, "secret"));
        assertTrue(c.called.get());
    }

    @Test
    @DisplayName("PASSWORD: checker 抛异常时向上传播")
    void password_checkerFailure_propagates() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, true);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertThrows(ServiceException.class, () -> v.verify(UID, SignAuthMethod.PASSWORD, "wrong"));
    }

    @Test
    @DisplayName("未支持的认证方式: 抛 ServiceException")
    void unsupportedMethod_throws() {
        SignAuthVerifier v = new SignAuthVerifier(List.of());
        ServiceException ex = assertThrows(ServiceException.class,
            () -> v.verify(UID, SignAuthMethod.FACE, "x"));
        assertTrue(ex.getMessage().contains("不支持"));
    }

    @Test
    @DisplayName("缺凭证: 抛 ServiceException（不允许降级）")
    void missingCredential_throws() {
        FakeChecker c = new FakeChecker(SignAuthMethod.PASSWORD, false);
        SignAuthVerifier v = new SignAuthVerifier(List.of(c));
        assertThrows(ServiceException.class, () -> v.verify(UID, SignAuthMethod.PASSWORD, ""));
        assertFalse(c.called.get());
    }

    @Test
    @DisplayName("认证方式为 null: 抛 ServiceException")
    void nullMethod_throws() {
        SignAuthVerifier v = new SignAuthVerifier(List.of());
        assertThrows(ServiceException.class, () -> v.verify(UID, null, "x"));
    }

    @Test
    @DisplayName("多个 checker: 按 authMethod 精确匹配")
    void multipleCheckers_exactMatch() {
        FakeChecker pwd = new FakeChecker(SignAuthMethod.PASSWORD, false);
        FakeChecker sms = new FakeChecker(SignAuthMethod.SMS, true);
        SignAuthVerifier v = new SignAuthVerifier(List.of(pwd, sms));
        assertDoesNotThrow(() -> v.verify(UID, SignAuthMethod.PASSWORD, "ok"));
        assertTrue(pwd.called.get());
        assertFalse(sms.called.get());
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=SignAuthVerifierTest`
Expected: 编译失败 —— `SignAuthVerifier` 不存在

- [ ] **Step 4: 实现 SignAuthVerifier**

`support/SignAuthVerifier.java`：

```java
package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 签署二级认证调度器。
 * <p>原则：认证失败即拒绝签名，<b>不允许降级</b>。</p>
 *
 * @author nexus
 */
@Component
public class SignAuthVerifier {

    private final Map<String, SignCredentialChecker> checkers = new HashMap<>();

    public SignAuthVerifier(List<SignCredentialChecker> checkerList) {
        if (checkerList != null) {
            for (SignCredentialChecker c : checkerList) {
                this.checkers.put(c.authMethod(), c);
            }
        }
    }

    /**
     * 校验凭证。
     *
     * @param userId     签署人用户ID
     * @param authMethod 认证方式
     * @param credential 凭证
     */
    public void verify(Long userId, String authMethod, String credential) {
        if (StringUtils.isBlank(authMethod)) {
            throw new ServiceException("签署认证方式不能为空");
        }
        if (SignAuthMethod.LOGIN_ONLY.equals(authMethod)) {
            return;
        }
        SignCredentialChecker checker = checkers.get(authMethod);
        if (checker == null) {
            throw new ServiceException("不支持的签署认证方式：{}", authMethod);
        }
        if (StringUtils.isBlank(credential)) {
            throw new ServiceException("请提供签署认证凭证");
        }
        checker.verify(userId, credential);
    }
}
```

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=SignAuthVerifierTest`
Expected: Tests run: 7, Failures: 0, Errors: 0

- [ ] **Step 6: 实现密码校验器（放 ruoyi-system）**

先在 `ruoyi-modules/ruoyi-system/pom.xml` 的 `<dependencies>` 中追加：

```xml
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sign</artifactId>
        </dependency>
```

创建 `ruoyi-modules/ruoyi-system/src/main/java/com/nexus/system/sign/PasswordSignCredentialChecker.java`：

```java
package com.nexus.system.sign;

import cn.hutool.crypto.digest.BCrypt;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.support.SignCredentialChecker;
import com.nexus.system.domain.SysUser;
import com.nexus.system.mapper.SysUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 密码签署凭证校验器。
 * <p>用户登录密码再次录入校验，<b>不复用会话票据</b>；凭证不落库、不记日志。</p>
 *
 * @author nexus
 */
@Component
@RequiredArgsConstructor
public class PasswordSignCredentialChecker implements SignCredentialChecker {

    private final SysUserMapper userMapper;

    @Override
    public String authMethod() {
        return SignAuthMethod.PASSWORD;
    }

    @Override
    public void verify(Long userId, String credential) {
        SysUser user = userMapper.selectById(userId);
        if (user == null) {
            throw new ServiceException("用户不存在");
        }
        if (user.getPassword() == null || !BCrypt.checkpw(credential, user.getPassword())) {
            throw new ServiceException("签署密码错误");
        }
    }
}
```

> **实施提示**：`SysUserMapper.getById` 可能被数据权限拦截；若报错，参照 `SysProfileController` 中
> `DataPermissionHelper.ignore(() -> ...)` 的既有写法包裹查询。

- [ ] **Step 7: 实现短信校验器**

`ruoyi-common-sign/.../support/SmsSignCredentialChecker.java`：

```java
package com.nexus.common.sign.support;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.system.domain.SysUser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 短信验证码签署凭证校验器。
 * <p>验证码由 {@code EsSignatureController.sendSmsCode()} 发送并写入 Redis；
 * 校验成功后立即删除（防重放）。</p>
 *
 * @author nexus
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SmsSignCredentialChecker implements SignCredentialChecker {

    /** 验证码 Redis key 前缀 */
    public static final String CODE_KEY_PREFIX = "es:sign:sms:";
    /** 验证码有效期 */
    public static final Duration CODE_TTL = Duration.ofMinutes(5);
    /** 连续错误锁定阈值 */
    public static final int MAX_FAIL = 5;
    /** 锁定 key 前缀 */
    public static final String LOCK_KEY_PREFIX = "es:sign:sms:lock:";
    /** 锁定时长 */
    public static final Duration LOCK_TTL = Duration.ofMinutes(10);

    @Override
    public String authMethod() {
        return SignAuthMethod.SMS;
    }

    @Override
    public void verify(Long userId, String credential) {
        String lockKey = LOCK_KEY_PREFIX + userId;
        if (Boolean.TRUE.equals(RedisUtils.hasKey(lockKey))) {
            throw new ServiceException("验证码错误次数过多，请 10 分钟后重试");
        }
        String key = CODE_KEY_PREFIX + userId;
        String expected = RedisUtils.getCacheObject(key);
        if (StringUtils.isBlank(expected)) {
            throw new ServiceException("验证码已失效，请重新获取");
        }
        if (!expected.equals(credential)) {
            long fails = RedisUtils.incrAtomicValue(LOCK_KEY_PREFIX + "cnt:" + userId);
            if (fails >= MAX_FAIL) {
                RedisUtils.setCacheObject(lockKey, Boolean.TRUE, LOCK_TTL);
            }
            throw new ServiceException("签署验证码错误");
        }
        // 校验通过立即失效，防重放
        RedisUtils.deleteObject(key);
        RedisUtils.deleteObject(LOCK_KEY_PREFIX + "cnt:" + userId);
    }
}
```

> **实施提示**：`RedisUtils` 的方法名以仓库实际实现为准（`ruoyi-common-redis`）。
> 实施前执行 `grep -n "public static" ruoyi-common/ruoyi-common-redis/src/main/java/com/nexus/common/redis/utils/RedisUtils.java`
> 核对可用方法，按实际签名替换 `hasKey` / `getCacheObject` / `setCacheObject` / `incrAtomicValue` / `deleteObject`。

- [ ] **Step 8: 编译与测试验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign,ruoyi-modules/ruoyi-system -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 9: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign ruoyi-modules/ruoyi-system
git commit -m "feat(sign): 二级认证 SPI 与密码/短信校验器"
```

---

## Task 9: 签名核心 sign()（TDD）

**Files:**
- Create: `ruoyi-common-sign/.../service/ESignatureService.java`
- Create: `ruoyi-common-sign/.../service/impl/ESignatureServiceImpl.java`
- Test: `ruoyi-common-sign/src/test/java/com/nexus/common/sign/service/impl/ESignatureServiceImplTest.java`

- [ ] **Step 1: 定义核心服务接口**

`service/ESignatureService.java`：

```java
package com.nexus.common.sign.service;

import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;

import java.util.List;

/**
 * 电子签名统一入口。
 *
 * <p>P1 仅实现「逻辑签名」（不产出新文件）；PDF 盖章在 P2 通过
 * {@code signDocument} 扩展，信任根通过 SPI 预留。</p>
 *
 * @author nexus
 */
public interface ESignatureService {

    /**
     * 逻辑签名：审批签批 / SOP 查阅 / NCR 处置。
     *
     * @param cmd 签名入参
     * @return 签名ID（回填业务表的 signature_id 字段）
     */
    Long sign(SignCommand cmd);

    /**
     * 验签：校验单条记录自身哈希 + 与上一条的链接。
     *
     * @param signatureId 签名ID
     * @return 验签结果
     */
    VerifyResultVo verify(Long signatureId);

    /**
     * 全链校验：对该业务单据的全部签名按序重算并校验链接。
     *
     * @param bizType 业务类型
     * @param bizId   业务单据ID
     * @return 验签结果
     */
    VerifyResultVo verifyChain(String bizType, Long bizId);

    /**
     * 按业务查询签名列表（按签署时间正序）。
     */
    List<SignatureVo> listByBiz(String bizType, Long bizId);
}
```

- [ ] **Step 2: 编写失败测试 —— sign() 核心行为**

`ESignatureServiceImplTest.java`：

```java
package com.nexus.common.sign.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 签名核心服务测试。
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ESignatureServiceImplTest {

    @Mock private EsSignatureMapper signatureMapper;
    @Mock private EsSealMapper sealMapper;
    @Mock private EsSealGrantMapper grantMapper;
    @Mock private SignAuthVerifier authVerifier;
    @Mock private LockTemplate lockTemplate;
    @Captor private ArgumentCaptor<EsSignature> sigCaptor;

    private ESignatureServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;
    private MockedStatic<com.nexus.common.satoken.utils.LoginHelper> loginHelperMock;

    private static final Long ME = 7L;
    private static final String BIZ = SignBizType.APPROVAL;

    @BeforeEach
    void setUp() {
        service = new ESignatureServiceImpl(signatureMapper, sealMapper, grantMapper, authVerifier, lockTemplate);
        // MyBatis-Plus lambda 缓存
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SIGNATUREID", "TENANTID", "BIZTYPE", "BIZID", "BIZNO",
            "SIGNERUSERID", "SIGNERNAME", "SIGNERDEPTID", "SEALID", "SEALTYPE",
            "SIGNATUREMEANING", "AUTHMETHOD", "SRCDOCOSSID", "SRCDOCHASH", "DOCOSSID",
            "DOCHASH", "DOCID", "SIGNEDAT", "SIGNIP", "SIGNUA", "PREVHASH", "CHAINHASH", "REMARK"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSignature.class)).thenReturn(cols);

        // 登录态
        loginHelperMock = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getUserId).thenReturn(ME);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getTenantId).thenReturn("000000");
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getDeptId).thenReturn(100L);

        // 锁总是获取成功
        LockInfo info = new LockInfo();
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(info);
        // 默认：链首（无历史记录）
        when(signatureMapper.selectOne(any())).thenReturn(null);
        when(signatureMapper.insert(any(EsSignature.class))).thenReturn(1);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
        if (loginHelperMock != null) loginHelperMock.close();
    }

    private SignCommand cmd(Long sealId) {
        return new SignCommand(BIZ, 100L, "AP-001", "审批通过", sealId, SignAuthMethod.LOGIN_ONLY, null);
    }

    private EsSeal enabledPersonal() {
        EsSeal s = new EsSeal();
        s.setSealId(9L);
        s.setSealType(SealType.PERSONAL);
        s.setOwnerUserId(ME);
        s.setStatus(SealStatus.ENABLED);
        return s;
    }

    @Test
    @DisplayName("sign: 无印章的逻辑签名成功，链首 prevHash 为 null")
    void sign_withoutSeal_chainHead() {
        Long id = service.sign(cmd(null));

        assertNotNull(id, "应返回非空签名ID");
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();
        assertNull(saved.getPrevHash(), "链首 prevHash 应为 null");
        assertEquals(ME, saved.getSignerUserId());
        assertEquals("审批通过", saved.getSignatureMeaning());
        assertEquals(SignAuthMethod.LOGIN_ONLY, saved.getAuthMethod());
        assertEquals(BIZ, saved.getBizType());
        assertEquals(100L, saved.getBizId());
        assertNotNull(saved.getChainHash());
        assertEquals(64, saved.getChainHash().length());
        assertNotNull(saved.getSignedAt());
    }

    @Test
    @DisplayName("sign: chainHash 与计算器结果一致（可被验签复现）")
    void sign_chainHashReproducible() {
        service.sign(cmd(null));
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();

        String expected = SignatureChainCalculator.compute(
            saved.getPrevHash(), saved.getSignatureId(), saved.getBizType(), saved.getBizId(),
            saved.getSignerUserId(), saved.getSealId(), saved.getSignatureMeaning(),
            saved.getDocHash(), saved.getSignedAt().getTime());
        assertEquals(expected, saved.getChainHash());
    }

    @Test
    @DisplayName("sign: 链首之后 prevHash 取自上一条的 chainHash")
    void sign_linksToPrevious() {
        EsSignature prev = new EsSignature();
        prev.setSignatureId(1L);
        prev.setChainHash("a".repeat(64));
        when(signatureMapper.selectOne(any())).thenReturn(prev);

        service.sign(cmd(null));
        verify(signatureMapper).insert(sigCaptor.capture());
        assertEquals("a".repeat(64), sigCaptor.getValue().getPrevHash());
    }

    @Test
    @DisplayName("sign: 本人个人章 -> 通过并记录 sealId/sealType")
    void sign_personalSealOfSelf_ok() {
        when(sealMapper.selectById(9L)).thenReturn(enabledPersonal());

        service.sign(cmd(9L));

        verify(signatureMapper).insert(sigCaptor.capture());
        assertEquals(9L, sigCaptor.getValue().getSealId());
        assertEquals(SealType.PERSONAL, sigCaptor.getValue().getSealType());
    }

    @Test
    @DisplayName("sign: 他人个人章 -> 抛异常且不写库")
    void sign_personalSealOfOthers_rejected() {
        EsSeal other = enabledPersonal();
        other.setOwnerUserId(999L);
        when(sealMapper.selectById(9L)).thenReturn(other);

        assertThrows(ServiceException.class, () -> service.sign(cmd(9L)));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("sign: 印章不存在 -> 抛异常")
    void sign_sealNotFound_throws() {
        when(sealMapper.selectById(9L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.sign(cmd(9L)));
    }

    @Test
    @DisplayName("sign: 停用印章 -> 抛异常")
    void sign_disabledSeal_rejected() {
        EsSeal s = enabledPersonal();
        s.setStatus(SealStatus.DISABLED);
        when(sealMapper.selectById(9L)).thenReturn(s);
        assertThrows(ServiceException.class, () -> service.sign(cmd(9L)));
    }

    @Test
    @DisplayName("sign: 认证失败 -> 抛异常且不写库（不允许降级）")
    void sign_authFailure_rejected() {
        doThrow(new ServiceException("签署密码错误"))
            .when(authVerifier).verify(anyLong(), anyString(), any());

        assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(BIZ, 100L, null, "审批通过", null, SignAuthMethod.PASSWORD, "bad")));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("sign: 缺 bizType -> 抛异常")
    void sign_blankBizType_rejected() {
        assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(" ", 100L, null, "审批通过", null, SignAuthMethod.LOGIN_ONLY, null)));
    }

    @Test
    @DisplayName("sign: 缺签署含义 -> 抛异常")
    void sign_blankMeaning_rejected() {
        assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(BIZ, 100L, null, "  ", null, SignAuthMethod.LOGIN_ONLY, null)));
    }

    @Test
    @DisplayName("sign: 取不到锁 -> 抛异常且不写库")
    void sign_lockUnavailable_rejected() {
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.sign(cmd(null)));
        verify(signatureMapper, never()).insert(any(EsSignature.class));
    }

    @Test
    @DisplayName("sign: 无论成功失败都释放锁")
    void sign_alwaysReleasesLock() {
        LockInfo info = new LockInfo();
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(info);

        service.sign(cmd(null));

        verify(lockTemplate).releaseLock(info);
    }
}
```

- [ ] **Step 3: 运行测试确认失败**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=ESignatureServiceImplTest`
Expected: 编译失败 —— `ESignatureServiceImpl` 不存在

- [ ] **Step 4: 实现 ESignatureServiceImpl.sign()**

`service/impl/ESignatureServiceImpl.java`（本步骤先实现 `sign`，`verify*` 在 Task 10 补齐；方法体先返回 `null` 并留 `// Task 10` 注释以保证编译通过）：

```java
package com.nexus.common.sign.service.impl;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSealGrant;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.support.SealAvailabilityResolver;
import com.nexus.common.sign.support.SignatureChainCalculator;
import com.nexus.common.sign.support.SignAuthVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 电子签名统一入口实现。
 *
 * @author nexus
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ESignatureServiceImpl implements ESignatureService {

    /** 链锁 key 前缀（按租户串行，保证链不分叉） */
    private static final String CHAIN_LOCK_PREFIX = "es:sign:chain:";
    /** 链锁持有时长(ms) */
    private static final long LOCK_EXPIRE = 30_000L;
    /** 链锁获取超时(ms) */
    private static final long LOCK_ACQUIRE_TIMEOUT = 10_000L;

    private final EsSignatureMapper signatureMapper;
    private final EsSealMapper sealMapper;
    private final EsSealGrantMapper grantMapper;
    private final SignAuthVerifier authVerifier;
    private final LockTemplate lockTemplate;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long sign(SignCommand cmd) {
        validate(cmd);

        Long userId = LoginHelper.getUserId();
        String tenantId = LoginHelper.getTenantId();
        Long deptId = LoginHelper.getDeptId();

        // 1. 印章鉴权（指定印章时必须可用）
        EsSeal seal = null;
        if (cmd.sealId() != null) {
            seal = sealMapper.selectById(cmd.sealId());
            if (seal == null) {
                throw new ServiceException("印章不存在");
            }
            List<EsSealGrant> grants = grantMapper.selectList(
                Wrappers.<EsSealGrant>lambdaQuery().eq(EsSealGrant::getSealId, seal.getSealId()));
            if (!SealAvailabilityResolver.isUsable(seal, grants, userId, deptId, cmd.bizType())) {
                throw new ServiceException("当前用户无权使用印章【{}】", seal.getSealName());
            }
        }

        // 2. 二级认证（失败即拒绝，不降级）
        authVerifier.verify(userId, cmd.authMethod(), cmd.credential());

        // 3. 接链（按租户串行，防分叉）
        LockInfo lock = lockTemplate.lock(CHAIN_LOCK_PREFIX + tenantId, LOCK_EXPIRE, LOCK_ACQUIRE_TIMEOUT);
        if (lock == null) {
            throw new ServiceException("签名通道繁忙，请稍后重试");
        }
        try {
            return appendSignature(cmd, seal, userId, deptId);
        } finally {
            lockTemplate.releaseLock(lock);
        }
    }

    /**
     * 落库并接入哈希链。
     * <p>先取雪花ID再算链哈希，避免"先插入后更新"——签名记录必须一次成型。</p>
     */
    private Long appendSignature(SignCommand cmd, EsSeal seal, Long userId, Long deptId) {
        EsSignature last = signatureMapper.selectOne(
            Wrappers.<EsSignature>lambdaQuery()
                .orderByDesc(EsSignature::getSignedAt)
                .orderByDesc(EsSignature::getSignatureId)
                .last("LIMIT 1"));
        String prevHash = last == null ? null : last.getChainHash();

        Date signedAt = new Date();
        Long signatureId = IdWorker.getId();

        EsSignature entity = new EsSignature();
        entity.setSignatureId(signatureId);
        entity.setBizType(cmd.bizType());
        entity.setBizId(cmd.bizId());
        entity.setBizNo(cmd.bizNo());
        entity.setSignerUserId(userId);
        entity.setSignerName(resolveSignerName());
        entity.setSignerDeptId(deptId);
        entity.setSealId(seal == null ? null : seal.getSealId());
        entity.setSealType(seal == null ? null : seal.getSealType());
        entity.setSignatureMeaning(cmd.signatureMeaning());
        entity.setAuthMethod(cmd.authMethod());
        entity.setSignedAt(signedAt);
        entity.setSignIp(com.nexus.common.core.utils.ServletUtils.getClientIP());
        entity.setSignUa(ServletUtils.getRequest() == null ? null
            : ServletUtils.getRequest().getHeader("User-Agent"));
        entity.setPrevHash(prevHash);
        entity.setChainHash(SignatureChainCalculator.compute(
            prevHash, signatureId, cmd.bizType(), cmd.bizId(), userId,
            entity.getSealId(), cmd.signatureMeaning(), entity.getDocHash(), signedAt.getTime()));

        signatureMapper.insert(entity);
        log.info("电子签名完成: signatureId={}, bizType={}, bizId={}, signer={}, sealId={}, method={}",
            signatureId, cmd.bizType(), cmd.bizId(), userId, entity.getSealId(), cmd.authMethod());
        return signatureId;
    }

    private String resolveSignerName() {
        return LoginHelper.getLoginUser() == null ? String.valueOf(LoginHelper.getUserId())
            : LoginHelper.getLoginUser().getNickname();
    }

    private void validate(SignCommand cmd) {
        if (cmd == null) {
            throw new ServiceException("签名入参不能为空");
        }
        if (StringUtils.isBlank(cmd.bizType())) {
            throw new ServiceException("业务类型不能为空");
        }
        if (StringUtils.isBlank(cmd.signatureMeaning())) {
            throw new ServiceException("签署含义不能为空");
        }
        if (StringUtils.isBlank(cmd.authMethod())) {
            throw new ServiceException("签署认证方式不能为空");
        }
        if (cmd.signatureMeaning().length() > 128) {
            throw new ServiceException("签署含义长度不能超过 128 字符");
        }
    }

    @Override
    public VerifyResultVo verify(Long signatureId) {
        // Task 10 实现
        throw new UnsupportedOperationException("Task 10");
    }

    @Override
    public VerifyResultVo verifyChain(String bizType, Long bizId) {
        // Task 10 实现
        throw new UnsupportedOperationException("Task 10");
    }

    @Override
    public List<SignatureVo> listByBiz(String bizType, Long bizId) {
        // Task 10 实现
        throw new UnsupportedOperationException("Task 10");
    }
}
```

> **实施提示**：`LoginHelper.getLoginUser().getNickname()`、`ServletUtils.getClientIP()`、`ServletUtils.getRequest()`
> 均为本仓库既有 API；实施前用 `grep -n "getClientIP\|getLoginUser"` 核对确切方法名（`LoginUser` 的昵称 getter
> 可能是 `getNickname()` 或 `getNickName()`）。

- [ ] **Step 5: 运行测试确认通过**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=ESignatureServiceImplTest`
Expected: Tests run: 12, Failures: 0, Errors: 0

- [ ] **Step 6: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src
git commit -m "feat(sign): 签名核心 sign() 与 SM3 哈希链接链"
```

---

## Task 10: 验签与签名查询（TDD）

**Files:**
- Modify: `ruoyi-common-sign/.../service/impl/ESignatureServiceImpl.java`（替换 Task 9 的三个 `UnsupportedOperationException`）
- Test: `ruoyi-common-sign/src/test/java/com/nexus/common/sign/service/impl/ESignatureVerifyTest.java`

- [ ] **Step 1: 编写失败测试 —— 验签**

`ESignatureVerifyTest.java`：

```java
package com.nexus.common.sign.service.impl;

import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import com.baomidou.lock.LockTemplate;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * 验签测试。
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ESignatureVerifyTest {

    @Mock private EsSignatureMapper signatureMapper;
    @Mock private EsSealMapper sealMapper;
    @Mock private EsSealGrantMapper grantMapper;
    @Mock private SignAuthVerifier authVerifier;
    @Mock private LockTemplate lockTemplate;

    private ESignatureServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        service = new ESignatureServiceImpl(signatureMapper, sealMapper, grantMapper, authVerifier, lockTemplate);
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SIGNATUREID", "BIZTYPE", "BIZID", "SIGNERUSERID", "SEALID",
            "SIGNATUREMEANING", "AUTHMETHOD", "DOCHASH", "SIGNEDAT", "PREVHASH", "CHAINHASH"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSignature.class)).thenReturn(cols);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
    }

    /** 构造一条自洽的记录（chainHash 已按算法算好） */
    private EsSignature consistent(Long id, String prevHash, String meaning) {
        EsSignature s = new EsSignature();
        s.setSignatureId(id);
        s.setBizType(SignBizType.APPROVAL);
        s.setBizId(100L);
        s.setSignerUserId(7L);
        s.setSealId(null);
        s.setSignatureMeaning(meaning);
        s.setAuthMethod(SignAuthMethod.LOGIN_ONLY);
        s.setSignedAt(new Date(1_755_000_000_000L + id));
        s.setPrevHash(prevHash);
        s.setChainHash(SignatureChainCalculator.compute(
            prevHash, id, s.getBizType(), s.getBizId(), s.getSignerUserId(),
            s.getSealId(), meaning, s.getDocHash(), s.getSignedAt().getTime()));
        return s;
    }

    @Test
    @DisplayName("verify: 未被篡改的记录 -> 通过")
    void verify_intact_passes() {
        EsSignature s = consistent(1L, null, "审批通过");
        when(signatureMapper.selectById(1L)).thenReturn(s);

        VerifyResultVo r = service.verify(1L);

        assertTrue(r.getPassed());
        assertTrue(r.getChainPassed());
        assertNull(r.getBrokenAt());
        assertEquals(1, r.getCheckedCount());
    }

    @Test
    @DisplayName("verify: 签署含义被篡改 -> 检出失败并定位")
    void verify_tamperedMeaning_detected() {
        EsSignature s = consistent(1L, null, "审批通过");
        s.setSignatureMeaning("审批驳回"); // 篡改，chainHash 未重算
        when(signatureMapper.selectById(1L)).thenReturn(s);

        VerifyResultVo r = service.verify(1L);

        assertFalse(r.getPassed());
        assertEquals(1L, r.getBrokenAt());
        assertTrue(r.getMessages().stream().anyMatch(m -> m.contains("篡改") || m.contains("不一致")));
    }

    @Test
    @DisplayName("verify: 签署时间被篡改 -> 检出失败")
    void verify_tamperedTime_detected() {
        EsSignature s = consistent(1L, null, "审批通过");
        s.setSignedAt(new Date(s.getSignedAt().getTime() + 60_000L));
        when(signatureMapper.selectById(1L)).thenReturn(s);

        assertFalse(service.verify(1L).getPassed());
    }

    @Test
    @DisplayName("verify: prevHash 与上一条 chainHash 不匹配 -> 检出断链")
    void verify_brokenLink_detected() {
        EsSignature prev = consistent(1L, null, "审批通过");
        // 本条 prevHash 乱填，但自身 chainHash 自洽 -> 自身通过、链接失败
        EsSignature cur = consistent(2L, "b".repeat(64), "复核通过");
        when(signatureMapper.selectById(2L)).thenReturn(cur);
        when(signatureMapper.selectOne(any())).thenReturn(prev);
        when(signatureMapper.selectById(1L)).thenReturn(prev);

        VerifyResultVo r = service.verify(2L);

        assertFalse(r.getPassed());
        assertEquals(2L, r.getBrokenAt());
    }

    @Test
    @DisplayName("verify: 记录不存在 -> 抛 ServiceException")
    void verify_notFound_throws() {
        when(signatureMapper.selectById(999L)).thenReturn(null);
        assertThrows(com.nexus.common.core.exception.ServiceException.class,
            () -> service.verify(999L));
    }

    @Test
    @DisplayName("verifyChain: 三条自洽记录 -> 全通过")
    void verifyChain_allConsistent_passes() {
        EsSignature a = consistent(1L, null, "拟稿");
        EsSignature b = consistent(2L, a.getChainHash(), "复核");
        EsSignature c = consistent(3L, b.getChainHash(), "批准");
        when(signatureMapper.selectList(any())).thenReturn(List.of(a, b, c));

        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);

        assertTrue(r.getPassed());
        assertEquals(3, r.getCheckedCount());
        assertNull(r.getBrokenAt());
    }

    @Test
    @DisplayName("verifyChain: 中间一条被篡改 -> 在该条断链")
    void verifyChain_middleTampered_breaksThere() {
        EsSignature a = consistent(1L, null, "拟稿");
        EsSignature b = consistent(2L, a.getChainHash(), "复核");
        EsSignature c = consistent(3L, b.getChainHash(), "批准");
        b.setSignatureMeaning("被改了"); // 篡改 b
        when(signatureMapper.selectList(any())).thenReturn(List.of(a, b, c));

        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);

        assertFalse(r.getPassed());
        assertEquals(2L, r.getBrokenAt());
    }

    @Test
    @DisplayName("verifyChain: 无记录 -> 通过且条数为 0")
    void verifyChain_noRecords_passes() {
        when(signatureMapper.selectList(any())).thenReturn(List.of());
        VerifyResultVo r = service.verifyChain(SignBizType.APPROVAL, 100L);
        assertTrue(r.getPassed());
        assertEquals(0, r.getCheckedCount());
    }
}
```

- [ ] **Step 2: 运行测试确认失败**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest=ESignatureVerifyTest`
Expected: FAIL —— `UnsupportedOperationException: Task 10`

- [ ] **Step 3: 实现 verify / verifyChain / listByBiz**

在 `ESignatureServiceImpl` 中，把 Task 9 留下的三个方法替换为：

```java
    @Override
    public VerifyResultVo verify(Long signatureId) {
        EsSignature cur = signatureMapper.selectById(signatureId);
        if (cur == null) {
            throw new ServiceException("签名记录不存在");
        }
        VerifyResultVo result = new VerifyResultVo();
        result.setCheckedCount(1);

        // 1. 自身字段与 chainHash 是否自洽
        String recomputed = SignatureChainCalculator.compute(
            cur.getPrevHash(), cur.getSignatureId(), cur.getBizType(), cur.getBizId(),
            cur.getSignerUserId(), cur.getSealId(), cur.getSignatureMeaning(),
            cur.getDocHash(), cur.getSignedAt().getTime());
        if (!recomputed.equals(cur.getChainHash())) {
            result.setPassed(false);
            result.setChainPassed(false);
            result.setBrokenAt(cur.getSignatureId());
            result.getMessages().add("签名记录内容与链哈希不一致，疑似被篡改");
            return result;
        }

        // 2. 与上一条的链接
        EsSignature prev = signatureMapper.selectOne(
            Wrappers.<EsSignature>lambdaQuery()
                .lt(EsSignature::getSignatureId, cur.getSignatureId())
                .orderByDesc(EsSignature::getSignatureId)
                .last("LIMIT 1"));
        String expectedPrev = prev == null ? null : prev.getChainHash();
        if (!java.util.Objects.equals(expectedPrev, cur.getPrevHash())) {
            result.setPassed(false);
            result.setChainPassed(false);
            result.setBrokenAt(cur.getSignatureId());
            result.getMessages().add("与上一条签名的链哈希不衔接，链已断裂");
            return result;
        }

        result.setPassed(true);
        result.setChainPassed(true);
        result.getMessages().add("签名记录完整，链哈希校验通过");
        return result;
    }

    @Override
    public VerifyResultVo verifyChain(String bizType, Long bizId) {
        List<EsSignature> list = signatureMapper.selectList(
            Wrappers.<EsSignature>lambdaQuery()
                .eq(EsSignature::getBizType, bizType)
                .eq(bizId != null, EsSignature::getBizId, bizId)
                .orderByAsc(EsSignature::getSignatureId));

        VerifyResultVo result = new VerifyResultVo();
        result.setCheckedCount(list.size());
        String expectedPrev = null;
        for (EsSignature cur : list) {
            String recomputed = SignatureChainCalculator.compute(
                cur.getPrevHash(), cur.getSignatureId(), cur.getBizType(), cur.getBizId(),
                cur.getSignerUserId(), cur.getSealId(), cur.getSignatureMeaning(),
                cur.getDocHash(), cur.getSignedAt().getTime());
            if (!recomputed.equals(cur.getChainHash())
                || !java.util.Objects.equals(expectedPrev, cur.getPrevHash())) {
                result.setPassed(false);
                result.setChainPassed(false);
                result.setBrokenAt(cur.getSignatureId());
                result.getMessages().add("链校验在 signatureId=" + cur.getSignatureId() + " 处断裂");
                return result;
            }
            expectedPrev = cur.getChainHash();
        }
        result.setPassed(true);
        result.setChainPassed(true);
        result.getMessages().add("共校验 " + list.size() + " 条签名记录，链完整");
        return result;
    }

    @Override
    public List<SignatureVo> listByBiz(String bizType, Long bizId) {
        return signatureMapper.selectVoList(
            Wrappers.<EsSignature>lambdaQuery()
                .eq(EsSignature::getBizType, bizType)
                .eq(bizId != null, EsSignature::getBizId, bizId)
                .orderByAsc(EsSignature::getSignedAt));
    }
```

- [ ] **Step 4: 运行测试确认通过**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign test -DskipTests=false -Dtest='ESignatureServiceImplTest,ESignatureVerifyTest'`
Expected: Tests run: 20, Failures: 0, Errors: 0

- [ ] **Step 5: 全模块测试回归**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am test -DskipTests=false`
Expected: 全部 PASS（累计约 41 个）

- [ ] **Step 6: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src
git commit -m "feat(sign): 验签（单条 + 全链）与签名查询"
```

---

## Task 11: 控制器与权限

**Files:**
- Create: `ruoyi-common-sign/.../controller/EsSealController.java`
- Create: `ruoyi-common-sign/.../controller/EsSealGrantController.java`
- Create: `ruoyi-common-sign/.../controller/EsSignatureController.java`

- [ ] **Step 1: 印章控制器**

`controller/EsSealController.java`：

```java
package com.nexus.common.sign.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.domain.bo.SealBo;
import com.nexus.common.sign.domain.bo.SealQueryBo;
import com.nexus.common.sign.domain.vo.AvailableSealVo;
import com.nexus.common.sign.domain.vo.SealVo;
import com.nexus.common.sign.service.IESSealService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 电子印章接口。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/seal")
public class EsSealController {

    private final IESSealService sealService;

    /** 分页查询印章 */
    @SaCheckPermission("system:sign:seal:list")
    @GetMapping("/list")
    public TableDataInfo<SealVo> list(SealQueryBo bo, PageQuery pageQuery) {
        return sealService.selectPageList(bo, pageQuery);
    }

    /** 查询印章详情 */
    @SaCheckPermission("system:sign:seal:query")
    @GetMapping("/{sealId}")
    public R<SealVo> getInfo(@PathVariable Long sealId) {
        return R.ok(sealService.selectById(sealId));
    }

    /** 查询当前用户在某业务场景可用的印章（供签署弹窗调用，无菜单权限要求） */
    @GetMapping("/available")
    public R<List<AvailableSealVo>> available(@RequestParam String bizType) {
        return R.ok(sealService.listAvailableSeals(bizType));
    }

    /** 新增印章 */
    @SaCheckPermission("system:sign:seal:add")
    @Log(title = "电子印章", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SealBo bo) {
        sealService.insertByBo(bo);
        return R.ok();
    }

    /** 修改印章 */
    @SaCheckPermission("system:sign:seal:edit")
    @Log(title = "电子印章", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SealBo bo) {
        return com.nexus.common.core.utils.StringUtils.toAjax(sealService.updateByBo(bo));
    }

    /** 删除印章 */
    @SaCheckPermission("system:sign:seal:remove")
    @Log(title = "电子印章", businessType = BusinessType.DELETE)
    @DeleteMapping("/{sealIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] sealIds) {
        return com.nexus.common.core.utils.StringUtils.toAjax(
            sealService.deleteByIds(java.util.Arrays.asList(sealIds)));
    }

    /** 启停/销毁印章 */
    @SaCheckPermission("system:sign:seal:status")
    @Log(title = "电子印章", businessType = BusinessType.UPDATE)
    @PutMapping("/{sealId}/status/{status}")
    public R<Void> changeStatus(@PathVariable Long sealId,
                               @NotNull(message = "状态不能为空") @PathVariable String status) {
        return com.nexus.common.core.utils.StringUtils.toAjax(sealService.changeStatus(sealId, status));
    }

    /**
     * 印章预览图直出。
     * <p><b>仅返回低清+水印预览图</b>；印章原图无任何对外接口。</p>
     */
    @SaCheckPermission("system:sign:seal:query")
    @GetMapping(value = "/{sealId}/preview", produces = MediaType.IMAGE_PNG_VALUE)
    public void preview(@PathVariable Long sealId, HttpServletResponse response) {
        byte[] bytes = sealService.loadPreviewImage(sealId);
        response.setContentType(MediaType.IMAGE_PNG_VALUE);
        response.setHeader("Cache-Control", "no-store");
        try {
            response.getOutputStream().write(bytes);
            response.getOutputStream().flush();
        } catch (java.io.IOException e) {
            throw new com.nexus.common.core.exception.ServiceException("印章预览图读取失败");
        }
    }
}
```

> **实施提示**：`@SaCheckPermission` 的导入为 `cn.dev33.satoken.annotation.SaCheckPermission`。
> `StringUtils.toAjax(int)` 为 RuoYi 风格工具方法，以仓库实际实现为准（也可直接 `return R.ok()`）。
> 实施前 `grep -rn "toAjax" ruoyi-common/ | head` 核对。

- [ ] **Step 2: 授权控制器**

`controller/EsSealGrantController.java`：

```java
package com.nexus.common.sign.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.idempotent.annotation.RepeatSubmit;
import com.nexus.common.log.annotation.Log;
import com.nexus.common.log.enums.BusinessType;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.common.sign.domain.bo.SealGrantBo;
import com.nexus.common.sign.domain.vo.SealGrantVo;
import com.nexus.common.sign.service.IESSealGrantService;
import jakarta.validation.constraints.NotEmpty;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

/**
 * 用章授权接口。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/grant")
public class EsSealGrantController {

    private final IESSealGrantService grantService;

    @SaCheckPermission("system:sign:grant:list")
    @GetMapping("/list")
    public TableDataInfo<SealGrantVo> list(SealGrantBo bo, PageQuery pageQuery) {
        return grantService.selectPageList(bo, pageQuery);
    }

    /** 按印章查授权列表 */
    @SaCheckPermission("system:sign:grant:query")
    @GetMapping("/by-seal/{sealId}")
    public R<List<SealGrantVo>> bySeal(@PathVariable Long sealId) {
        return R.ok(grantService.selectListBySeal(sealId));
    }

    @SaCheckPermission("system:sign:grant:add")
    @Log(title = "用章授权", businessType = BusinessType.INSERT)
    @RepeatSubmit()
    @PostMapping
    public R<Void> add(@Validated @RequestBody SealGrantBo bo) {
        grantService.insertByBo(bo);
        return R.ok();
    }

    @SaCheckPermission("system:sign:grant:edit")
    @Log(title = "用章授权", businessType = BusinessType.UPDATE)
    @RepeatSubmit()
    @PutMapping
    public R<Void> edit(@Validated @RequestBody SealGrantBo bo) {
        grantService.updateByBo(bo);
        return R.ok();
    }

    @SaCheckPermission("system:sign:grant:remove")
    @Log(title = "用章授权", businessType = BusinessType.DELETE)
    @DeleteMapping("/{grantIds}")
    public R<Void> remove(@NotEmpty(message = "主键不能为空") @PathVariable Long[] grantIds) {
        grantService.deleteByIds(Arrays.asList(grantIds));
        return R.ok();
    }
}
```

- [ ] **Step 3: 签名记录控制器（含短信验证码）**

`controller/EsSignatureController.java`：

```java
package com.nexus.common.sign.controller;

import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.RandomUtils;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.domain.vo.SignatureVo;
import com.nexus.common.sign.domain.vo.VerifyResultVo;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.support.SmsSignCredentialChecker;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 签名记录接口。
 *
 * @author nexus
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/system/sign/record")
public class EsSignatureController {

    private final ESignatureService signatureService;

    /** 按业务查询签名记录 */
    @SaCheckPermission("system:sign:record:list")
    @GetMapping("/list")
    public R<List<SignatureVo>> list(@RequestParam String bizType,
                                     @RequestParam(required = false) Long bizId) {
        return R.ok(signatureService.listByBiz(bizType, bizId));
    }

    /** 单条验签 */
    @SaCheckPermission("system:sign:record:verify")
    @GetMapping("/{signatureId}/verify")
    public R<VerifyResultVo> verify(@PathVariable Long signatureId) {
        return R.ok(signatureService.verify(signatureId));
    }

    /** 全链验签 */
    @SaCheckPermission("system:sign:record:verify")
    @GetMapping("/verify-chain")
    public R<VerifyResultVo> verifyChain(@RequestParam String bizType,
                                         @RequestParam(required = false) Long bizId) {
        return R.ok(signatureService.verifyChain(bizType, bizId));
    }

    /**
     * 发送签署短信验证码。
     * <p>验证码只写入 Redis 并下发短信，<b>不出现在响应体中</b>。</p>
     */
    @PostMapping("/sms-code")
    public R<Void> sendSmsCode() {
        Long userId = LoginHelper.getUserId();
        String phone = LoginHelper.getLoginUser() == null ? null
            : LoginHelper.getLoginUser().getPhonenumber();
        if (phone == null || phone.isBlank()) {
            throw new ServiceException("当前账号未绑定手机号，无法使用短信验证");
        }
        String code = RandomUtils.randomString(6, "0123456789");
        RedisUtils.setCacheObject(SmsSignCredentialChecker.CODE_KEY_PREFIX + userId, code,
            SmsSignCredentialChecker.CODE_TTL);
        // TODO(实施): 调用 ruoyi-common-sms 的短信发送服务下发 code；渠道未配置时抛可读异常
        throw new ServiceException("短信渠道未配置，请使用密码验证或联系管理员");
    }
}
```

> **实施提示**：短信发送需接入 `ruoyi-common-sms`（`SmsUtils` 或等价服务）。
> **若短信渠道未配置，P1 可先只保留 PASSWORD + LOGIN_ONLY**：删除 `/sms-code` 接口与
> `SmsSignCredentialChecker`，并在 `SignAuthMethod.SUPPORTED` 中移除 `SMS`。
> 这是可接受的降级 —— 密码二次录入已满足内部留痕场景。

- [ ] **Step 4: 编译验证**

Run: `mvn -q -pl ruoyi-common/ruoyi-common-sign -am compile -DskipTests`
Expected: BUILD SUCCESS

- [ ] **Step 5: 启动冒烟验证**

Run: `mvn spring-boot:run -P local`（`ruoyi-admin`）
Expected: 应用启动无 `No qualifying bean of type 'EsSealMapper'` 报错。
若报错，在 `SignAutoConfiguration` 上加 `@org.mybatis.spring.annotation.MapperScan("com.nexus.common.sign.mapper")` 后重启。

- [ ] **Step 6: 提交**

```bash
git add ruoyi-common/ruoyi-common-sign/src
git commit -m "feat(sign): 印章/授权/签名记录接口"
```

---

## Task 12: 前端 API 层与印章管理页

**Files:**
- Create: `plus-ui/src/api/system/sign/seal.ts`
- Create: `plus-ui/src/api/system/sign/grant.ts`
- Create: `plus-ui/src/api/system/sign/record.ts`
- Create: `plus-ui/src/views/system/sign/seal/index.vue`

- [ ] **Step 1: 创建 API 层**

`plus-ui/src/api/system/sign/seal.ts`：

```ts
import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

export interface SealVO {
  sealId?: string | number;
  sealType: string;
  sealName: string;
  ownerUserId?: string | number;
  previewOssId?: string | number;
  imgWidth: number;
  imgHeight: number;
  status?: string;
  sealVersion?: number;
  validFrom?: string;
  validTo?: string;
  remark?: string;
  createTime?: string;
}

export interface SealForm {
  sealId?: string | number;
  sealType: string;
  sealName: string;
  ownerUserId?: string | number;
  sealOssId: string | number;
  imgWidth: number;
  imgHeight: number;
  validFrom?: string;
  validTo?: string;
  remark?: string;
}

export interface AvailableSealVO {
  sealId: string | number;
  sealType: string;
  sealName: string;
  previewOssId?: string | number;
  imgWidth?: number;
  imgHeight?: number;
}

/** 查询印章列表 */
export const listSeal = (query?: Partial<SealVO> & PageQuery): AxiosPromise<TableDataInfo<SealVO>> => {
  return request({ url: '/system/sign/seal/list', method: 'get', params: query });
};

/** 查询印章详情 */
export const getSeal = (sealId: string | number): AxiosPromise<SealVO> => {
  return request({ url: `/system/sign/seal/${sealId}`, method: 'get' });
};

/** 查询当前用户可用印章 */
export const listAvailableSeal = (bizType: string): AxiosPromise<AvailableSealVO[]> => {
  return request({ url: '/system/sign/seal/available', method: 'get', params: { bizType } });
};

/** 新增印章 */
export const addSeal = (data: SealForm) => {
  return request({ url: '/system/sign/seal', method: 'post', data });
};

/** 修改印章 */
export const updateSeal = (data: SealForm) => {
  return request({ url: '/system/sign/seal', method: 'put', data });
};

/** 删除印章 */
export const delSeal = (sealIds: string | number | (string | number)[]) => {
  return request({ url: `/system/sign/seal/${sealIds}`, method: 'delete' });
};

/** 变更印章状态 */
export const changeSealStatus = (sealId: string | number, status: string) => {
  return request({ url: `/system/sign/seal/${sealId}/status/${status}`, method: 'put' });
};

/** 印章预览图地址（后端直出，带鉴权头由 request 拦截器处理时改用 blob 下载） */
export const sealPreviewUrl = (sealId: string | number) => `/system/sign/seal/${sealId}/preview`;
```

> **实施提示**：`plus-ui` 使用全局类型 `PageQuery` / `TableDataInfo`（见 `src/types/global.d.ts` 或
> 其它 API 文件的既有用法）。若这两个类型不存在，参照同目录既有 API 文件（如 `src/api/system/user.ts`）
> 的 import 与写法调整。

`plus-ui/src/api/system/sign/grant.ts`：

```ts
import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

export interface GrantVO {
  grantId?: string | number;
  sealId: string | number;
  sealName?: string;
  granteeUserId?: string | number;
  granteeUserName?: string;
  granteeDeptId?: string | number;
  granteeDeptName?: string;
  bizType?: string;
  canSignAlone?: string;
  validFrom?: string;
  validTo?: string;
  status?: string;
  remark?: string;
}

export const listGrant = (query?: Partial<GrantVO> & PageQuery): AxiosPromise<TableDataInfo<GrantVO>> => {
  return request({ url: '/system/sign/grant/list', method: 'get', params: query });
};

export const listGrantBySeal = (sealId: string | number): AxiosPromise<GrantVO[]> => {
  return request({ url: `/system/sign/grant/by-seal/${sealId}`, method: 'get' });
};

export const addGrant = (data: GrantVO) => {
  return request({ url: '/system/sign/grant', method: 'post', data });
};

export const updateGrant = (data: GrantVO) => {
  return request({ url: '/system/sign/grant', method: 'put', data });
};

export const delGrant = (grantIds: string | number | (string | number)[]) => {
  return request({ url: `/system/sign/grant/${grantIds}`, method: 'delete' });
};
```

`plus-ui/src/api/system/sign/record.ts`：

```ts
import request from '@/utils/request';
import type { AxiosPromise } from 'axios';

export interface SignatureVO {
  signatureId: string | number;
  bizType: string;
  bizId?: string | number;
  bizNo?: string;
  signerUserId: string | number;
  signerName: string;
  sealId?: string | number;
  sealType?: string;
  signatureMeaning: string;
  authMethod: string;
  signedAt: string;
  signIp?: string;
  signUa?: string;
  chainHash: string;
  prevHash?: string;
}

export interface VerifyResultVO {
  passed: boolean;
  chainPassed: boolean;
  brokenAt?: string | number;
  checkedCount: number;
  messages: string[];
}

export const listSignature = (bizType: string, bizId?: string | number): AxiosPromise<SignatureVO[]> => {
  return request({ url: '/system/sign/record/list', method: 'get', params: { bizType, bizId } });
};

export const verifySignature = (signatureId: string | number): AxiosPromise<VerifyResultVO> => {
  return request({ url: `/system/sign/record/${signatureId}/verify`, method: 'get' });
};

export const verifySignatureChain = (bizType: string, bizId?: string | number): AxiosPromise<VerifyResultVO> => {
  return request({ url: '/system/sign/record/verify-chain', method: 'get', params: { bizType, bizId } });
};

export const sendSignSmsCode = () => {
  return request({ url: '/system/sign/record/sms-code', method: 'post' });
};
```

- [ ] **Step 2: 创建印章管理页**

`plus-ui/src/views/system/sign/seal/index.vue`：

```vue
<template>
  <div class="p-2">
    <el-card shadow="never">
      <el-form :model="queryParams" ref="queryRef" :inline="true" v-show="showSearch">
        <el-form-item label="印章名称" prop="sealName">
          <el-input v-model="queryParams.sealName" placeholder="请输入印章名称" clearable style="width: 200px" @keyup.enter="handleQuery" />
        </el-form-item>
        <el-form-item label="印章类型" prop="sealType">
          <el-select v-model="queryParams.sealType" placeholder="全部" clearable style="width: 160px">
            <el-option label="个人章" value="1" />
            <el-option label="公司章" value="2" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="queryParams.status" placeholder="全部" clearable style="width: 160px">
            <el-option label="草稿" value="0" />
            <el-option label="启用" value="1" />
            <el-option label="停用" value="2" />
            <el-option label="销毁" value="3" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="handleQuery">搜索</el-button>
          <el-button icon="Refresh" @click="resetQuery">重置</el-button>
        </el-form-item>
      </el-form>

      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['system:sign:seal:add']">新增</el-button>
        </el-col>
        <right-toolbar v-model:showSearch="showSearch" @queryTable="getList" />
      </el-row>

      <el-table v-loading="loading" :data="sealList">
        <el-table-column label="印章名称" prop="sealName" show-overflow-tooltip />
        <el-table-column label="类型" prop="sealType" width="100">
          <template #default="scope">
            <el-tag :type="scope.row.sealType === '2' ? 'danger' : 'info'">
              {{ scope.row.sealType === '2' ? '公司章' : '个人章' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预览" width="120">
          <template #default="scope">
            <el-image
              v-if="scope.row.previewOssId"
              :src="previewSrc(scope.row.sealId)"
              :preview-src-list="[previewSrc(scope.row.sealId)]"
              fit="contain"
              style="width: 60px; height: 60px"
              preview-teleported
            />
            <span v-else class="text-gray-400">无</span>
          </template>
        </el-table-column>
        <el-table-column label="版本" prop="sealVersion" width="80" align="center" />
        <el-table-column label="状态" prop="status" width="100">
          <template #default="scope">
            <el-tag :type="statusTag(scope.row.status)">{{ statusText(scope.row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="生效日期" prop="validFrom" width="120" />
        <el-table-column label="失效日期" prop="validTo" width="120" />
        <el-table-column label="备注" prop="remark" show-overflow-tooltip />
        <el-table-column label="操作" width="240" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:sign:seal:edit']" :disabled="scope.row.status === '3'">修改</el-button>
            <el-button link type="primary" icon="Switch" @click="handleStatus(scope.row)" v-hasPermi="['system:sign:seal:status']" :disabled="scope.row.status === '3'">
              {{ scope.row.status === '1' ? '停用' : '启用' }}
            </el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:sign:seal:remove']" :disabled="scope.row.status === '1'">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="title" v-model="open" width="640px" append-to-body>
      <el-form ref="sealRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="印章类型" prop="sealType">
          <el-radio-group v-model="form.sealType" :disabled="!!form.sealId">
            <el-radio value="1">个人章</el-radio>
            <el-radio value="2">公司章</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="印章名称" prop="sealName">
          <el-input v-model="form.sealName" placeholder="请输入印章名称" maxlength="64" />
        </el-form-item>
        <el-form-item v-if="form.sealType === '1'" label="归属用户" prop="ownerUserId">
          <el-select v-model="form.ownerUserId" placeholder="请选择用户" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.userId" :label="u.nickName" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item label="印章图片" prop="sealOssId">
          <image-upload v-model="form.sealOssId" :limit="1" :file-size="2" :is-show-tip="true" />
          <div class="text-gray-400 text-xs mt-1">
            请上传 PNG（透明底，建议 400×400）。印章原图仅服务端使用，前端始终只展示低清水印预览图。
          </div>
        </el-form-item>
        <el-form-item label="图片尺寸" prop="imgWidth">
          <el-input-number v-model="form.imgWidth" :min="1" :max="4000" controls-position="right" />
          <span class="mx-2">×</span>
          <el-input-number v-model="form.imgHeight" :min="1" :max="4000" controls-position="right" />
          <span class="ml-2 text-gray-400">px</span>
        </el-form-item>
        <el-form-item label="生效日期" prop="validFrom">
          <el-date-picker v-model="form.validFrom" type="date" value-format="YYYY-MM-DD" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效日期" prop="validTo">
          <el-date-picker v-model="form.validTo" type="date" value-format="YYYY-MM-DD" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" :loading="submitLoading" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SignSeal" lang="ts">
import { addSeal, changeSealStatus, delSeal, listSeal, updateSeal, type SealForm, type SealVO } from '@/api/system/sign/seal';
import { listUser } from '@/api/system/user';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const submitLoading = ref(false);
const showSearch = ref(true);
const sealList = ref<SealVO[]>([]);
const userOptions = ref<any[]>([]);
const total = ref(0);
const title = ref('');
const open = ref(false);

const queryParams = reactive({ pageNum: 1, pageSize: 10, sealName: '', sealType: '', status: '' });

const initForm: SealForm = {
  sealId: undefined,
  sealType: '1',
  sealName: '',
  ownerUserId: undefined,
  sealOssId: undefined as any,
  imgWidth: 400,
  imgHeight: 400,
  validFrom: undefined,
  validTo: undefined,
  remark: ''
};
const form = ref<SealForm>({ ...initForm });

const rules = {
  sealType: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
  sealName: [{ required: true, message: '请输入印章名称', trigger: 'blur' }],
  ownerUserId: [{ required: true, message: '请选择归属用户', trigger: 'change' }],
  sealOssId: [{ required: true, message: '请上传印章图片', trigger: 'change' }],
  imgWidth: [{ required: true, message: '请输入图片宽度', trigger: 'blur' }],
  imgHeight: [{ required: true, message: '请输入图片高度', trigger: 'blur' }]
};

const statusText = (s?: string) => ({ '0': '草稿', '1': '启用', '2': '停用', '3': '销毁' }[s ?? ''] ?? '-');
const statusTag = (s?: string) => ({ '0': 'info', '1': 'success', '2': 'warning', '3': 'danger' }[s ?? ''] ?? 'info') as any;

/** 预览图需带鉴权头，用 blob 方式加载并缓存 objectURL */
const previewCache = new Map<string, string>();
const previewSrc = (sealId: string | number) => {
  const key = String(sealId);
  if (!previewCache.has(key)) {
    previewCache.set(key, `${import.meta.env.VITE_APP_BASE_API}/system/sign/seal/${key}/preview`);
  }
  return previewCache.get(key)!;
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listSeal(queryParams);
    sealList.value = res.data.rows;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const loadUsers = async () => {
  if (userOptions.value.length) return;
  const res = await listUser({ pageNum: 1, pageSize: 200 } as any);
  userOptions.value = res.data.rows ?? [];
};

const handleQuery = () => {
  queryParams.pageNum = 1;
  getList();
};

const resetQuery = () => {
  queryParams.sealName = '';
  queryParams.sealType = '';
  queryParams.status = '';
  handleQuery();
};

const reset = () => {
  form.value = { ...initForm };
  proxy?.$refs.sealRef && (proxy.$refs.sealRef as any).resetFields();
};

const cancel = () => {
  open.value = false;
  reset();
};

const handleAdd = async () => {
  reset();
  await loadUsers();
  title.value = '新增印章';
  open.value = true;
};

const handleUpdate = async (row: SealVO) => {
  reset();
  await loadUsers();
  const res = await getSealDetail(row.sealId!);
  Object.assign(form.value, res);
  title.value = '修改印章';
  open.value = true;
};

// 详情需回填 sealOssId（原图 ID 仅用于编辑回显，不用于展示）
const getSealDetail = async (sealId: string | number) => {
  const { getSeal } = await import('@/api/system/sign/seal');
  const res = await getSeal(sealId);
  return { ...res.data, sealOssId: (res.data as any).sealOssId };
};

const submitForm = () => {
  (proxy?.$refs.sealRef as any)?.validate(async (valid: boolean) => {
    if (!valid) return;
    submitLoading.value = true;
    try {
      if (form.value.sealId) {
        await updateSeal(form.value);
      } else {
        await addSeal(form.value);
      }
      proxy?.$modal.msgSuccess('操作成功');
      open.value = false;
      await getList();
    } finally {
      submitLoading.value = false;
    }
  });
};

const handleStatus = async (row: SealVO) => {
  const next = row.status === '1' ? '2' : '1';
  const action = next === '1' ? '启用' : '停用';
  await proxy?.$modal.confirm(`确认${action}印章「${row.sealName}」？`);
  await changeSealStatus(row.sealId!, next);
  proxy?.$modal.msgSuccess(`${action}成功`);
  await getList();
};

const handleDelete = async (row: SealVO) => {
  await proxy?.$modal.confirm(`是否确认删除印章「${row.sealName}」？`);
  await delSeal(row.sealId!);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(getList);
</script>
```

> **实施提示**：
> 1. `image-upload`、`right-toolbar`、`pagination` 均为本仓库全局组件（见既有页面用法）。
> 2. **`sealOssId` 的编辑回显**：`SealVo` **有意不含** `sealOssId`（原图不下发）。因此修改时
>    `image-upload` 无法回显原图。两种处理方式，实施时二选一并在提交信息中说明：
>    - **推荐**：修改时不回显图片，提示"不选新图则保留原图"，后端 `updateByBo` 中 `sealOssId`
>      为 `null` 时保持原值（需在 `EsSealServiceImpl.updateByBo` 补该判断）。
>    - 次选：新增 `GET /system/sign/seal/{id}/edit-detail` 接口返回 `sealOssId`，并加
>      `system:sign:seal:edit` 权限校验（仍不下发图片本体，只给 OSS ID 供上传组件回显）。
> 3. 预览图带鉴权：若 `VITE_APP_BASE_API` 直连后端且 token 走请求头，`el-image :src` 无法带
>    header。**若预览不显示，改用 `request` 以 `responseType: 'blob'` 拉取并 `URL.createObjectURL`**。

- [ ] **Step 3: 前端类型检查**

Run: `cd plus-ui && npm run lint:eslint`
Expected: 无 error（warning 可接受）

- [ ] **Step 4: 浏览器验证**

启动 `npm run dev` + 后端，访问「系统管理 → 电子签章 → 印章管理」：
- [ ] 列表可加载
- [ ] 新增个人章（上传 PNG + 指定归属用户）成功
- [ ] 新增公司章（归属用户字段隐藏）成功
- [ ] 启停按钮生效
- [ ] 启用状态的印章无法删除（提示先停用）

- [ ] **Step 5: 提交**

```bash
git add plus-ui/src/api/system/sign plus-ui/src/views/system/sign/seal
git commit -m "feat(sign): 前端印章管理页与 API 层"
```

---

## Task 13: 用章授权页与签名记录页

**Files:**
- Create: `plus-ui/src/views/system/sign/grant/index.vue`
- Create: `plus-ui/src/views/system/sign/record/index.vue`

- [ ] **Step 1: 创建用章授权页**

`plus-ui/src/views/system/sign/grant/index.vue`（结构对齐印章管理页，字段换成授权字段）：

```vue
<template>
  <div class="p-2">
    <el-card shadow="never">
      <el-row :gutter="10" class="mb8">
        <el-col :span="1.5">
          <el-button type="primary" plain icon="Plus" @click="handleAdd" v-hasPermi="['system:sign:grant:add']">新增授权</el-button>
        </el-col>
        <right-toolbar @queryTable="getList" />
      </el-row>

      <el-alert type="info" :closable="false" class="mb2"
        title="仅公司章需要授权；个人章仅归属人本人可使用，无需在此配置。" show-icon />

      <el-table v-loading="loading" :data="grantList">
        <el-table-column label="印章" prop="sealName" show-overflow-tooltip>
          <template #default="scope">{{ scope.row.sealName || scope.row.sealId }}</template>
        </el-table-column>
        <el-table-column label="授权对象" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.granteeUserName">用户：{{ scope.row.granteeUserName }}</span>
            <span v-else-if="scope.row.granteeDeptName">部门：{{ scope.row.granteeDeptName }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="限定场景" prop="bizType" width="150">
          <template #default="scope">{{ scope.row.bizType || '不限' }}</template>
        </el-table-column>
        <el-table-column label="生效时间" prop="validFrom" width="170" />
        <el-table-column label="失效时间" prop="validTo" width="170">
          <template #default="scope">{{ scope.row.validTo || '不限' }}</template>
        </el-table-column>
        <el-table-column label="状态" prop="status" width="90">
          <template #default="scope">
            <el-tag :type="scope.row.status === '1' ? 'success' : 'info'">
              {{ scope.row.status === '1' ? '启用' : '停用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="Edit" @click="handleUpdate(scope.row)" v-hasPermi="['system:sign:grant:edit']">修改</el-button>
            <el-button link type="danger" icon="Delete" @click="handleDelete(scope.row)" v-hasPermi="['system:sign:grant:remove']">删除</el-button>
          </template>
        </el-table-column>
      </el-table>

      <pagination v-show="total > 0" :total="total" v-model:page="queryParams.pageNum" v-model:limit="queryParams.pageSize" @pagination="getList" />
    </el-card>

    <el-dialog :title="title" v-model="open" width="620px" append-to-body>
      <el-form ref="grantRef" :model="form" :rules="rules" label-width="110px">
        <el-form-item label="印章" prop="sealId">
          <el-select v-model="form.sealId" placeholder="请选择公司章" filterable style="width: 100%">
            <el-option v-for="s in companySeals" :key="s.sealId" :label="s.sealName" :value="s.sealId!" />
          </el-select>
        </el-form-item>
        <el-form-item label="授权方式">
          <el-radio-group v-model="grantMode">
            <el-radio value="user">按用户</el-radio>
            <el-radio value="dept">按部门</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="grantMode === 'user'" label="被授权用户" prop="granteeUserId">
          <el-select v-model="form.granteeUserId" placeholder="请选择用户" filterable style="width: 100%">
            <el-option v-for="u in userOptions" :key="u.userId" :label="u.nickName" :value="u.userId" />
          </el-select>
        </el-form-item>
        <el-form-item v-else label="被授权部门" prop="granteeDeptId">
          <el-tree-select v-model="form.granteeDeptId" :data="deptOptions" :props="{ value: 'id', label: 'label', children: 'children' }" value-key="id" check-strictly filterable style="width: 100%" />
        </el-form-item>
        <el-form-item label="限定场景" prop="bizType">
          <el-select v-model="form.bizType" placeholder="不限（留空即可）" clearable style="width: 100%">
            <el-option label="流程审批" value="APPROVAL" />
            <el-option label="销售合同" value="SO_CONTRACT" />
            <el-option label="采购合同" value="PU_CONTRACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="生效时间" prop="validFrom">
          <el-date-picker v-model="form.validFrom" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="失效时间" prop="validTo">
          <el-date-picker v-model="form.validTo" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="不限" style="width: 100%" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-radio-group v-model="form.status">
            <el-radio value="1">启用</el-radio>
            <el-radio value="0">停用</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" maxlength="255" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button type="primary" @click="submitForm">确 定</el-button>
          <el-button @click="cancel">取 消</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="SignGrant" lang="ts">
import { addGrant, delGrant, listGrant, updateGrant, type GrantVO } from '@/api/system/sign/grant';
import { listSeal, type SealVO } from '@/api/system/sign/seal';
import { listUser } from '@/api/system/user';
import { deptTreeSelect } from '@/api/system/dept';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const loading = ref(false);
const open = ref(false);
const title = ref('');
const total = ref(0);
const grantList = ref<GrantVO[]>([]);
const companySeals = ref<SealVO[]>([]);
const userOptions = ref<any[]>([]);
const deptOptions = ref<any[]>([]);
const grantMode = ref<'user' | 'dept'>('user');

const queryParams = reactive({ pageNum: 1, pageSize: 10 });

const initForm: GrantVO = {
  grantId: undefined,
  sealId: undefined as any,
  granteeUserId: undefined,
  granteeDeptId: undefined,
  bizType: undefined,
  canSignAlone: '0',
  validFrom: undefined,
  validTo: undefined,
  status: '1',
  remark: ''
};
const form = ref<GrantVO>({ ...initForm });

const rules = {
  sealId: [{ required: true, message: '请选择公司章', trigger: 'change' }],
  granteeUserId: [{ required: true, message: '请选择被授权用户', trigger: 'change' }],
  granteeDeptId: [{ required: true, message: '请选择被授权部门', trigger: 'change' }]
};

const getList = async () => {
  loading.value = true;
  try {
    const res = await listGrant(queryParams);
    grantList.value = res.data.rows;
    total.value = res.data.total;
  } finally {
    loading.value = false;
  }
};

const loadRefs = async () => {
  const [sealRes, userRes, deptRes] = await Promise.all([
    listSeal({ pageNum: 1, pageSize: 200, sealType: '2' } as any),
    listUser({ pageNum: 1, pageSize: 200 } as any),
    deptTreeSelect()
  ]);
  companySeals.value = sealRes.data.rows ?? [];
  userOptions.value = userRes.data.rows ?? [];
  deptOptions.value = deptRes.data ?? [];
};

const cancel = () => {
  open.value = false;
  form.value = { ...initForm };
  (proxy?.$refs.grantRef as any)?.resetFields();
};

const handleAdd = async () => {
  cancel();
  await loadRefs();
  title.value = '新增用章授权';
  open.value = true;
};

const handleUpdate = async (row: GrantVO) => {
  cancel();
  await loadRefs();
  Object.assign(form.value, row);
  grantMode.value = row.granteeDeptId ? 'dept' : 'user';
  title.value = '修改用章授权';
  open.value = true;
};

const submitForm = () => {
  (proxy?.$refs.grantRef as any)?.validate(async (valid: boolean) => {
    if (!valid) return;
    const payload = { ...form.value };
    if (grantMode.value === 'user') {
      payload.granteeDeptId = undefined;
    } else {
      payload.granteeUserId = undefined;
    }
    if (payload.grantId) {
      await updateGrant(payload);
    } else {
      await addGrant(payload);
    }
    proxy?.$modal.msgSuccess('操作成功');
    open.value = false;
    await getList();
  });
};

const handleDelete = async (row: GrantVO) => {
  await proxy?.$modal.confirm('是否确认删除该授权？');
  await delGrant(row.grantId!);
  proxy?.$modal.msgSuccess('删除成功');
  await getList();
};

onMounted(getList);
</script>
```

- [ ] **Step 2: 创建签名记录页**

`plus-ui/src/views/system/sign/record/index.vue`：

```vue
<template>
  <div class="p-2">
    <el-card shadow="never">
      <el-form :model="queryParams" ref="queryRef" :inline="true">
        <el-form-item label="业务类型" prop="bizType">
          <el-select v-model="queryParams.bizType" placeholder="请选择" style="width: 200px" @change="loadData">
            <el-option label="流程审批" value="APPROVAL" />
            <el-option label="SOP 查阅" value="SOP_VIEW" />
            <el-option label="NCR 处置" value="NCR_DISPOSITION" />
            <el-option label="销售合同" value="SO_CONTRACT" />
            <el-option label="采购合同" value="PU_CONTRACT" />
          </el-select>
        </el-form-item>
        <el-form-item label="业务单据ID" prop="bizId">
          <el-input v-model="queryParams.bizId" placeholder="可选" clearable style="width: 220px" @keyup.enter="loadData" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" icon="Search" @click="loadData">查询</el-button>
          <el-button type="warning" icon="CircleCheck" @click="handleVerifyChain" v-hasPermi="['system:sign:record:verify']">全链验签</el-button>
        </el-form-item>
      </el-form>

      <el-alert v-if="verifyResult" :type="verifyResult.passed ? 'success' : 'error'" :closable="false" class="mb2" show-icon>
        <div class="font-bold">
          {{ verifyResult.passed ? '链校验通过' : `链校验失败，断裂位置：signatureId=${verifyResult.brokenAt}` }}
          （已校验 {{ verifyResult.checkedCount }} 条）
        </div>
        <div v-for="(m, i) in verifyResult.messages" :key="i" class="text-sm">{{ m }}</div>
      </el-alert>

      <el-table v-loading="loading" :data="records">
        <el-table-column label="签名ID" prop="signatureId" width="180" show-overflow-tooltip />
        <el-table-column label="签署人" prop="signerName" width="120" />
        <el-table-column label="印章" width="100">
          <template #default="scope">
            <el-tag v-if="scope.row.sealType === '2'" type="danger">公司章</el-tag>
            <el-tag v-else-if="scope.row.sealType === '1'" type="info">个人章</el-tag>
            <span v-else class="text-gray-400">未用章</span>
          </template>
        </el-table-column>
        <el-table-column label="签署含义" prop="signatureMeaning" show-overflow-tooltip />
        <el-table-column label="认证方式" prop="authMethod" width="120" />
        <el-table-column label="签署时间" prop="signedAt" width="170" />
        <el-table-column label="IP" prop="signIp" width="130" />
        <el-table-column label="链哈希" prop="chainHash" width="140" show-overflow-tooltip>
          <template #default="scope">
            <span class="font-mono text-xs">{{ scope.row.chainHash?.slice(0, 12) }}…</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="scope">
            <el-button link type="primary" icon="CircleCheck" @click="handleVerifyOne(scope.row)" v-hasPermi="['system:sign:record:verify']">验签</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup name="SignRecord" lang="ts">
import { listSignature, verifySignature, verifySignatureChain, type SignatureVO, type VerifyResultVO } from '@/api/system/sign/record';

const loading = ref(false);
const records = ref<SignatureVO[]>([]);
const verifyResult = ref<VerifyResultVO | null>(null);

const queryParams = reactive({ bizType: 'APPROVAL', bizId: '' });

const loadData = async () => {
  if (!queryParams.bizType) return;
  loading.value = true;
  verifyResult.value = null;
  try {
    const res = await listSignature(queryParams.bizType, queryParams.bizId || undefined);
    records.value = res.data ?? [];
  } finally {
    loading.value = false;
  }
};

const handleVerifyOne = async (row: SignatureVO) => {
  const res = await verifySignature(row.signatureId);
  verifyResult.value = res.data;
  proxy?.$modal.msgSuccess(res.data.passed ? '验签通过' : '验签失败');
};

const handleVerifyChain = async () => {
  const res = await verifySignatureChain(queryParams.bizType, queryParams.bizId || undefined);
  verifyResult.value = res.data;
};

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

onMounted(loadData);
</script>
```

- [ ] **Step 3: 前端检查与浏览器验证**

Run: `cd plus-ui && npm run lint:eslint`

浏览器验证：
- [ ] 「用章授权」页可为公司章授权给某用户，保存后列表可见
- [ ] 「签名记录」页按 `APPROVAL` 查询返回空列表且不报错
- [ ] 「全链验签」返回"共校验 0 条，链完整"

- [ ] **Step 4: 提交**

```bash
git add plus-ui/src/views/system/sign
git commit -m "feat(sign): 用章授权页与签名记录验签页"
```

---

## Task 14: 签章组件（选章 + 二级认证）

**Files:**
- Create: `plus-ui/src/components/sign/SealPicker.vue`
- Create: `plus-ui/src/components/sign/SignAuthDialog.vue`

- [ ] **Step 1: 创建选章组件**

`plus-ui/src/components/sign/SealPicker.vue`：

```vue
<template>
  <div class="seal-picker">
    <el-radio-group v-model="selected" class="flex flex-wrap gap-3">
      <el-radio v-for="s in seals" :key="s.sealId" :value="String(s.sealId)" border class="seal-item">
        <div class="flex items-center gap-2">
          <el-image v-if="s.previewOssId" :src="previewSrc(s.sealId)" fit="contain" class="w-12 h-12" />
          <div>
            <div class="font-bold">{{ s.sealName }}</div>
            <el-tag size="small" :type="s.sealType === '2' ? 'danger' : 'info'">
              {{ s.sealType === '2' ? '公司章' : '个人章' }}
            </el-tag>
          </div>
        </div>
      </el-radio>
    </el-radio-group>
    <el-empty v-if="!seals.length" description="当前用户在该场景下没有可用印章" :image-size="60" />
  </div>
</template>

<script setup lang="ts">
import { listAvailableSeal, type AvailableSealVO } from '@/api/system/sign/seal';

const props = defineProps<{ bizType: string }>();
const selected = defineModel<string | undefined>({ default: undefined });

const seals = ref<AvailableSealVO[]>([]);
const previewCache = new Map<string, string>();

const previewSrc = (sealId: string | number) => {
  const key = String(sealId);
  if (!previewCache.has(key)) {
    previewCache.set(key, `${import.meta.env.VITE_APP_BASE_API}/system/sign/seal/${key}/preview`);
  }
  return previewCache.get(key)!;
};

const load = async () => {
  const res = await listAvailableSeal(props.bizType);
  seals.value = res.data ?? [];
  // 仅一个可用印章时自动选中，减少操作
  if (seals.value.length === 1) {
    selected.value = String(seals.value[0].sealId);
  }
};

watch(() => props.bizType, load, { immediate: true });

defineExpose({ load });
</script>

<style scoped>
.seal-item {
  height: auto;
  padding: 8px 12px;
}
</style>
```

- [ ] **Step 2: 创建二级认证弹窗**

`plus-ui/src/components/sign/SignAuthDialog.vue`：

```vue
<template>
  <el-dialog v-model="visible" title="签署身份验证" width="440px" append-to-body :close-on-click-modal="false">
    <el-alert type="warning" :closable="false" class="mb3" show-icon
      title="签署行为将永久记录（签署人 / 时间 / IP / 哈希链），请确认是本人操作。" />

    <el-form ref="authRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="认证方式" prop="authMethod">
        <el-radio-group v-model="form.authMethod">
          <el-radio value="PASSWORD">登录密码</el-radio>
          <el-radio value="SMS">短信验证码</el-radio>
        </el-radio-group>
      </el-form-item>

      <el-form-item v-if="form.authMethod === 'PASSWORD'" label="登录密码" prop="credential">
        <el-input v-model="form.credential" type="password" show-password placeholder="请输入登录密码" @keyup.enter="confirm" />
      </el-form-item>

      <template v-else>
        <el-form-item label="验证码" prop="credential">
          <div class="flex gap-2 w-full">
            <el-input v-model="form.credential" placeholder="6 位验证码" maxlength="6" @keyup.enter="confirm" />
            <el-button :disabled="countdown > 0" @click="sendCode">
              {{ countdown > 0 ? `${countdown}s` : '获取验证码' }}
            </el-button>
          </div>
        </el-form-item>
      </template>
    </el-form>

    <template #footer>
      <div class="dialog-footer">
        <el-button type="primary" :loading="submitLoading" @click="confirm">确认签署</el-button>
        <el-button @click="visible = false">取消</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
import { sendSignSmsCode } from '@/api/system/sign/record';

const { proxy } = getCurrentInstance() as ComponentInternalInstance;

const visible = defineModel<boolean>({ default: false });
const emit = defineEmits<{ (e: 'confirmed', payload: { authMethod: string; credential: string }): void }>();

const authRef = ref();
const submitLoading = ref(false);
const countdown = ref(0);
const form = ref({ authMethod: 'PASSWORD', credential: '' });

const rules = {
  authMethod: [{ required: true, message: '请选择认证方式', trigger: 'change' }],
  credential: [{ required: true, message: '请输入认证凭证', trigger: 'blur' }]
};

let timer: ReturnType<typeof setInterval> | null = null;

const sendCode = async () => {
  await sendSignSmsCode();
  proxy?.$modal.msgSuccess('验证码已发送');
  countdown.value = 60;
  timer = setInterval(() => {
    countdown.value -= 1;
    if (countdown.value <= 0 && timer) {
      clearInterval(timer);
      timer = null;
    }
  }, 1000);
};

const confirm = () => {
  authRef.value?.validate((valid: boolean) => {
    if (!valid) return;
    emit('confirmed', { authMethod: form.value.authMethod, credential: form.value.credential });
  });
};

const reset = () => {
  form.value = { authMethod: 'PASSWORD', credential: '' };
  submitLoading.value = false;
};

watch(visible, (v) => {
  if (v) reset();
});

onBeforeUnmount(() => {
  if (timer) clearInterval(timer);
});

defineExpose({ setLoading: (v: boolean) => (submitLoading.value = v) });
</script>
```

> **安全提示**：`credential` 仅存于组件内存，**不写入 Pinia、localStorage 或 URL**；
> 提交后立即清空（`reset()` 在下次打开时执行）。

- [ ] **Step 3: 前端检查**

Run: `cd plus-ui && npm run lint:eslint && npx vue-tsc --noEmit -p tsconfig.json 2>&1 | head -30`
Expected: 无 error

- [ ] **Step 4: 提交**

```bash
git add plus-ui/src/components/sign
git commit -m "feat(sign): 选章组件与二级认证弹窗"
```

---

## Task 15: 审批流插桩（覆盖约 20 个单据）

**Files:**
- Modify: `ruoyi-modules/ruoyi-flowable/pom.xml`
- Modify: `ruoyi-modules/ruoyi-flowable/src/main/java/com/nexus/workflow/service/impl/WfTaskServiceImpl.java`
- Modify: `plus-ui/src/views/workflow/components/ActHandleBtn.vue`

- [ ] **Step 1: 新增模块依赖**

在 `ruoyi-modules/ruoyi-flowable/pom.xml` 的 `<dependencies>` 中追加：

```xml
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sign</artifactId>
        </dependency>
```

- [ ] **Step 2: 在 complete() 中插入签名关卡**

定位 `WfTaskServiceImpl.complete(TaskBo taskBo)`，在**获取到 `dataId` 之后、`taskService.complete(...)` 之前**
（即现有 `WfMyBusiness existBusiness = wfMyBusinessService.getByDataId(taskBo.getDataId());` 校验块之后）插入：

```java
        // ===== 电子签章关卡（P1）：节点配置 needSign 时必须先完成签署，否则拒绝流转 =====
        if (hasDataId && taskBo.getVariables() != null) {
            Object needSign = taskBo.getVariables().get("needSign");
            if (needSign != null && (
                    Boolean.TRUE.equals(needSign) || "true".equalsIgnoreCase(String.valueOf(needSign)))) {
                Object sealIdObj = taskBo.getVariables().get("signSealId");
                Long sealId = sealIdObj == null || String.valueOf(sealIdObj).isBlank()
                    ? null : Long.valueOf(String.valueOf(sealIdObj));
                String meaning = taskBo.getVariables().get("signMeaning") == null
                    ? "审批签批" : String.valueOf(taskBo.getVariables().get("signMeaning"));
                String authMethod = taskBo.getVariables().get("signAuthMethod") == null
                    ? SignAuthMethod.PASSWORD : String.valueOf(taskBo.getVariables().get("signAuthMethod"));
                String credential = taskBo.getVariables().get("signCredential") == null
                    ? null : String.valueOf(taskBo.getVariables().get("signCredential"));

                signatureService.sign(new SignCommand(
                    SignBizType.APPROVAL,
                    parseLongQuietly(taskBo.getDataId()),
                    null,
                    meaning,
                    sealId,
                    authMethod,
                    credential
                ));
            }
        }
        // ===== 电子签章关卡结束 =====
```

新增 import 与字段注入：

```java
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
```

```java
    /** 电子签章服务（P1 审批签批关卡） */
    private final ESignatureService signatureService;
```

以及私有工具方法：

```java
    /** dataId 非数字（如 UUID）时返回 null，不阻断审批 */
    private Long parseLongQuietly(String s) {
        if (s == null || s.isBlank()) {
            return null;
        }
        try {
            return Long.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
```

> **实施提示**：
> 1. `WfTaskServiceImpl` 若使用 `@Autowired` 字段注入而非构造器注入，**沿用该类既有风格**注入
>    `ESignatureService`，不要混用。
> 2. **凭证清理**：签名完成后应把 `signCredential` 从 `variables` 中移除，避免其随流程变量持久化到
>    `act_ru_variable`。在签名调用后追加 `taskBo.getVariables().remove("signCredential");`。
> 3. **异常语义**：`sign()` 抛 `ServiceException` 时整个 `complete` 回滚，审批不推进 —— 这正是
>    "不签章不生效"的保证。**不要**捕获并忽略该异常。

- [ ] **Step 3: 前端在审批弹窗中加签章入口**

修改 `plus-ui/src/views/workflow/components/ActHandleBtn.vue`：

在 `<el-form>` 的「处理意见」表单项之后插入：

```vue
          <el-form-item v-if="needSign" label="签署印章">
            <SealPicker v-model="signForm.sealId" :biz-type="'APPROVAL'" />
          </el-form-item>
          <el-form-item v-if="needSign" label="签署含义" prop="signMeaning">
            <el-input v-model="signForm.signMeaning" placeholder="如：审批通过" maxlength="128" />
          </el-form-item>
```

在 `<script setup>` 中追加：

```ts
import SealPicker from '@/components/sign/SealPicker.vue';
import SignAuthDialog from '@/components/sign/SignAuthDialog.vue';

/** 节点是否要求签章：由调用方通过 props.variables.needSign 传入 */
const needSign = computed(() => {
  const v = (props.variables as any)?.needSign;
  return v === true || String(v).toLowerCase() === 'true';
});

const signForm = reactive({ sealId: undefined as string | undefined, signMeaning: '审批通过' });
const authVisible = ref(false);
const authDialogRef = ref();
```

在提交函数 `handelSubmit` 中，**在调用后端之前**插入认证流程：

```ts
const handelSubmit = () => {
  formRef.value?.validate(async (valid: boolean) => {
    if (!valid) return;
    if (!needSign.value) {
      await doSubmit({});
      return;
    }
    if (!signForm.signMeaning?.trim()) {
      proxy?.$modal.msgWarning('请填写签署含义');
      return;
    }
    authVisible.value = true;
  });
};

/** 认证通过后，把签名变量并入流程变量提交 —— 由父组件 onConfirmed 触发 */
const onSignConfirmed = async (payload: { authMethod: string; credential: string }) => {
  authDialogRef.value?.setLoading(true);
  try {
    await doSubmit({
      needSign: true,
      signSealId: signForm.sealId ?? '',
      signMeaning: signForm.signMeaning,
      signAuthMethod: payload.authMethod,
      signCredential: payload.credential
    });
  } finally {
    authDialogRef.value?.setLoading(false);
    authVisible.value = false;
  }
};
```

模版末尾（`</el-dialog>` 之后、`</span>` 之前）追加：

```vue
    <SignAuthDialog ref="authDialogRef" v-model="authVisible" @confirmed="onSignConfirmed" />
```

> **实施提示**：`doSubmit(extraVariables)` 需把 `extraVariables` 合并进原有的 `props.variables`
> 再调后端接口。**必须复用本组件现有的提交函数（第 131 行附近被注释的 `completeTask`）所对应的
> 实际提交逻辑**，不要新写一套提交。实施时先完整阅读该文件，确认现有提交函数名与变量合并点。

- [ ] **Step 4: 后端编译 + 前端检查**

Run: `mvn -q -pl ruoyi-modules/ruoyi-flowable -am compile -DskipTests`
Run: `cd plus-ui && npm run lint:eslint`
Expected: 均无错误

- [ ] **Step 5: 端到端验证（关键验收）**

1. 在「流程模型」中给某节点加扩展属性 `needSign = true` 并部署
2. 用 A 账号发起一个已接入审批的单据（如销售合同）并提交
3. 用 B 账号（有审批权 + 已配置个人章）打开待办，点「通过」
4. **预期**：弹出签章框 → 选章 → 填含义 → 二级认证 → 提交成功
5. 查库：`SELECT * FROM es_signature WHERE biz_type='APPROVAL' ORDER BY signature_id DESC LIMIT 1;`
   **预期**：新增一条记录，`chain_hash` 非空、`signer_user_id=B`、`seal_id` 正确
6. **负向验证**：故意输错密码 → **预期**审批不通过，且 `es_signature` **无新增记录**
7. **回归验证**：另建一个**不配** `needSign` 的流程，走一遍审批 → **预期**行为与改造前完全一致，无签章弹窗

- [ ] **Step 6: 提交**

```bash
git add ruoyi-modules/ruoyi-flowable plus-ui/src/views/workflow/components/ActHandleBtn.vue
git commit -m "feat(sign): 审批流签章关卡（不签章不生效）"
```

---

## Task 16: MES P0-6 回填

**Files:**
- Modify: `ruoyi-modules/ruoyi-mes/pom.xml`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/sop/service/impl/SopViewLogServiceImpl.java`
- Modify: `ruoyi-modules/ruoyi-mes/src/main/java/com/nexus/mes/ncr/service/impl/NcrOrderServiceImpl.java`

- [ ] **Step 1: 新增模块依赖**

在 `ruoyi-modules/ruoyi-mes/pom.xml` 的 `<dependencies>` 中追加：

```xml
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sign</artifactId>
        </dependency>
```

- [ ] **Step 2: SOP 查阅回填 signature_id**

在 `SopViewLogServiceImpl` 中，把现有的

```java
        // signature_id = null (预留 P0-6 接入)
        log.setSignatureId(null);
```

替换为：

```java
        // P0-6 接入：SOP 查阅按「已阅确认」签署，失败不阻断查阅记录
        try {
            Long signatureId = signatureService.sign(new SignCommand(
                SignBizType.SOP_VIEW,
                sopId,
                versionNo,
                "SOP 已阅确认",
                null,
                SignAuthMethod.LOGIN_ONLY,
                null));
            log.setSignatureId(signatureId);
        } catch (Exception e) {
            log.warn("[sign] SOP 查阅签名失败, sopId={}, viewer={}: {}", sopId, viewerId, e.getMessage());
            log.setSignatureId(null);
        }
```

新增注入与 import：

```java
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
```

```java
    private final ESignatureService signatureService;
```

> **设计取舍**：SOP 查阅用 `LOGIN_ONLY`（打开查看器即记录，不应弹二次认证）；
> **签名失败降级为不阻断查阅** —— 与 CV 模块"视觉服务不可用不阻断主流程"的既有约定一致。
> 这与审批场景"不签章不生效"形成有意区分：查阅是**记录**，审批是**授权**。

- [ ] **Step 3: NCR 处置签名**

在 `NcrOrderServiceImpl` 中，把现有的空实现

```java
    public void bindDispositionSignature(Long ncrId, Long signatureId) {
        baseMapper.update(null, Wrappers.<NcrOrder>lambdaUpdate()
            .set(NcrOrder::getDispositionSignatureId, signatureId)
            .eq(NcrOrder::getNcrId, ncrId));
        log.info("NCR签名绑定: ncrId={}, signatureId={}", ncrId, signatureId);
    }
```

**保持不变**（它是纯绑定），新增一个签署入口方法，并在 `INcrOrderService` 中声明：

```java
    /**
     * NCR 处置签署：先签署再绑定，返回签名ID。
     *
     * @param ncrId              NCR 单ID
     * @param disposition        处置结论（冗余进签署含义）
     * @param authMethod         认证方式（P0-6 强制：不得为 LOGIN_ONLY）
     * @param credential         认证凭证
     * @return 签名ID
     */
    Long signDisposition(Long ncrId, String disposition, String authMethod, String credential);
```

实现：

```java
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long signDisposition(Long ncrId, String disposition, String authMethod, String credential) {
        NcrOrder ncr = baseMapper.selectById(ncrId);
        if (ncr == null) {
            throw new ServiceException("NCR 单不存在");
        }
        if (SignAuthMethod.LOGIN_ONLY.equals(authMethod)) {
            throw new ServiceException("NCR 处置签署必须进行二级认证");
        }
        Long signatureId = signatureService.sign(new SignCommand(
            SignBizType.NCR_DISPOSITION,
            ncrId,
            ncr.getNcrCode(),
            "不合格品处置批准：" + (disposition == null ? "" : disposition),
            null,
            authMethod,
            credential));
        bindDispositionSignature(ncrId, signatureId);
        return signatureId;
    }
```

- [ ] **Step 4: 后端编译与单测回归**

Run: `mvn -q -pl ruoyi-modules/ruoyi-mes -am compile -DskipTests`
Run: `mvn -q -pl ruoyi-modules/ruoyi-mes test -DskipTests=false -Dtest=NcrOrderServiceImplTest`
Expected: 编译通过；既有 NCR 测试仍 PASS（新增方法不改动既有行为）

- [ ] **Step 5: 提交**

```bash
git add ruoyi-modules/ruoyi-mes
git commit -m "feat(sign): 兑现 MES P0-6 预留（SOP 查阅 + NCR 处置签名）"
```

---

## Task 17: 端到端回归验收

**Files:** 无新增，仅验证与文档

- [ ] **Step 1: 全量后端测试**

Run: `mvn test -DskipTests=false -pl ruoyi-common/ruoyi-common-sign,ruoyi-modules/ruoyi-mes,ruoyi-modules/ruoyi-flowable -am`
Expected: 全 PASS，无新增失败

- [ ] **Step 2: 前端检查**

Run: `cd plus-ui && npm run lint:eslint`
Expected: 无 error

- [ ] **Step 3: 逐项验收（P1 验收标准）**

| # | 验收项 | 操作 | 期望 |
|---|--------|------|------|
| 1 | 未签章不可提交 | 对 `needSign` 节点直接调 `complete` 接口（不带 `signSealId`） | 返回失败，流程不推进，`es_signature` 无记录 |
| 2 | 签名信息完整 | 查 `es_signature` 最新记录 | `signer_user_id`/`signature_meaning`/`auth_method`/`sign_ip`/`sign_ua`/`signed_at` 均非空 |
| 3 | 篡改可检出 | `UPDATE es_signature SET signature_meaning='伪造' WHERE signature_id=?;` 后调验签 | `passed=false`，`brokenAt` 指向该条 |
| 4 | 全链可检出 | 同上，调「全链验签」 | 在篡改处断裂 |
| 5 | 个人章专有 | 用 B 账号尝试用 A 的个人章 `sealId` 调 `complete` | 返回"当前用户无权使用印章" |
| 6 | 公司章需授权 | 未授权时调用「可用印章」接口 | 返回列表中**不含**该公司章 |
| 7 | 公司章授权后可用 | 配置授权 → 再查可用印章 | 列表中**包含**该公司章 |
| 8 | 授权过期失效 | 把 `valid_to` 改到过去 → 再查 | 列表中**不含**该公司章 |
| 9 | 印章原图不外泄 | 全量搜索前端代码与接口响应 | 无任何接口返回 `sealOssId` |
| 10 | 存量流程零影响 | 跑一遍未配 `needSign` 的流程 | 行为与改造前一致 |
| 11 | MES SOP 回填 | 打开 SOP 查看器 → 查 `sop_view_log` | 新增行 `signature_id` 非空 |
| 12 | MES NCR 签署 | 调 `signDisposition` 且 `authMethod=LOGIN_ONLY` | 抛"必须进行二级认证" |

- [ ] **Step 4: 还原篡改数据**

验收第 3/4 项会污染数据，**必须还原**：

```sql
-- 用步骤 3 之前备份的值还原 signature_meaning 与 chain_hash
UPDATE es_signature SET signature_meaning = ?, chain_hash = ? WHERE signature_id = ?;
```

然后重新验签确认 `passed=true`。

- [ ] **Step 5: 更新文档状态**

把 `doc/system/specs/2026-09-15-esignature-impl-spec.md` 头部状态改为：

```markdown
> 状态：**P1 已完成** —— 签名核心/印章管理/用章授权/审批挂载/MES P0-6 回填均已落地并验收通过（2026-__-__）
```

在文档末尾「附：变更记录」追加一行：

```markdown
| 2026-__-__ | P1 完成实施与验收，见 `doc/system/plans/2026-09-15-esignature-p1-implementation-plan.md` |
```

同步更新 `doc/system/README.md` 中该文档的状态列：`📋 待评审` → `🚧 P1 已完成`。

- [ ] **Step 6: 提交**

```bash
git add doc/system
git commit -m "docs(sign): P1 验收完成，更新方案状态"
```

---

## Self-Review 记录

**Spec 覆盖检查**（对照 `doc/system/specs/2026-09-15-esignature-impl-spec.md` 第七章 P1）：

| Spec 要求 | 对应 Task |
|-----------|----------|
| `ESignatureService`（`sign`/`verify`/`verifyChain`/`listByBiz`/`listAvailableSeals`） | Task 9、10；`listAvailableSeals` 在 Task 6 |
| 4 张表 + `ry-vue.sql` + 变更登记 | Task 2 |
| 印章管理页（CRUD + 启停 + 预览） | Task 12 |
| 用章授权页 | Task 13 |
| 签名记录查询页 + 验签 | Task 13 |
| 二级认证（密码 / 短信） | Task 8 |
| 哈希链（SM3 + Lock4j 串行化）+ 验签接口 | Task 5、9、10 |
| 审批挂载：`ActHandleBtn.vue` + `WfTaskServiceImpl.complete()` | Task 15 |
| MES P0-6 回填：SOP + NCR | Task 16 |
| 菜单/权限/字典 SQL | Task 2 |
| 印章防滥用（`preview_oss_id` 水印预览、原图不外发） | Task 2（字段）、6（`loadPreviewImage`）、11（`preview` 接口）、12（前端仅用预览）、17（验收 9） |
| 「不签章不生效」后端强制 | Task 15 |
| 存量流程零影响 | Task 15 Step 5 第 7 项、Task 17 验收 10 |

**已知偏差（有意为之）**：
- Spec 提到 P1 含「短信认证」。若短信渠道未配置，Task 11 Step 3 提供了降级方案（仅保留密码认证）。
  这不影响内部留痕目标，但需在验收时明确记录所选路径。
- Spec 未细化 `es_sign_document` 的写入链路（P2 才用），P1 仅建表建模，符合"预留不实现"约定。

**类型一致性检查**：
- `SignCommand` 字段顺序在 Task 4 定义，Task 9/15/16 的所有构造调用均按
  `(bizType, bizId, bizNo, signatureMeaning, sealId, authMethod, credential)` 顺序，一致。
- `SealAvailabilityResolver.isUsable(seal, grants, userId, deptId, bizType)` 在 Task 6、9 调用一致。
- `SignatureChainCalculator.compute(prevHash, signatureId, bizType, bizId, signerUserId, sealId, meaning,
  docHash, signedAtEpochMs)` 在 Task 5、9、10 调用一致。
- `EsSignature.setSignedAt(...)` 为 `Date`，`verify` 中 `getSignedAt().getTime()` 取毫秒，与 `sign` 一致。

**Placeholder 扫描**：Task 9 Step 4 与 Task 10 之间保留了 `UnsupportedOperationException("Task 10")`
作为**有意的分步编译垫片**，Task 10 Step 3 明确给出替换代码 —— 这是可执行的，不是占位符。

---

## 风险提示（实施时必读）

| # | 风险 | 处置 |
|---|------|------|
| 1 | **链排序依赖时间**：`appendSignature` 按 `signed_at DESC, signature_id DESC` 取上一条。若应用节点时钟差 > 锁粒度，理论上可能取错。 | 部署机必须启用 NTP 时间同步（本仓库 Docker 编排已假设时钟同步）。如需强保证，后续为 `es_signature` 加 `seq_no` 列。 |
| 2 | **`es_signature` 无逻辑删除**：MyBatis-Plus 多租户插件对无 `tenant_id` 实体的处理需验证。`EsSignature` 未继承 `TenantEntity` 但保留了 `tenantId` 字段 —— **Task 9 Step 4 首次跑通后必须确认 insert 后 `tenant_id` 已正确写入**（查库验证，不能只看单测）。若插件不注入，改为在 `appendSignature` 中显式 `entity.setTenantId(LoginHelper.getTenantId())`。 |
| 3 | **`@Transactional` 与分布式锁顺序**：`sign()` 先在事务内取锁再写库，锁释放晚于事务提交（`finally` 在事务提交前执行）。极端情况下锁已释放但事务未提交，并发读可能取到旧链尾。 | 若验收第 4 项出现偶发断链，把锁下沉到事务外层（拆成两个方法，锁方法不加 `@Transactional`，内部调用事务方法）。 |
| 4 | **菜单 ID 冲突** | Task 2 Step 2 的 ID 需先查库确认。 |
| 5 | **`LambdaUtils` 静态 mock** | 新增涉及 lambda wrapper 的单测时，须按 Task 9 的模式补齐 `ColumnCache`，否则测试报 `MybatisPlusException`。 |
| 6 | **凭证泄漏** | Task 15 Step 2 的 `signCredential` 必须从 `variables` 移除后再流转，否则会持久化进 `act_ru_variable`。 |

---

## Execution Handoff

计划已完成并保存至 `doc/system/plans/2026-09-15-esignature-p1-implementation-plan.md`。

两种执行方式：

1. **Subagent-Driven（推荐）** —— 每个 Task 派发一个全新 subagent，Task 间由我审查，迭代快、上下文干净
2. **Inline Execution** —— 在当前会话内用 executing-plans 批量执行，带检查点

请选择执行方式。

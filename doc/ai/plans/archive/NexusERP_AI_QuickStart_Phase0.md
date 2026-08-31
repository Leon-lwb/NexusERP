# NexusERP AI Quick Start：Phase 0 最小可运行版本

> **目标**：5 天内跑通 **"用户提问 → LLM 流式回答 → SSE 推送到前端"** 的完整闭环。
>
> **前置条件**：
> - JDK 17 + Maven 3.8+
> - Node.js 20.19+ + pnpm/npm
> - MySQL 8.0（已有 ry-vue 和 ry-erp 两个数据库）
> - Redis（已运行）
> - 一个 LLM API Key（推荐 DeepSeek，兼容 OpenAI 接口格式）

---

## Day 1：创建 Maven 模块

### 1.1 在根 pom.xml 中添加版本属性

编辑 `E:\Nexus\nexus-erp\pom.xml`，在 `<properties>` 中新增：

```xml
<!-- AI 相关版本 -->
<langchain4j.version>1.13.0</langchain4j.version>
<langchain4j.community.version>1.13.0-beta23</langchain4j.community.version>
<langgraph4j.version>1.5.3</langgraph4j.version>
```

### 1.2 在 BOM 中注册新模块

编辑 `E:\Nexus\nexus-erp\ruoyi-common\ruoyi-common-bom\pom.xml`，在 `<dependencyManagement><dependencies>` 中新增：

```xml
<!-- AI 公共模块 -->
<dependency>
    <groupId>com.nexus</groupId>
    <artifactId>ruoyi-common-ai</artifactId>
    <version>${revision}</version>
</dependency>
```

### 1.3 创建 ruoyi-common-ai 模块

创建目录 `E:\Nexus\nexus-erp\ruoyi-common\ruoyi-common-ai\`

**pom.xml**：

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

    <artifactId>ruoyi-common-ai</artifactId>

    <description>
        ruoyi-common-ai AI公共模块
    </description>

    <dependencies>
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-core</artifactId>
        </dependency>

        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sse</artifactId>
        </dependency>

        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-mybatis</artifactId>
        </dependency>

        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-tenant</artifactId>
        </dependency>

        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <dependency>
            <groupId>io.swagger.core.v3</groupId>
            <artifactId>swagger-annotations</artifactId>
        </dependency>
    </dependencies>

</project>
```

创建目录 `E:\Nexus\nexus-erp\ruoyi-common\ruoyi-common-ai\src\main\java\com\nexus\common\ai\`

创建占位文件 `src\main\java\com\nexus\common\ai\package-info.java`：

```java
/**
 * NexusERP AI 公共模块 - 存放 AI 相关的 Entity/BO/VO/DTO/枚举
 */
package com.nexus.common.ai;
```

### 1.4 创建 ruoyi-ai 模块

创建目录 `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-ai\`

**pom.xml**：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <parent>
        <groupId>com.nexus</groupId>
        <artifactId>ruoyi-modules</artifactId>
        <version>${revision}</version>
    </parent>
    <modelVersion>4.0.0</modelVersion>

    <artifactId>ruoyi-ai</artifactId>

    <description>
        AI 模块 - 智能对话、数据查询、报表生成
    </description>

    <dependencies>

        <!-- AI 公共模块 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-ai</artifactId>
        </dependency>

        <!-- SSE 推送 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sse</artifactId>
        </dependency>

        <!-- 脱敏 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-sensitive</artifactId>
        </dependency>

        <!-- 幂等 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-idempotent</artifactId>
        </dependency>

        <!-- 日志 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-log</artifactId>
        </dependency>

        <!-- Web -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-web</artifactId>
        </dependency>

        <!-- 接口文档 -->
        <dependency>
            <groupId>com.nexus</groupId>
            <artifactId>ruoyi-common-doc</artifactId>
        </dependency>

        <!-- ========== LangChain4j 核心 ========== -->

        <!-- OpenAI 兼容接口（DeepSeek/通义千问/OpenAI 均走此接口） -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Ollama 本地模型 -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-ollama</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Agent 框架（SupervisorAgent 等） -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-agentic</artifactId>
            <version>${langchain4j.community.version}</version>
        </dependency>

        <!-- 阿里云 DashScope（通义千问） -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-dashscope</artifactId>
            <version>${langchain4j.community.version}</version>
        </dependency>

        <!-- 智谱 AI（GLM） -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-community-zhipu-ai</artifactId>
            <version>${langchain4j.community.version}</version>
        </dependency>

    </dependencies>

</project>
```

创建目录 `E:\Nexus\nexus-erp\ruoyi-modules\ruoyi-ai\src\main\java\com\nexus\ai\`

创建占位文件 `src\main\java\com\nexus\ai\package-info.java`：

```java
/**
 * NexusERP AI 核心模块 - Agent/Tool/Service/Controller
 */
package com.nexus.ai;
```

### 1.5 注册模块到父 pom

**编辑 `E:\Nexus\nexus-erp\ruoyi-common\pom.xml`**，在 `<modules>` 中新增：

```xml
<module>ruoyi-common-ai</module>
```

**编辑 `E:\Nexus\nexus-erp\ruoyi-modules\pom.xml`**，在 `<modules>` 中新增：

```xml
<module>ruoyi-ai</module>
```

### 1.6 在 ruoyi-admin 中引入 ruoyi-ai

**编辑 `E:\Nexus\nexus-erp\ruoyi-admin\pom.xml`**，在 `<dependencies>` 中新增：

```xml
<!-- AI 模块 -->
<dependency>
    <groupId>com.nexus</groupId>
    <artifactId>ruoyi-ai</artifactId>
    <version>${revision}</version>
</dependency>
```

### 1.7 验证编译

```bash
cd E:\Nexus\nexus-erp
mvn clean compile -pl ruoyi-common/ruoyi-common-ai,ruoyi-modules/ruoyi-ai,ruoyi-admin -am -DskipTests
```

如果编译通过，Day 1 完成。

---

## Day 2：建表 + 实体类 + 模型工厂

### 2.1 执行建表 SQL

在 **ry-vue** 数据库（系统管理库）中执行以下 SQL：

```sql
-- ============================================================
-- AI 厂商配置
-- ============================================================
CREATE TABLE ai_provider (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    name            VARCHAR(64)  NOT NULL COMMENT '厂商名称',
    api_host        VARCHAR(256) NOT NULL COMMENT 'API 地址',
    api_key         VARCHAR(512) NOT NULL COMMENT 'API Key（Jasypt 加密存储）',
    provider_type   VARCHAR(32)  NOT NULL COMMENT '厂商类型: OPENAI/DASHSCOPE/ZHIPU/OLLAMA',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 厂商配置';

-- ============================================================
-- AI 模型配置
-- ============================================================
CREATE TABLE ai_model (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    provider_id     BIGINT       NOT NULL COMMENT '关联厂商ID',
    model_name      VARCHAR(128) NOT NULL COMMENT '模型名称（如 deepseek-chat）',
    display_name    VARCHAR(128) DEFAULT NULL COMMENT '显示名称',
    model_type      VARCHAR(32)  NOT NULL COMMENT '类型: CHAT/EMBEDDING/RERANK',
    max_tokens      INT          DEFAULT 4096 COMMENT '最大输出 Token',
    temperature     DECIMAL(3,2) DEFAULT 0.70 COMMENT '温度参数',
    is_default      CHAR(1)      DEFAULT '0' COMMENT '是否默认模型（1是 0否）',
    status          CHAR(1)      DEFAULT '1' COMMENT '状态（1正常 0停用）',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_provider_id (provider_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 模型配置';

-- ============================================================
-- AI 对话会话
-- ============================================================
CREATE TABLE ai_session (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    user_id         BIGINT       NOT NULL COMMENT '用户ID',
    title           VARCHAR(256) DEFAULT NULL COMMENT '会话标题',
    model_name      VARCHAR(128) DEFAULT NULL COMMENT '使用的模型名称',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_user_id (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话会话';

-- ============================================================
-- AI 对话消息
-- ============================================================
CREATE TABLE ai_message (
    id              BIGINT       NOT NULL COMMENT '主键',
    tenant_id       BIGINT       DEFAULT 0 COMMENT '租户编号',
    session_id      BIGINT       NOT NULL COMMENT '会话ID',
    role            VARCHAR(16)  NOT NULL COMMENT '角色: USER/ASSISTANT/SYSTEM',
    content         TEXT         NOT NULL COMMENT '消息内容',
    token_usage     BIGINT       DEFAULT NULL COMMENT 'Token 消耗',
    create_dept     BIGINT       DEFAULT NULL COMMENT '创建部门',
    create_by       BIGINT       DEFAULT NULL COMMENT '创建者',
    create_time     DATETIME     DEFAULT NULL COMMENT '创建时间',
    update_by       BIGINT       DEFAULT NULL COMMENT '更新者',
    update_time     DATETIME     DEFAULT NULL COMMENT '更新时间',
    del_flag        CHAR(1)      DEFAULT '0' COMMENT '删除标志（0代表存在 2代表删除）',
    attr1           VARCHAR(256) DEFAULT NULL COMMENT '预留字段1',
    attr2           VARCHAR(256) DEFAULT NULL COMMENT '预留字段2',
    attr3           VARCHAR(256) DEFAULT NULL COMMENT '预留字段3',
    attr4           VARCHAR(256) DEFAULT NULL COMMENT '预留字段4',
    PRIMARY KEY (id),
    KEY idx_session_id (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 对话消息';

-- ============================================================
-- 插入默认厂商和模型（DeepSeek，兼容 OpenAI 接口）
-- ============================================================
INSERT INTO ai_provider (id, tenant_id, name, api_host, api_key, provider_type, status)
VALUES (1, 0, 'DeepSeek', 'https://api.deepseek.com', '你的API Key', 'OPENAI', '1');

INSERT INTO ai_model (id, tenant_id, provider_id, model_name, display_name, model_type, max_tokens, temperature, is_default, status)
VALUES (1, 0, 1, 'deepseek-chat', 'DeepSeek Chat', 'CHAT', 4096, 0.70, '1', '1');
```

> **注意**：将 `你的API Key` 替换为实际的 DeepSeek API Key。如果使用 Jasypt 加密，可用 `ENC(加密后的值)` 格式。

### 2.2 创建实体类（ruoyi-common-ai）

在 `ruoyi-common-ai` 中创建以下文件：

**AiProvider.java** — `src/main/java/com/nexus/common/ai/domain/entity/AiProvider.java`

```java
package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider")
public class AiProvider extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 厂商名称 */
    private String name;

    /** API 地址 */
    private String apiHost;

    /** API Key（Jasypt 加密存储） */
    private String apiKey;

    /** 厂商类型: OPENAI/DASHSCOPE/ZHIPU/OLLAMA */
    private String providerType;

    /** 状态（1正常 0停用） */
    private String status;
}
```

**AiModel.java** — `src/main/java/com/nexus/common/ai/domain/entity/AiModel.java`

```java
package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_model")
public class AiModel extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 关联厂商ID */
    private Long providerId;

    /** 模型名称（如 deepseek-chat） */
    private String modelName;

    /** 显示名称 */
    private String displayName;

    /** 类型: CHAT/EMBEDDING/RERANK */
    private String modelType;

    /** 最大输出 Token */
    private Integer maxTokens;

    /** 温度参数 */
    private BigDecimal temperature;

    /** 是否默认模型（1是 0否） */
    private String isDefault;

    /** 状态（1正常 0停用） */
    private String status;

    // ========== 非数据库字段（从关联查询填充） ==========

    /** 厂商 API 地址（来自 ai_provider） */
    private String apiHost;

    /** 厂商 API Key（来自 ai_provider） */
    private String apiKey;

    /** 厂商类型（来自 ai_provider） */
    private String providerType;
}
```

**AiSession.java** — `src/main/java/com/nexus/common/ai/domain/entity/AiSession.java`

```java
package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_session")
public class AiSession extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    private Long tenantId;

    private Long userId;

    private String title;

    private String modelName;
}
```

**AiMessage.java** — `src/main/java/com/nexus/common/ai/domain/entity/AiMessage.java`

```java
package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_message")
public class AiMessage extends BaseEntity {

    @TableId(value = "id")
    private Long id;

    private Long tenantId;

    private Long sessionId;

    private String role;

    private String content;

    private Long tokenUsage;
}
```

### 2.3 创建 Mapper 接口

在 `ruoyi-common-ai` 中创建：

**AiProviderMapper.java** — `src/main/java/com/nexus/common/ai/mapper/AiProviderMapper.java`

```java
package com.nexus.common.ai.mapper;

import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

public interface AiProviderMapper extends BaseMapperPlus<AiProvider, AiProvider> {
}
```

**AiModelMapper.java** — `src/main/java/com/nexus/common/ai/mapper/AiModelMapper.java`

```java
package com.nexus.common.ai.mapper;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

public interface AiModelMapper extends BaseMapperPlus<AiModel, AiModel> {
}
```

**AiSessionMapper.java** — `src/main/java/com/nexus/common/ai/mapper/AiSessionMapper.java`

```java
package com.nexus.common.ai.mapper;

import com.nexus.common.ai.domain.entity.AiSession;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

public interface AiSessionMapper extends BaseMapperPlus<AiSession, AiSession> {
}
```

**AiMessageMapper.java** — `src/main/java/com/nexus/common/ai/mapper/AiMessageMapper.java`

```java
package com.nexus.common.ai.mapper;

import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

public interface AiMessageMapper extends BaseMapperPlus<AiMessage, AiMessage> {
}
```

### 2.4 创建 Service 接口和实现

**IAiModelService.java** — `src/main/java/com/nexus/common/ai/service/IAiModelService.java`

```java
package com.nexus.common.ai.service;

import com.nexus.common.ai.domain.entity.AiModel;

/**
 * AI 模型配置 Service
 */
public interface IAiModelService {

    /**
     * 根据模型名称查询模型配置（含厂商信息）
     */
    AiModel selectModelByName(String modelName);

    /**
     * 根据ID查询模型配置（含厂商信息）
     */
    AiModel selectModelById(Long id);

    /**
     * 查询默认模型
     */
    AiModel selectDefaultModel();
}
```

**AiModelServiceImpl.java** — `src/main/java/com/nexus/common/ai/service/impl/AiModelServiceImpl.java`

```java
package com.nexus.common.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.domain.entity.AiProvider;
import com.nexus.common.ai.mapper.AiModelMapper;
import com.nexus.common.ai.mapper.AiProviderMapper;
import com.nexus.common.ai.service.IAiModelService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AiModelServiceImpl implements IAiModelService {

    private final AiModelMapper aiModelMapper;
    private final AiProviderMapper aiProviderMapper;

    @Override
    public AiModel selectModelByName(String modelName) {
        AiModel model = aiModelMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getModelName, modelName)
                .eq(AiModel::getStatus, "1")
        );
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    @Override
    public AiModel selectModelById(Long id) {
        AiModel model = aiModelMapper.selectById(id);
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    @Override
    public AiModel selectDefaultModel() {
        AiModel model = aiModelMapper.selectOne(
            new LambdaQueryWrapper<AiModel>()
                .eq(AiModel::getIsDefault, "1")
                .eq(AiModel::getStatus, "1")
        );
        if (model != null) {
            fillProviderInfo(model);
        }
        return model;
    }

    private void fillProviderInfo(AiModel model) {
        AiProvider provider = aiProviderMapper.selectById(model.getProviderId());
        if (provider != null) {
            model.setApiHost(provider.getApiHost());
            model.setApiKey(provider.getApiKey());
            model.setProviderType(provider.getProviderType());
        }
    }
}
```

### 2.5 创建 ChatModelFactory

在 `ruoyi-ai` 模块中创建：

**ChatModelFactory.java** — `src/main/java/com/nexus/ai/service/chat/ChatModelFactory.java`

```java
package com.nexus.ai.service.chat;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.service.IAiModelService;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * AI 模型工厂（带缓存）
 * 根据数据库配置动态构建 ChatModel 和 StreamingChatModel
 */
@Component
@RequiredArgsConstructor
public class ChatModelFactory {

    private final IAiModelService aiModelService;

    private final ConcurrentHashMap<Long, ChatModel> chatModelCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, StreamingChatModel> streamingModelCache = new ConcurrentHashMap<>();

    /**
     * 获取同步模型（带缓存）
     */
    public ChatModel getChatModel(Long modelId) {
        return chatModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            return buildChatModel(config);
        });
    }

    /**
     * 获取流式模型（带缓存）
     */
    public StreamingChatModel getStreamingChatModel(Long modelId) {
        return streamingModelCache.computeIfAbsent(modelId, id -> {
            AiModel config = aiModelService.selectModelById(id);
            return buildStreamingChatModel(config);
        });
    }

    /**
     * 根据模型名称获取同步模型
     */
    public ChatModel getChatModelByName(String modelName) {
        AiModel config = aiModelService.selectModelByName(modelName);
        if (config == null) {
            throw new IllegalArgumentException("未找到模型配置: " + modelName);
        }
        return getChatModel(config.getId());
    }

    /**
     * 清除缓存（模型配置变更时调用）
     */
    public void evictCache(Long modelId) {
        chatModelCache.remove(modelId);
        streamingModelCache.remove(modelId);
    }

    private ChatModel buildChatModel(AiModel config) {
        // 目前所有厂商均走 OpenAI 兼容接口
        // DeepSeek、通义千问（兼容模式）、智谱 AI 均支持 OpenAI 格式
        return OpenAiChatModel.builder()
            .baseUrl(config.getApiHost())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .build();
    }

    private StreamingChatModel buildStreamingChatModel(AiModel config) {
        return OpenAiStreamingChatModel.builder()
            .baseUrl(config.getApiHost())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .temperature(config.getTemperature())
            .maxTokens(config.getMaxTokens())
            .build();
    }
}
```

> **说明**：Phase 0 先用 OpenAI 兼容接口统一处理（DeepSeek、通义千问均兼容），后续 Phase 再按 `providerType` 分支处理。

### 2.6 验证编译

```bash
cd E:\Nexus\nexus-erp
mvn clean compile -pl ruoyi-common/ruoyi-common-ai,ruoyi-modules/ruoyi-ai,ruoyi-admin -am -DskipTests
```

---

## Day 3：SSE 消息工具 + ERP 数据源服务 + 聊天接口

### 3.1 创建 AiSseMessageHelper

在 `ruoyi-ai` 模块中创建：

**AiSseMessageHelper.java** — `src/main/java/com/nexus/ai/service/sse/AiSseMessageHelper.java`

```java
package com.nexus.ai.service.sse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.sse.dto.SseMessageDto;
import com.nexus.common.sse.utils.SseMessageUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI SSE 消息工具类
 *
 * 将 AI 消息类型编码到 SseMessageDto.message 字段中（JSON 格式），
 * 前端解析 message JSON，根据 type 字段区分消息类型。
 *
 * 使用 publishMessage 支持多节点部署（通过 Redis Pub/Sub 广播）。
 */
@Slf4j
public class AiSseMessageHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** AI 回复内容（流式 Token） */
    public static final String TYPE_CONTENT = "AI_CONTENT";
    /** AI 思考中 */
    public static final String TYPE_THINKING = "AI_THINKING";
    /** AI 回复完成 */
    public static final String TYPE_DONE = "AI_DONE";
    /** AI 出错 */
    public static final String TYPE_ERROR = "AI_ERROR";
    /** ECharts 图表配置 */
    public static final String TYPE_CHART = "AI_CHART";

    public static void sendContent(Long userId, String content) {
        send(userId, TYPE_CONTENT, content);
    }

    public static void sendThinking(Long userId) {
        send(userId, TYPE_THINKING, "AI 正在思考...");
    }

    public static void sendDone(Long userId) {
        send(userId, TYPE_DONE, null);
    }

    public static void sendError(Long userId, String error) {
        send(userId, TYPE_ERROR, error);
    }

    public static void sendChart(Long userId, String chartJson) {
        send(userId, TYPE_CHART, chartJson);
    }

    private static void send(Long userId, String type, String content) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", type);
            if (content != null) {
                payload.put("content", content);
            }
            String json = MAPPER.writeValueAsString(payload);

            // 使用 publishMessage 支持多节点部署
            SseMessageDto dto = new SseMessageDto();
            dto.setUserIds(List.of(userId));
            dto.setMessage(json);
            SseMessageUtils.publishMessage(dto);
        } catch (Exception e) {
            log.error("发送 AI SSE 消息失败, userId={}, type={}", userId, type, e);
        }
    }
}
```

### 3.2 创建 AI 专用线程池

**AiThreadPoolConfig.java** — `src/main/java/com/nexus/ai/config/AiThreadPoolConfig.java`

```java
package com.nexus.ai.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;

/**
 * AI 专用线程池配置
 * 避免 CompletableFuture.runAsync() 使用 ForkJoinPool.commonPool() 耗尽公共线程
 */
@Configuration
public class AiThreadPoolConfig {

    @Bean("aiExecutor")
    public Executor aiExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("ai-task-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }
}
```

### 3.3 创建 ERP 数据源 SQL 执行服务

**ErpQueryService.java** — `src/main/java/com/nexus/ai/service/query/ErpQueryService.java`

```java
package com.nexus.ai.service.query;

import com.baomidou.dynamic.datasource.annotation.DS;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.ResultSetMetaData;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ERP 数据源 SQL 执行服务
 *
 * 关键：标注 @DS("erp") 切换到 ERP 数据源（ry-erp 库）。
 * ruoyi-ai 的 ExecuteSqlQueryTool 使用 SpringUtils.getBean(DataSource.class)
 * 会拿到 master 数据源（ry-vue），查不到 ERP 业务表。
 * 此 Service 通过 @DS 注解正确切换到 ERP 数据源。
 */
@Slf4j
@Service
@DS("erp")
@RequiredArgsConstructor
public class ErpQueryService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * 执行 SELECT 查询
     *
     * @param sql      SQL 语句
     * @param maxRows  最大返回行数
     * @param timeoutSeconds 超时秒数
     */
    public List<Map<String, Object>> executeQuery(String sql, int maxRows, int timeoutSeconds) {
        jdbcTemplate.setQueryTimeout(timeoutSeconds);
        jdbcTemplate.setMaxRows(maxRows);
        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            ResultSetMetaData meta = rs.getMetaData();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                row.put(meta.getColumnName(i), rs.getObject(i));
            }
            return row;
        });
    }

    /**
     * 查询所有 ERP 业务表（只返回 bd_*/so_*/pu_*/prd_*/wm_*/sch_* 前缀的表）
     */
    public List<String> queryErpTables() {
        return jdbcTemplate.queryForList(
            "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE()",
            String.class
        ).stream()
         .filter(name -> name.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*"))
         .toList();
    }

    /**
     * 查询表结构（SHOW CREATE TABLE，间接含 COMMENT）
     */
    public String queryTableSchema(String tableName) {
        List<Map<String, Object>> results = jdbcTemplate.queryForList(
            "SHOW CREATE TABLE `" + tableName + "`"
        );
        if (!results.isEmpty()) {
            Object createTable = results.get(0).get("Create Table");
            return createTable != null ? createTable.toString() : null;
        }
        return null;
    }
}
```

### 3.4 创建 AI 配置属性

**AiProperties.java** — `src/main/java/com/nexus/ai/config/AiProperties.java`

```java
package com.nexus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * AI 配置属性（前缀 nexus.ai，避免与 Spring AI 的 spring.ai 冲突）
 */
@Data
@ConfigurationProperties(prefix = "nexus.ai")
public class AiProperties {

    /** 默认厂商 */
    private String defaultProvider = "deepseek";

    /** 默认模型 */
    private String defaultModel = "deepseek-chat";

    /** 允许查询的表名前缀 */
    private List<String> allowedTablePrefixes = List.of("bd_", "so_", "pu_", "prd_", "wm_", "sch_");

    /** SQL 执行超时（毫秒） */
    private int sqlTimeout = 5000;

    /** SQL 最大返回行数 */
    private int sqlMaxRows = 1000;
}
```

### 3.5 创建聊天请求 DTO

**AiChatRequest.java** — `src/main/java/com/nexus/common/ai/dto/AiChatRequest.java`

```java
package com.nexus.common.ai.dto;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

@Data
public class AiChatRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 会话ID（新会话传 null） */
    private Long sessionId;

    /** 消息内容 */
    private String content;

    /** 模型名称（可选，为空使用默认模型） */
    private String model;
}
```

### 3.6 创建 AiServiceFacade（核心门面）

**AiServiceFacade.java** — `src/main/java/com/nexus/ai/service/facade/AiServiceFacade.java`

```java
package com.nexus.ai.service.facade;

import com.nexus.common.ai.domain.entity.AiModel;
import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.ai.service.IAiModelService;
import com.nexus.ai.service.chat.ChatModelFactory;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
import dev.langchain4j.model.chat.ChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * AI 服务门面
 * 路由：普通对话 / RAG / Agent 编排
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiServiceFacade {

    private final ChatModelFactory chatModelFactory;
    private final IAiModelService aiModelService;

    @Qualifier("aiExecutor")
    private final Executor aiExecutor;

    /**
     * 聊天入口
     */
    public void chat(AiChatRequest request) {
        Long userId = com.nexus.common.satoken.utils.LoginHelper.getUserId();

        // 1. 获取模型配置
        AiModel modelConfig;
        if (request.getModel() != null && !request.getModel().isEmpty()) {
            modelConfig = aiModelService.selectModelByName(request.getModel());
        } else {
            modelConfig = aiModelService.selectDefaultModel();
        }
        if (modelConfig == null) {
            AiSseMessageHelper.sendError(userId, "未找到可用的 AI 模型，请先配置模型");
            return;
        }

        // 2. 构建模型
        ChatModel chatModel = chatModelFactory.getChatModel(modelConfig.getId());

        // 3. Phase 0：直接对话（不经过 Agent）
        //    后续 Phase 1 会改为 SupervisorAgent 编排
        AiSseMessageHelper.sendThinking(userId);

        CompletableFuture.runAsync(() -> {
            try {
                String result = chatModel.chat(request.getContent());
                AiSseMessageHelper.sendContent(userId, result);
                AiSseMessageHelper.sendDone(userId);
            } catch (Exception e) {
                log.error("AI 对话失败", e);
                AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
            }
        }, aiExecutor);
    }
}
```

### 3.7 创建 AiChatController

**AiChatController.java** — `src/main/java/com/nexus/ai/controller/AiChatController.java`

```java
package com.nexus.ai.controller;

import com.nexus.common.ai.dto.AiChatRequest;
import com.nexus.common.core.domain.R;
import com.nexus.ai.service.facade.AiServiceFacade;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * AI 聊天接口
 */
@Validated
@RequiredArgsConstructor
@RestController
@RequestMapping("/ai/chat")
public class AiChatController {

    private final AiServiceFacade aiServiceFacade;

    /**
     * 发送消息
     * 前端通过 nexus-erp 已有的 SSE 连接接收流式响应
     */
    @PostMapping("/send")
    public R<Void> send(@RequestBody AiChatRequest request) {
        aiServiceFacade.chat(request);
        return R.ok();
    }
}
```

### 3.8 添加 YAML 配置

编辑 `E:\Nexus\nexus-erp\ruoyi-admin\src\main\resources\application.yml`，在文件末尾新增：

```yaml
# NexusERP AI 配置
nexus:
  ai:
    default-provider: deepseek
    default-model: deepseek-chat
    allowed-table-prefixes: bd_,so_,pu_,prd_,wm_,sch_
    sql-timeout: 5000
    sql-max-rows: 1000
```

### 3.9 验证编译并启动

```bash
cd E:\Nexus\nexus-erp
mvn clean compile -pl ruoyi-common/ruoyi-common-ai,ruoyi-modules/ruoyi-ai,ruoyi-admin -am -DskipTests
```

启动 `RuoYiApplication`，确认无报错。

### 3.10 验证 SSE 闭环

使用 curl 或 Postman 测试：

```bash
# 1. 先登录获取 token（替换为实际账号密码）
curl -X POST http://localhost:8080/auth/login \
  -H "Content-Type: application/json" \
  -H "clientid: e5cd7e4891bf95d1d19206ce24a7b32e" \
  -d '{"username":"admin","password":"admin123"}'

# 2. 建立 SSE 连接（在另一个终端，替换 TOKEN）
curl -N http://localhost:8080/resource/sse?Authorization=Bearer%20TOKEN&clientid=e5cd7e4891bf95d1d19206ce24a7b32e

# 3. 发送 AI 消息（在第三个终端）
curl -X POST http://localhost:8080/ai/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"content":"你好，请介绍一下你自己"}'
```

在 SSE 连接终端应看到：
```
data: {"type":"AI_THINKING","content":"AI 正在思考..."}

data: {"type":"AI_CONTENT","content":"你好！我是 NexusERP 智能助手..."}

data: {"type":"AI_DONE"}
```

如果看到以上输出，**SSE 闭环验证成功**！

---

## Day 4：接入 Text2SQL（ERP 数据查询 Agent）

### 4.1 创建 SQL Tool（注入 ErpQueryService）

**QueryAllTablesTool.java** — `src/main/java/com/nexus/ai/tool/QueryAllTablesTool.java`

```java
package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.Tool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryAllTablesTool {

    private final ErpQueryService erpQueryService;

    @Tool("Query all available ERP business tables in the database")
    public String queryAllTables() {
        try {
            List<String> tables = erpQueryService.queryErpTables();
            if (tables.isEmpty()) {
                return "No ERP business tables found.";
            }
            StringBuilder result = new StringBuilder();
            result.append("Found ").append(tables.size()).append(" ERP business tables:\n");
            for (String tableName : tables) {
                result.append("- ").append(tableName).append("\n");
            }
            return result.toString();
        } catch (Exception e) {
            log.error("查询 ERP 表列表失败", e);
            return "Error: " + e.getMessage();
        }
    }
}
```

**QueryTableSchemaTool.java** — `src/main/java/com/nexus/ai/tool/QueryTableSchemaTool.java`

```java
package com.nexus.ai.tool;

import com.nexus.ai.service.query.ErpQueryService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class QueryTableSchemaTool {

    private final ErpQueryService erpQueryService;

    @Tool("Query the CREATE TABLE statement (DDL) for a specific ERP table by table name")
    public String queryTableSchema(@P("Table name") String tableName) {
        if (tableName == null || tableName.trim().isEmpty()) {
            return "Error: Table name cannot be empty";
        }
        if (!tableName.matches("^[a-zA-Z0-9_]+$")) {
            return "Error: Invalid table name format";
        }
        if (!tableName.matches("^(bd_|so_|pu_|prd_|wm_|sch_).*")) {
            return "Error: Only ERP business tables are allowed (bd_*/so_*/pu_*/prd_*/wm_*/sch_*)";
        }
        try {
            String schema = erpQueryService.queryTableSchema(tableName);
            if (schema == null) {
                return "Table not found: " + tableName;
            }
            return schema;
        } catch (Exception e) {
            log.error("查询表结构失败: {}", tableName, e);
            return "Error: " + e.getMessage();
        }
    }
}
```

**ExecuteSqlQueryTool.java** — `src/main/java/com/nexus/ai/tool/ExecuteSqlQueryTool.java`

```java
package com.nexus.ai.tool;

import com.nexus.ai.config.AiProperties;
import com.nexus.ai.service.query.ErpQueryService;
import com.nexus.common.satoken.utils.LoginHelper;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.P;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@RequiredArgsConstructor
public class ExecuteSqlQueryTool {

    private final ErpQueryService erpQueryService;
    private final AiProperties aiProperties;

    /** 禁止出现的 SQL 关键词 */
    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
        "INTO OUTFILE", "INTO DUMPFILE", "LOAD_FILE", "LOAD DATA",
        "INSERT", "UPDATE", "DELETE", "DROP", "ALTER", "CREATE",
        "GRANT", "REVOKE", "EXECUTE", "INFORMATION_SCHEMA",
        "SLEEP", "BENCHMARK", "WAITFOR"
    );

    /** 表名提取正则 */
    private static final Pattern TABLE_NAME_PATTERN =
        Pattern.compile("(?:FROM|JOIN)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?", Pattern.CASE_INSENSITIVE);

    @Tool("Execute a SELECT SQL query on ERP database and return the results")
    public String executeSqlQuery(@P("SQL SELECT query statement") String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return "Error: SQL query cannot be empty";
        }

        String upperSql = sql.trim().toUpperCase();

        // 1. 必须以 SELECT 开头
        if (!upperSql.startsWith("SELECT")) {
            return "Error: Only SELECT queries are allowed";
        }

        // 2. 禁止危险关键词
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (upperSql.contains(keyword)) {
                return "Error: SQL contains forbidden keyword: " + keyword;
            }
        }

        // 3. 表名白名单校验
        Matcher matcher = TABLE_NAME_PATTERN.matcher(sql);
        while (matcher.find()) {
            String tableName = matcher.group(1);
            boolean allowed = aiProperties.getAllowedTablePrefixes().stream()
                .anyMatch(tableName::startsWith);
            if (!allowed) {
                return "Error: Table '" + tableName + "' is not allowed. Only ERP business tables are permitted.";
            }
        }

        // 4. 注入租户隔离条件
        Long tenantId = LoginHelper.getTenantId();
        sql = injectTenantCondition(sql, tenantId);

        // 5. 追加行数限制
        if (!upperSql.contains("LIMIT")) {
            sql = sql + " LIMIT " + aiProperties.getSqlMaxRows();
        }

        // 6. 通过 ErpQueryService 执行（走 @DS("erp") 数据源）
        try {
            List<Map<String, Object>> results = erpQueryService.executeQuery(
                sql, aiProperties.getSqlMaxRows(), aiProperties.getSqlTimeout() / 1000);
            return formatResults(results);
        } catch (Exception e) {
            log.error("执行 SQL 查询失败: {}", sql, e);
            return "Error: Query execution failed - " + e.getMessage();
        }
    }

    private String injectTenantCondition(String sql, Long tenantId) {
        if (sql.toUpperCase().contains("TENANT_ID")) {
            return sql;
        }
        String upperSql = sql.toUpperCase();
        if (upperSql.contains("WHERE")) {
            int whereIndex = upperSql.indexOf("WHERE");
            sql = sql.substring(0, whereIndex + 5) +
                  " tenant_id = " + tenantId + " AND" +
                  sql.substring(whereIndex + 5);
        } else if (upperSql.contains("LIMIT")) {
            int limitIndex = upperSql.indexOf("LIMIT");
            sql = sql.substring(0, limitIndex) +
                  " WHERE tenant_id = " + tenantId + " " +
                  sql.substring(limitIndex);
        } else {
            sql = sql + " WHERE tenant_id = " + tenantId;
        }
        return sql;
    }

    private String formatResults(List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return "Query executed successfully, but no results returned.";
        }

        StringBuilder result = new StringBuilder();
        int displayRows = Math.min(results.size(), 10);

        // 获取列名
        List<String> columnNames = List.copyOf(results.get(0).keySet());
        int displayCols = Math.min(columnNames.size(), 8);
        List<String> displayColumns = columnNames.subList(0, displayCols);

        // 表头
        result.append("| ");
        for (String col : displayColumns) {
            result.append(col).append(" | ");
        }
        result.append("\n|");
        for (int i = 0; i < displayCols; i++) {
            result.append(" --- |");
        }
        result.append("\n");

        // 数据行
        for (int i = 0; i < displayRows; i++) {
            result.append("| ");
            Map<String, Object> row = results.get(i);
            for (String column : displayColumns) {
                Object value = row.get(column);
                String displayValue = (value == null) ? "-" : value.toString();
                if (displayValue.length() > 20) {
                    displayValue = displayValue.substring(0, 17) + "...";
                }
                result.append(displayValue).append(" | ");
            }
            result.append("\n");
        }

        result.append("\nTotal: ").append(results.size()).append(" rows");
        if (displayRows < results.size()) {
            result.append(" (displayed ").append(displayRows).append(" rows)");
        }
        return result.toString();
    }
}
```

### 4.2 创建 ErpQueryAgent

**ErpQueryAgent.java** — `src/main/java/com/nexus/ai/agent/ErpQueryAgent.java`

```java
package com.nexus.ai.agent;

import dev.langchain4j.agentic.Agent;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

/**
 * ERP 数据查询 Agent
 * 注解放在接口方法上（非类级别），与 ruoyi-ai 的 SqlAgent 一致
 */
public interface ErpQueryAgent {

    @SystemMessage("""
        你是一个 ERP 数据查询助手。你负责：
        1. 查询数据库表结构，理解 NexusERP 的业务数据模型
        2. 根据用户的自然语言问题，生成并执行 SQL 查询
        3. 用清晰的中文回答用户的问题，必要时用表格展示数据

        重要规则：
        - 你只能查询 SELECT 语句，严禁执行 INSERT/UPDATE/DELETE
        - 你只能访问以下业务表：bd_*, so_*, pu_*, prd_*, wm_*, sch_*
        - 涉及金额数据时，保留2位小数
        - 涉及日期数据时，使用 YYYY-MM-DD 格式
        - 所有查询必须包含 tenant_id 条件以遵守数据隔离

        可用工具：
        - queryAllTables: 查询数据库中所有可用的 ERP 业务表
        - queryTableSchema: 查询指定表的结构和字段说明
        - executeSqlQuery: 执行 SELECT SQL 查询

        CRITICAL: 你必须先调用 queryAllTables 了解数据库结构，再调用 queryTableSchema 查看表结构，
        然后才能构造并执行 SQL 查询。
        """)
    @UserMessage("Answer the following question: {{query}}")
    @Agent("ERP 数据查询助手")
    String query(@V("query") String query);
}
```

### 4.3 修改 AiServiceFacade（加入 Agent 编排）

将 `AiServiceFacade.java` 中的 `chat()` 方法替换为：

```java
public void chat(AiChatRequest request) {
    Long userId = com.nexus.common.satoken.utils.LoginHelper.getUserId();

    // 1. 获取模型配置
    AiModel modelConfig;
    if (request.getModel() != null && !request.getModel().isEmpty()) {
        modelConfig = aiModelService.selectModelByName(request.getModel());
    } else {
        modelConfig = aiModelService.selectDefaultModel();
    }
    if (modelConfig == null) {
        AiSseMessageHelper.sendError(userId, "未找到可用的 AI 模型，请先配置模型");
        return;
    }

    // 2. 构建模型
    ChatModel chatModel = chatModelFactory.getChatModel(modelConfig.getId());

    // 3. 构建 ERP 查询 Agent
    ErpQueryAgent queryAgent = AgenticServices.agentBuilder(ErpQueryAgent.class)
        .chatModel(chatModel)
        .tools(queryAllTablesTool, queryTableSchemaTool, executeSqlQueryTool)
        .build();

    // 4. 构建 Supervisor
    SupervisorAgent supervisor = AgenticServices.supervisorBuilder()
        .chatModel(chatModel)
        .subAgents(queryAgent)
        .responseStrategy(SupervisorResponseStrategy.LAST)
        .build();

    // 5. 发送"思考中"状态
    AiSseMessageHelper.sendThinking(userId);

    // 6. 异步执行 + SSE 推送结果
    CompletableFuture.runAsync(() -> {
        try {
            String result = supervisor.invoke(request.getContent());
            AiSseMessageHelper.sendContent(userId, result);
            AiSseMessageHelper.sendDone(userId);
        } catch (Exception e) {
            log.error("AI 对话失败", e);
            AiSseMessageHelper.sendError(userId, "AI 对话失败: " + e.getMessage());
        }
    }, aiExecutor);
}
```

同时在类中注入三个 Tool：

```java
private final QueryAllTablesTool queryAllTablesTool;
private final QueryTableSchemaTool queryTableSchemaTool;
private final ExecuteSqlQueryTool executeSqlQueryTool;
```

并添加 import：

```java
import com.nexus.ai.agent.ErpQueryAgent;
import com.nexus.ai.tool.QueryAllTablesTool;
import com.nexus.ai.tool.QueryTableSchemaTool;
import com.nexus.ai.tool.ExecuteSqlQueryTool;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.supervisor.SupervisorAgent;
import dev.langchain4j.agentic.supervisor.SupervisorResponseStrategy;
```

### 4.4 验证 Text2SQL

重启应用，测试：

```bash
curl -X POST http://localhost:8080/ai/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"content":"查询本月所有采购订单的总金额"}'
```

在 SSE 连接中应看到 AI 先查询表结构，然后生成 SQL 并返回结果。

---

## Day 5：前端 AI 聊天页面

### 5.1 安装前端依赖

```bash
cd E:\Nexus\nexus-erp\plus-ui
npm install vue-element-plus-x@1.3.0 hook-fetch@2.0.4-beta.1 github-markdown-css
```

> 安装后需验证 vue-element-plus-x 与 Element Plus 2.13.5 的兼容性。如遇组件渲染异常，可临时降级 Element Plus 至 2.10.x。

### 5.2 修改 SSE 消息处理

编辑 `E:\Nexus\nexus-erp\plus-ui\src\utils\sse.ts`，在消息处理逻辑中新增 AI 消息类型分支。

找到 `watch(data, (val)` 回调中的消息处理逻辑，在现有的 `USER_NOTIFICATION` 和 `IM_MESSAGE` 分支后，新增：

```typescript
// AI 消息类型处理
if (obj.type && obj.type.startsWith('AI_')) {
  window.dispatchEvent(new CustomEvent('ai-sse-message', { detail: obj }))
  return
}
```

### 5.3 创建 AI 聊天 API

**api/ai/chat.ts**

```typescript
import request from '@/utils/request'

/** 发送 AI 消息 */
export function sendAiMessage(data: { sessionId?: number; content: string; model?: string }) {
  return request({
    url: '/ai/chat/send',
    method: 'post',
    data
  })
}

/** 查询聊天历史 */
export function getChatHistory(sessionId: number) {
  return request({
    url: `/ai/chat/history/${sessionId}`,
    method: 'get'
  })
}

/** 创建新会话 */
export function createChatSession(data: { title?: string; modelName?: string }) {
  return request({
    url: '/ai/chat/session',
    method: 'post',
    data
  })
}
```

### 5.4 创建 AI 聊天抽屉组件

**components/ai/AiChatDrawer.vue**

```vue
<template>
  <el-drawer v-model="visible" title="NexusERP 智能助手" direction="rtl" size="450px" :before-close="handleClose">
    <div class="ai-chat-container">
      <!-- 消息列表 -->
      <div ref="messageListRef" class="message-list">
        <div v-for="(msg, index) in messages" :key="index" :class="['message-item', msg.role === 'user' ? 'user-message' : 'ai-message']">
          <div class="message-content">
            <div v-if="msg.role === 'assistant'" v-html="renderMarkdown(msg.content)"></div>
            <div v-else>{{ msg.content }}</div>
          </div>
        </div>
        <div v-if="thinking" class="message-item ai-message">
          <div class="message-content thinking">
            <el-icon class="is-loading"><Loading /></el-icon>
            <span>AI 正在思考...</span>
          </div>
        </div>
      </div>

      <!-- 输入框 -->
      <div class="input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          placeholder="输入问题，如：查询本月采购订单总金额"
          @keydown.enter.exact.prevent="sendMessage"
          :disabled="loading"
        />
        <el-button type="primary" :icon="Promotion" @click="sendMessage" :loading="loading" style="margin-top: 8px; width: 100%">
          发送
        </el-button>
      </div>
    </div>
  </el-drawer>
</template>

<script setup lang="ts">
import { ref, onMounted, onUnmounted, nextTick } from 'vue'
import { Promotion, Loading } from '@element-plus/icons-vue'
import { sendAiMessage } from '@/api/ai/chat'
import { marked } from 'marked'
import 'github-markdown-css'

interface ChatMessage {
  role: 'user' | 'assistant'
  content: string
}

const visible = ref(false)
const inputText = ref('')
const messages = ref<ChatMessage[]>([])
const thinking = ref(false)
const loading = ref(false)
const messageListRef = ref<HTMLElement>()

// SSE 消息监听
function onAiSseMessage(event: Event) {
  const detail = (event as CustomEvent).detail
  if (!detail) return

  switch (detail.type) {
    case 'AI_THINKING':
      thinking.value = true
      break
    case 'AI_CONTENT':
      thinking.value = false
      loading.value = false
      // 追加到当前 AI 消息
      const lastMsg = messages.value[messages.value.length - 1]
      if (lastMsg && lastMsg.role === 'assistant') {
        lastMsg.content = detail.content
      } else {
        messages.value.push({ role: 'assistant', content: detail.content })
      }
      nextTick(() => scrollToBottom())
      break
    case 'AI_DONE':
      thinking.value = false
      loading.value = false
      break
    case 'AI_ERROR':
      thinking.value = false
      loading.value = false
      messages.value.push({ role: 'assistant', content: `[错误] ${detail.content}` })
      break
    case 'AI_CHART':
      // Phase 1 再实现 ECharts 渲染
      break
  }
}

function renderMarkdown(content: string) {
  return marked.parse(content || '')
}

function scrollToBottom() {
  if (messageListRef.value) {
    messageListRef.value.scrollTop = messageListRef.value.scrollHeight
  }
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || loading.value) return

  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  loading.value = true

  await nextTick()
  scrollToBottom()

  try {
    await sendAiMessage({ content: text })
  } catch (e: any) {
    loading.value = false
    thinking.value = false
    messages.value.push({ role: 'assistant', content: `[请求失败] ${e.message}` })
  }
}

function handleClose() {
  visible.value = false
}

function open() {
  visible.value = true
}

defineExpose({ open })

onMounted(() => {
  window.addEventListener('ai-sse-message', onAiSseMessage)
})

onUnmounted(() => {
  window.removeEventListener('ai-sse-message', onAiSseMessage)
})
</script>

<style scoped lang="scss">
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  padding: 0 16px;
}

.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 16px 0;
}

.message-item {
  margin-bottom: 16px;
  display: flex;

  &.user-message {
    justify-content: flex-end;

    .message-content {
      background-color: #409eff;
      color: white;
      border-radius: 12px 12px 0 12px;
      max-width: 80%;
    }
  }

  &.ai-message {
    justify-content: flex-start;

    .message-content {
      background-color: #f4f4f5;
      color: #303133;
      border-radius: 12px 12px 12px 0;
      max-width: 85%;
    }
  }
}

.message-content {
  padding: 10px 14px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;

  &.thinking {
    display: flex;
    align-items: center;
    gap: 8px;
    color: #909399;
  }

  :deep(.markdown-body) {
    background: transparent;
    font-size: 14px;

    table {
      border-collapse: collapse;
      width: 100%;

      th, td {
        border: 1px solid #dcdfe6;
        padding: 6px 10px;
        text-align: left;
      }

      th {
        background-color: #f5f7fa;
      }
    }
  }
}

.input-area {
  padding: 12px 0;
  border-top: 1px solid #e4e7ed;
}
</style>
```

### 5.5 在布局中添加 AI 入口

编辑 `E:\Nexus\nexus-erp\plus-ui\src\layout\components\Navbar.vue`（或对应的导航栏组件），在工具栏区域添加 AI 助手按钮：

```vue
<!-- AI 助手入口 -->
<el-tooltip content="AI 智能助手" placement="bottom">
  <el-button :icon="ChatDotRound" circle @click="openAiChat" />
</el-tooltip>
```

在 script 中：

```typescript
import { ChatDotRound } from '@element-plus/icons-vue'
import AiChatDrawer from '@/components/ai/AiChatDrawer.vue'

const aiChatRef = ref()

function openAiChat() {
  aiChatRef.value?.open()
}
```

在 template 中添加组件：

```vue
<AiChatDrawer ref="aiChatRef" />
```

### 5.6 验证完整闭环

1. 启动后端（`RuoYiApplication`）
2. 启动前端（`pnpm dev`）
3. 登录系统
4. 点击导航栏 AI 助手图标
5. 在聊天框输入："查询本月所有采购订单的总金额"
6. 观察：
   - 显示"AI 正在思考..."
   - AI 调用 queryAllTables → queryTableSchema → executeSqlQuery
   - 返回查询结果（Markdown 表格格式）

---

## 验证清单

| 序号 | 验证项 | 预期结果 |
|------|--------|----------|
| 1 | Maven 编译 | `mvn compile` 无报错 |
| 2 | 应用启动 | `RuoYiApplication` 正常启动，无 Bean 注入异常 |
| 3 | SSE 连接 | `curl -N /resource/sse` 返回 `connected` 事件 |
| 4 | 简单对话 | 发送"你好" → SSE 收到 AI_CONTENT + AI_DONE |
| 5 | Text2SQL | 发送"查询采购订单" → AI 查表结构 → 生成 SQL → 返回结果 |
| 6 | 安全校验 | AI 生成 `DROP TABLE` → 被 ExecuteSqlQueryTool 拦截 |
| 7 | 租户隔离 | SQL 自动注入 `tenant_id` 条件 |
| 8 | 前端聊天 | 点击 AI 图标 → 打开抽屉 → 输入问题 → 显示 AI 回复 |
| 9 | 错误处理 | API Key 错误 → SSE 收到 AI_ERROR |

---

## 常见问题排查

### Q1：启动报 `No qualifying bean of type 'JdbcTemplate'`

**原因**：`ErpQueryService` 需要 JdbcTemplate，但 ruoyi-ai 模块未引入 spring-jdbc 依赖。

**解决**：在 `ruoyi-ai/pom.xml` 中添加：

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

### Q2：启动报 `Invalid value '1.13.0-beta23' for version`

**原因**：Maven 版本属性中 beta 后缀可能被误解析。

**解决**：确保 `<langchain4j.community.version>1.13.0-beta23</langchain4j.community.version>` 在根 pom.xml 的 `<properties>` 中正确声明。

### Q3：SSE 连接建立后收不到 AI 消息

**排查步骤**：
1. 确认 `sse.enabled=true` 在 `application.yml` 中
2. 确认 SSE 连接 URL 包含正确的 `Authorization` 和 `clientid` 参数
3. 检查后端日志是否有 `发送 AI SSE 消息失败` 错误
4. 确认 Redis 正常运行（`publishMessage` 依赖 Redis Pub/Sub）

### Q4：AI 回复"未找到可用的 AI 模型"

**原因**：`ai_model` 表中没有数据或 API Key 未配置。

**解决**：
1. 检查 `ai_provider` 和 `ai_model` 表是否有数据
2. 检查 API Key 是否正确（如果使用 Jasypt 加密，确认加密密码正确）
3. 确认 `ai_provider.status = '1'` 和 `ai_model.status = '1'`

### Q5：ExecuteSqlQueryTool 报"Table doesn't exist"

**原因**：数据源指向了 master（ry-vue）而非 erp（ry-erp）。

**解决**：确认 `ErpQueryService` 上有 `@DS("erp")` 注解，且 `application-dev.yml` 中配置了 `erp` 数据源。

### Q6：前端 vue-element-plus-x 组件渲染异常

**原因**：vue-element-plus-x 1.3.0 基于 Element Plus 2.10.x，与 2.13.5 可能不兼容。

**解决**：
1. 先尝试正常使用，部分组件可能兼容
2. 如不兼容，可临时降级 Element Plus：`npm install element-plus@2.10.0`
3. 或等 vue-element-plus-x 发布兼容 2.13.x 的新版本

package com.nexus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 配置属性
 * <p>前缀 nexus.ai，避免与 Spring AI 的 spring.ai 冲突</p>
 * <p>包含默认厂商/模型、SQL 安全配置、向量存储（Milvus/InMemory）配置</p>
 *
 * @author NexusERP
 * @since 1.0.0
 */
@Data
@Component
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

    /** 向量存储配置 */
    private VectorStoreConfig vectorStore = new VectorStoreConfig();

    @Data
    public static class VectorStoreConfig {
        /** 向量存储类型：milvus / inmemory */
        private String type = "inmemory";

        /** Milvus 配置 */
        private MilvusConfig milvus = new MilvusConfig();

        /** InMemory 配置 */
        private InMemoryConfig inmemory = new InMemoryConfig();
    }

    @Data
    public static class MilvusConfig {
        /** Milvus 连接地址 */
        private String url = "http://localhost:19530";

        /** 集合名称前缀 */
        private String collectionName = "AiKnowledge";

        /** 向量维度（根据 Embedding 模型自动推断，此处为默认值） */
        private int dimension = 1024;
    }

    @Data
    public static class InMemoryConfig {
        /** 是否启用 InMemory 模式（开发/测试用） */
        private boolean enabled = true;
    }
}

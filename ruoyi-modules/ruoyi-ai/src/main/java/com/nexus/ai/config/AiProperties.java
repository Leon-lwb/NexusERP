package com.nexus.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * AI 配置属性
 * <p>前缀 nexus.ai，避免与 Spring AI 的 spring.ai 冲突</p>
 *
 * @author NexusERP
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
}

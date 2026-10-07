package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 厂商配置实体
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_provider")
public class AiProvider extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 厂商名称 */
    private String name;

    /** API 地址 */
    private String apiHost;

    /** API Key（Jasypt 加密存储） */
    private String apiKey;

    /** 厂商类型: OPENAI/DASHSCOPE/ZHIPU/OLLAMA */
    private String providerType;

    /** 状态（0启用 1停用，字典 sys_normal_disable） */
    private String status;
}

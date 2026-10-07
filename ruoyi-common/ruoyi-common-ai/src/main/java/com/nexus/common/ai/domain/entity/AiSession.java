package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 对话会话实体
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_session")
public class AiSession extends BaseEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 租户编号（文本身份，必须保留前导零） */
    private String tenantId;

    /** 用户ID */
    private Long userId;

    /** 会话标题 */
    private String title;

    /** 使用的模型名称 */
    private String modelName;

    /** 会话启用的工具ID列表（逗号分隔，null=使用全部已启用工具） */
    private String enabledToolIds;
}

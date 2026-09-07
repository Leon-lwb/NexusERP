package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 运行参数配置实体
 * <p>对应 ai_config 表，按分组存储 nexus.ai 运行参数（SQL 安全/向量存储/联网搜索/流程编排/知识图谱），
 * 替代 application.yml 中同名键的运行时可变性 —— yml 仅作首次启动兜底默认，入库后以表为准</p>
 * <p>全局配置（不入多租户插件，表已加入 tenant.excludes），config_group 为主键语义</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_config")
public class AiConfig extends BaseEntity {

    /**
     * 配置分组（主键语义）：sql_safety / vector_store / web_search / flow / knowledge_graph
     */
    @TableId(value = "config_group")
    private String configGroup;

    /** 配置值（分组内字段的 JSON 序列化） */
    private String configValue;

    /** 备注 */
    private String remark;
}

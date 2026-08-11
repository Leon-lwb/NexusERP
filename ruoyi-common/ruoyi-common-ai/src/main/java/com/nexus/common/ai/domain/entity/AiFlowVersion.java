package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 流程版本快照实体 ai_flow_version
 * <p>发布时落一行快照（config_json + 版本号 + 流程名），供版本列表/恢复/只读预览</p>
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_flow_version")
public class AiFlowVersion extends TenantEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 流程ID */
    private Long flowId;

    /** 发布后的流程版本号 */
    private Integer flowVersion;

    /** 流程名称(发布时快照) */
    private String flowName;

    /** 画布配置快照(JSON: nodes+edges) */
    private String configJson;

    /** 发布说明 */
    private String description;
}

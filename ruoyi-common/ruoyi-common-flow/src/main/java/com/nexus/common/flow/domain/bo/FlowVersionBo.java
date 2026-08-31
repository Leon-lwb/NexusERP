package com.nexus.common.flow.domain.bo;

import com.nexus.common.flow.domain.entity.FlowVersion;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * AI 流程版本快照业务对象 ai_flow_version
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = FlowVersion.class, reverseConvertGenerate = false)
public class FlowVersionBo extends BaseEntity {

    /** 主键 */
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

    /** 批量删除ID集合 */
    private Long[] ids;
}

package com.nexus.common.ai.domain.bo;

import com.nexus.common.ai.domain.entity.AiFlowInstance;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 流程运行实例业务对象 ai_flow_instance
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = AiFlowInstance.class, reverseConvertGenerate = false)
public class AiFlowInstanceBo extends BaseEntity {

    /** 主键 */
    private Long id;

    /** 流程ID */
    private Long flowId;

    /** 运行时的流程版本 */
    private Integer flowVersion;

    /** 触发方式: MANUAL/SCHEDULE */
    private String triggerType;

    /** 触发人(定时任务=流程创建人, 用于SSE通知) */
    private Long triggerBy;

    /** 输入(JSON) */
    private String inputJson;

    /** 输出(JSON, 末节点输出) */
    private String outputJson;

    /** 状态: RUNNING/SUCCEEDED/FAILED/STOPPED */
    private String status;

    /** 错误信息 */
    private String errorMsg;

    /** 总耗时(毫秒) */
    private Long durationMs;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 批量删除ID集合 */
    private Long[] ids;
}

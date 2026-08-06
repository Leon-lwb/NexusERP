package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 流程运行实例实体 ai_flow_instance
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_flow_instance")
public class AiFlowInstance extends TenantEntity {

    /** 主键 */
    @TableId(value = "id")
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

    /** 节点总数 */
    private Integer nodeTotal;

    /** 成功节点数 */
    private Integer nodeSuccess;

    /** 失败节点数 */
    private Integer nodeFailed;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;
}

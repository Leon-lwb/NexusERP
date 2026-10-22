package com.nexus.common.flow.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * AI 流程节点运行记录实体 ai_flow_node_run
 *
 * @author NexusERP
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_flow_node_run")
public class FlowNodeRun extends TenantEntity {

    /** 主键 */
    @TableId(value = "id")
    private Long id;

    /** 实例ID */
    private Long instanceId;

    /** 流程ID(冗余, 便于查询) */
    private Long flowId;

    /** 画布节点ID(如 node_1) */
    private String nodeId;

    /** 节点名称 */
    private String nodeName;

    /** 节点类型: LLM/AGENT/RAG/IF/CODE */
    private String nodeType;

    /** 状态: PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED/CANCELLED/RECOVERY_REQUIRED */
    private String status;

    /** 输入(JSON, 渲染后的最终入参) */
    private String inputJson;

    /** 输出(JSON) */
    private String outputJson;

    /** 错误信息 */
    private String errorMsg;

    /** 已重试次数 */
    private Integer retryCount;

    /** 循环批次索引（split 循环场景同一节点多次执行的轮次，非循环=0） */
    private Integer batchIndex;

    /** 耗时(毫秒) */
    private Long durationMs;

    /** 开始时间 */
    private Date startTime;

    /** 结束时间 */
    private Date endTime;
}

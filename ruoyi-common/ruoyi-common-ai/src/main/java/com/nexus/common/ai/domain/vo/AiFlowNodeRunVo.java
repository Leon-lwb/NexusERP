package com.nexus.common.ai.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.ai.domain.entity.AiFlowNodeRun;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 流程节点运行记录视图对象 ai_flow_node_run
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = AiFlowNodeRun.class)
public class AiFlowNodeRunVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 租户编号 */
    private Long tenantId;

    /** 实例ID */
    @ExcelProperty(value = "实例ID")
    private Long instanceId;

    /** 流程ID(冗余, 便于查询) */
    private Long flowId;

    /** 画布节点ID(如 node_1) */
    @ExcelProperty(value = "节点ID")
    private String nodeId;

    /** 节点名称 */
    @ExcelProperty(value = "节点名称")
    private String nodeName;

    /** 节点类型: LLM/AGENT/RAG/IF/CODE */
    @ExcelProperty(value = "节点类型")
    private String nodeType;

    /** 状态: PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED */
    @ExcelProperty(value = "状态")
    private String status;

    /** 输入(JSON, 渲染后的最终入参) */
    private String inputJson;

    /** 输出(JSON) */
    private String outputJson;

    /** 错误信息 */
    private String errorMsg;

    /** 已重试次数 */
    private Integer retryCount;

    /** 循环批次索引（split 循环轮次，非循环=0） */
    private Integer batchIndex;

    /** 耗时(毫秒) */
    @ExcelProperty(value = "耗时(ms)")
    private Long durationMs;

    /** 开始时间 */
    @ExcelProperty(value = "开始时间")
    private Date startTime;

    /** 结束时间 */
    private Date endTime;
}

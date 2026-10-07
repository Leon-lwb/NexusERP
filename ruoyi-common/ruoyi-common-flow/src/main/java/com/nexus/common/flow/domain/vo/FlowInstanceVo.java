package com.nexus.common.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.flow.domain.entity.FlowInstance;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 流程运行实例视图对象 ai_flow_instance
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FlowInstance.class)
public class FlowInstanceVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "实例ID")
    private Long id;

    /** 租户编号（文本身份，保留前导零；历史0不自动升级） */
    private String tenantId;

    /** 流程ID */
    @ExcelProperty(value = "流程ID")
    private Long flowId;

    /** 流程名称（冗余, 来自 ai_flow 关联查询） */
    @ExcelProperty(value = "流程名称")
    private String flowName;

    /** 运行时的流程版本 */
    private Integer flowVersion;

    /** 启动时配置快照（JSON，供历史重放；列表接口不返回，仅详情） */
    private String configSnapshot;

    /** 触发方式: MANUAL/SCHEDULE */
    @ExcelProperty(value = "触发方式")
    private String triggerType;

    /** 触发人(定时任务=流程创建人, 用于SSE通知) */
    private Long triggerBy;

    /** 输入(JSON) */
    private String inputJson;

    /** 输出(JSON, 末节点输出) */
    private String outputJson;

    /** 状态: RUNNING/SUCCEEDED/FAILED/STOPPED */
    @ExcelProperty(value = "状态")
    private String status;

    /** 错误信息 */
    private String errorMsg;

    /** 总耗时(毫秒) */
    @ExcelProperty(value = "总耗时(ms)")
    private Long durationMs;

    /** 节点总数 */
    private Integer nodeTotal;

    /** 成功节点数 */
    private Integer nodeSuccess;

    /** 失败节点数 */
    private Integer nodeFailed;

    /** 开始时间 */
    @ExcelProperty(value = "开始时间")
    private Date startTime;

    /** 结束时间 */
    private Date endTime;

    /** 创建时间 */
    private Date createTime;
}

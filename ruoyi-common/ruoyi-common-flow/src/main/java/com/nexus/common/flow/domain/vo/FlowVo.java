package com.nexus.common.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.flow.domain.entity.Flow;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 流程定义视图对象 ai_flow
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = Flow.class)
public class FlowVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "流程ID")
    private Long id;

    /** 租户编号（文本身份，保留前导零；历史0不自动升级） */
    private String tenantId;

    /** 流程名称 */
    @ExcelProperty(value = "流程名称")
    private String flowName;

    /** 流程标识(唯一, 定时/Webhook 引用) */
    @ExcelProperty(value = "流程标识")
    private String flowKey;

    /** 描述 */
    private String description;

    /** 状态: DRAFT/PUBLISHED/OFFLINE */
    @ExcelProperty(value = "状态")
    private String status;

    /** 版本号(每次发布+1) */
    @ExcelProperty(value = "版本号")
    private Integer version;

    /** 画布配置(JSON: nodes+edges) */
    private String configJson;

    /** 触发方式: MANUAL/SCHEDULE (逗号分隔可多选) */
    @ExcelProperty(value = "触发方式")
    private String triggerType;

    /** 定时 Cron 表达式 */
    private String cronExpr;

    /** 节点失败最大重试次数 */
    private Integer maxRetry;

    /** 创建时间 */
    private Date createTime;

    /** 创建者 */
    private Long createBy;

    /** 更新时间 */
    private Date updateTime;
}

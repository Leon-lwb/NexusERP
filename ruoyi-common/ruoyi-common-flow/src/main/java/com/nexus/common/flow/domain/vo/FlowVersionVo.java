package com.nexus.common.flow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.nexus.common.flow.domain.entity.FlowVersion;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * AI 流程版本快照视图对象 ai_flow_version
 *
 * @author NexusERP
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = FlowVersion.class)
public class FlowVersionVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 主键 */
    @ExcelProperty(value = "版本快照ID")
    private Long id;

    /** 租户编号（文本身份，保留前导零；历史0不自动升级） */
    private String tenantId;

    /** 流程ID */
    private Long flowId;

    /** 发布后的流程版本号 */
    @ExcelProperty(value = "版本号")
    private Integer flowVersion;

    /** 流程名称(发布时快照) */
    @ExcelProperty(value = "流程名称")
    private String flowName;

    /** 画布配置快照(JSON: nodes+edges) */
    private String configJson;

    /** 发布说明 */
    private String description;

    /** 创建时间（=发布时间） */
    @ExcelProperty(value = "发布时间")
    private Date createTime;

    /** 创建者（=发布人） */
    private Long createBy;
}

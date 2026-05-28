package com.nexus.workflow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;


/**
 * 钉钉流程视图对象 wf_dd_flow
 *
 * @author wanghuan
 * @date 2023-11-29
 */
@Data
@ExcelIgnoreUnannotated
public class WfDdFlowVo {

    private static final long serialVersionUID = 1L;

    /**
     * 钉钉流程主键
     */
    @ExcelProperty(value = "钉钉流程主键")
    private Long id;

    /**
     * 流程名称
     */
    @ExcelProperty(value = "流程名称")
    private String name;

    /**
     * 流程JSON数据
     */
    @ExcelProperty(value = "流程JSON数据")
    private String flowData;


}

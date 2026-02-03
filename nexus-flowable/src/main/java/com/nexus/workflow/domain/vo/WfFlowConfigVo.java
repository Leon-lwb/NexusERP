package com.nexus.workflow.domain.vo;

import com.alibaba.excel.annotation.ExcelIgnoreUnannotated;
import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;



/**
 * 流程配置主视图对象 wf_flow_config
 *
 * @author nbacheng
 * @date 2023-11-19
 */
@Data
@ExcelIgnoreUnannotated
public class WfFlowConfigVo {

    private static final long serialVersionUID = 1L;

    /**
     * 流程配置主表主键
     */
    @ExcelProperty(value = "流程配置主表主键")
    private Long id;

    /**
     * 流程模型ID
     */
    @ExcelProperty(value = "流程模型ID")
    private String modelId;

    /**
     * 节点Key
     */
    @ExcelProperty(value = "节点Key")
    private String nodeKey;

    /**
     * 节点名称
     */
    @ExcelProperty(value = "节点名称")
    private String nodeName;

    /**
     * 表单Key
     */
    @ExcelProperty(value = "表单Key")
    private String formKey;

    /**
     * 应用类型
     */
    @ExcelProperty(value = "应用类型")
    private String appType;


}

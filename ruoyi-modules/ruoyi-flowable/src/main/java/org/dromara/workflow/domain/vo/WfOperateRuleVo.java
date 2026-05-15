package org.dromara.workflow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;



/**
 * 流程操作规则视图对象 wf_operate_rule
 *
 * @author wanghuan
 * @date 2023-11-23
 */
@Data
@ExcelIgnoreUnannotated
public class WfOperateRuleVo {

    private static final long serialVersionUID = 1L;

    /**
     * 流程操作主键
     */
    @ExcelProperty(value = "流程操作主键")
    private Long id;

    /**
     * 流程配置主表ID
     */
    @ExcelProperty(value = "流程配置主表ID")
    private Long configId;

    /**
     * 操作类型
     */
    @ExcelProperty(value = "操作类型")
    private String opeType;

    /**
     * 操作名称
     */
    @ExcelProperty(value = "操作名称")
    private String opeName;

    /**
     * 是否启用1-启用0-关闭默认
     */
    @ExcelProperty(value = "是否启用1-启用0-关闭默认")
    private String isEnable;

    /**
     * 排序
     */
    @ExcelProperty(value = "排序")
    private Long sort;


}

package org.dromara.workflow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.workflow.domain.WfCustomForm;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;



/**
 * 流程业务单视图对象 wf_custom_form
 *
 * @author nbacheng
 * @date 2023-10-09
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WfCustomForm.class)
public class WfCustomFormVo {

    private static final long serialVersionUID = 1L;

    /**
     * 主键
     */
    @ExcelProperty(value = "主键")
    private Long id;

    /**
     * 业务表单名称
     */
    @ExcelProperty(value = "业务表单名称")
    private String businessName;

    /**
     * 业务服务名称
     */
    @ExcelProperty(value = "业务服务名称")
    private String businessService;

    /**
     * 
     */
    @ExcelProperty(value = "")
    private String flowName;

    /**
     * 关联流程发布主键
     */
    @ExcelProperty(value = "关联流程发布主键")
    private String deployId;

    /**
     * 前端路由地址
     */
    @ExcelProperty(value = "前端路由地址")
    private String routeName;

    /**
     * 组件注入方法
     */
    @ExcelProperty(value = "组件注入方法")
    private String component;


    /**
     * 关联业务主表主键
     */
    @ExcelProperty(value = "关联业务主表主键主键")
    private Long tableId;
}

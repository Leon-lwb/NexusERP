package org.dromara.workflow.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import org.dromara.workflow.domain.WfNform;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;


/**
 * 设计器单视图对象 wf_nform
 *
 * @author wanghuan
 * @date 2025-04-29
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WfNform.class)
public class WfNformVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

            /**
             * 表单ID
             */
            @ExcelProperty(value = "表单ID" )
        private Long formId;

            /**
             * 表单名称
             */
            @ExcelProperty(value = "表单名称" )
        private String formName;

            /**
             * 表单描述
             */
            @ExcelProperty(value = "表单描述" )
        private String formDesc;

            /**
             * 表单内容
             */
            @ExcelProperty(value = "表单内容" )
        private String formContent;


}

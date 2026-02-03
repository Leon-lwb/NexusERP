package com.nexus.workflow.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 流程自定义业务规则业务对象 wf_custom_rule
 *
 * @author nbacheng
 * @date 2023-11-23
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class WfCustomRuleBo extends BaseEntity {

    /**
     * 业务规则主键
     */
    @NotNull(message = "业务规则主键不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 流程配置主表ID
     */
    @NotNull(message = "流程配置主表ID不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long configId;

    /**
     * 字段编码
     */
    @NotBlank(message = "字段编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String colCode;

    /**
     * 字段名称
     */
    @NotBlank(message = "字段名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String colName;

    /**
     * java类型
     */
    @NotBlank(message = "java类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String javaType;

    /**
     * java字段名
     */
    @NotBlank(message = "java字段名不能为空", groups = { AddGroup.class, EditGroup.class })
    private String javaField;

    /**
     * 属性0-隐藏1-只读默认2-可编辑
     */
    @NotBlank(message = "属性0-隐藏1-只读默认2-可编辑不能为空", groups = { AddGroup.class, EditGroup.class })
    private String attribute;

    /**
     * 排序
     */
    @NotNull(message = "排序不能为空", groups = { AddGroup.class, EditGroup.class })
    private Long sort;


}

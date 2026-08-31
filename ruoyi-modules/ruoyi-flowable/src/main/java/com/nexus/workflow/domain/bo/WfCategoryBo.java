package com.nexus.workflow.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.workflow.domain.WfCategory;

import java.io.Serial;

/**
 * 流程分类业务对象 wf_category
 *
 * @author: Baymax
 * @date: 2023/6/19 21:19
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WfCategory.class, reverseConvertGenerate = false)
public class WfCategoryBo extends BaseEntity {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 分类ID
     */
    @NotNull(message = "分类id不能为空", groups = { EditGroup.class })
    private Long categoryId;

    /**
     * 分类名称
     */
    @NotBlank(message = "分类名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String categoryName;

    /**
     * 分类编码
     */
    @NotBlank(message = "分类编码不能为空", groups = { AddGroup.class, EditGroup.class })
    private String code;

    /**
     * 应用类型
     */
    @NotBlank(message = "应用类型不能为空", groups = { AddGroup.class, EditGroup.class })
    private String appType;

    /**
     * 备注
     */
    private String remark;
    /**
     * 租户编号
     */
    private String tenantId;

}

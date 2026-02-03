package com.nexus.workflow.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.core.domain.BaseEntity;
import com.nexus.workflow.domain.WfNform;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 设计器单业务对象 wf_nform
 *
 * @author nbacheng
 * @date 2025-04-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WfNform.class, reverseConvertGenerate = false)
public class WfNformBo extends BaseEntity {

    /**
     * 表单ID
     */
    @NotNull(message = "表单ID不能为空", groups = { EditGroup.class })
    private Long formId;

    /**
     * 表单名称
     */
            @NotBlank(message = "表单名称不能为空", groups = { AddGroup.class, EditGroup.class })
        private String formName;
    /**
     * 表单描述
     */
            @NotBlank(message = "表单描述不能为空", groups = { AddGroup.class, EditGroup.class })
        private String formDesc;
    /**
     * 表单内容
     */
            @NotBlank(message = "表单内容不能为空", groups = { AddGroup.class, EditGroup.class })
        private String formContent;
}

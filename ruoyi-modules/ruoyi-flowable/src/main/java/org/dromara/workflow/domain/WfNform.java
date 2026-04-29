package org.dromara.workflow.domain;

import org.dromara.common.tenant.core.TenantEntity;
import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * 设计器单对象 wf_nform
 *
 * @author nbacheng
 * @date 2025-04-29
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("wf_nform" )
public class WfNform extends TenantEntity {

@Serial
private static final long serialVersionUID=1L;

    /**
     * 表单ID
     */
        @TableId(value = "form_id" )
    private Long formId;

    /**
     * 表单名称
     */
    private String formName;

    /**
     * 表单描述
     */
    private String formDesc;

    /**
     * 表单内容
     */
    private String formContent;

    /**
     * 删除标志（0代表存在 2代表删除）
     */
        @TableLogic
    private String delFlag;


}

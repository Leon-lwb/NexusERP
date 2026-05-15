package org.dromara.workflow.domain.bo;

import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * 钉钉流程业务对象 wf_dd_flow
 *
 * @author wanghuan
 * @date 2023-11-29
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class WfDdFlowBo extends BaseEntity {

    /**
     * 钉钉流程主键
     */
    @NotNull(message = "钉钉流程主键不能为空", groups = { EditGroup.class })
    private Long id;

    /**
     * 流程名称
     */
    @NotBlank(message = "流程名称不能为空", groups = { AddGroup.class, EditGroup.class })
    private String name;

    /**
     * 流程JSON数据
     */
    private String flowData;


}

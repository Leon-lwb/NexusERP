package org.dromara.workflow.domain.bo;

import org.dromara.common.core.validate.AddGroup;
import org.dromara.common.core.validate.EditGroup;
import org.dromara.common.mybatis.core.domain.BaseEntity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;


/**
 * DEMO业务对象 wf_demo
 *
 * @author wanghuan
 * @date 2023-10-12
 */

@Data
@EqualsAndHashCode(callSuper = true)
public class WfDemoBo extends BaseEntity {

    /**
     * DEMO-ID
     */
    @NotNull(message = "DEMO-ID不能为空", groups = { EditGroup.class })
    private Long demoId;

    /**
     * 用户账号
     */
    @NotBlank(message = "用户账号不能为空", groups = { AddGroup.class, EditGroup.class })
    private String userName;

    /**
     * 用户昵称
     */
    private String nickName;

    /**
     * 用户邮箱
     */
    private String email;

    /**
     * 头像地址
     */
    private String avatar;

    /**
     * 帐号状态（0正常 1停用）
     */
    private String status;

    /**
     * 备注
     */
    private String remark;


}

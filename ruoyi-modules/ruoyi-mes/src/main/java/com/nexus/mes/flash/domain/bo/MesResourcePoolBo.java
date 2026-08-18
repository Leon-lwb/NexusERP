package com.nexus.mes.flash.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class MesResourcePoolBo extends BaseEntity {

    private Long poolId;
    private String poolCode;

    @NotBlank(message = "资源池名称不能为空", groups = {AddGroup.class})
    private String poolName;

    @NotBlank(message = "资源类型不能为空", groups = {AddGroup.class})
    private String resourceType;

    /** INTERNAL/EXTERNAL */
    private String source;
    private String externalConfig;
    private String allocStrategy;
    private String status;
    private String remark;
}

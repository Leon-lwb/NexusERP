package com.nexus.mes.lt.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.lt.domain.LtSnBinding;

/**
 * MES SN批次绑定业务对象 lt_sn_binding
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = LtSnBinding.class, reverseConvertGenerate = false)
public class LtSnBindingBo extends BaseEntity {

    /** 绑定ID */
    private Long bindingId;

    /** SN ID */
    @NotNull(message = "SN ID不能为空")
    private Long snId;

    /** 批次ID */
    @NotNull(message = "批次ID不能为空")
    private Long lotId;

    /** 物料ID */
    @NotNull(message = "物料ID不能为空")
    private Long materialId;

    /** 批量删除ID集合 */
    private Long[] ids;
}

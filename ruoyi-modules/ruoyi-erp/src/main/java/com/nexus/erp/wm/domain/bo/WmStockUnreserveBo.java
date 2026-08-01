package com.nexus.erp.wm.domain.bo;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;

/**
 * 库存预留释放业务对象（释放预留请求参数）
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class WmStockUnreserveBo extends BaseEntity {

    /**
     * 来源单据类型（SALES_ORDER / WORK_ORDER 等）
     */
    @NotBlank(message = "来源单据类型不能为空")
    private String sourceDocType;

    /**
     * 来源单据ID
     */
    @NotNull(message = "来源单据ID不能为空")
    private Long sourceDocId;

    /**
     * 来源单据行ID（可为空，为空时释放该单据全部预留）
     */
    private Long sourceDocLineId;
}

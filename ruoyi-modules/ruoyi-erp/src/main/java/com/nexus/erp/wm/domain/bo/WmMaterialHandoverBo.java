package com.nexus.erp.wm.domain.bo;

import com.nexus.erp.wm.domain.WmMaterialHandoverLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 上料交接单建单入参 (PC 端; PDA 核销+签收执行)
 */
@Data
public class WmMaterialHandoverBo {

    /** 来源工单ID(prd_work_order, 可空) */
    private Long woId;

    /** 来源工单号(可空) */
    private String woNo;

    /** 源仓库ID */
    private Long warehouseId;

    /** 源仓库编码 */
    @NotBlank(message = "仓库编码不能为空")
    private String warehouseCode;

    /** 源仓库名称 */
    private String warehouseName;

    /** 备注 */
    private String remark;

    /** 行明细 */
    @Valid
    @NotEmpty(message = "行明细不能为空")
    private List<WmMaterialHandoverLine> lines;
}

package com.nexus.erp.wm.domain.bo;

import com.nexus.erp.wm.domain.WmMaterialReturnLine;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 生产退料单建单入参 (PC 端; PDA 只核销执行)
 */
@Data
public class WmMaterialReturnBo {

    /** 仓库ID */
    private Long warehouseId;

    /** 仓库编码 */
    @NotBlank(message = "仓库编码不能为空")
    private String warehouseCode;

    /** 仓库名称 */
    private String warehouseName;

    /** 退料类型 GOOD/DEFECT/SCRAP */
    @NotBlank(message = "退料类型不能为空")
    private String returnType;

    /** 来源单号(工单/领料单等) */
    private String sourceDoc;

    /** 退料日期 */
    private Date returnDate;

    /** 备注 */
    private String remark;

    /** 行明细 */
    @Valid
    @NotEmpty(message = "行明细不能为空")
    private List<WmMaterialReturnLine> lines;
}

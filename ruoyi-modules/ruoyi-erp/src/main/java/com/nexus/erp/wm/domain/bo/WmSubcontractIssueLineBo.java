package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.wm.domain.WmSubcontractIssueLine;

import java.math.BigDecimal;

/**
 * 委外发料单行业务对象
 *
 * @author nexus
 */
@Data
@AutoMapper(target = WmSubcontractIssueLine.class, reverseConvertGenerate = false)
public class WmSubcontractIssueLineBo {

    /** 行ID */
    private Long lineId;
    /** BOM快照行ID(prd_subcontract_bom_line) */
    private Long bomLineId;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位名称 */
    private String baseUomName;
    /** 本次发料量 */
    private BigDecimal issueQty;
    /** 分配明细JSON([{stockId,batchId,batchCode,locationId,deductQty}]), 空=过账时自动FEFO/FIFO */
    private String allocJson;
    /** 备注 */
    private String remark;
}

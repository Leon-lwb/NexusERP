package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 委外发料单行对象
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_subcontract_issue_line")
public class WmSubcontractIssueLine extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /** 行ID */
    Long lineId;
    /** 发料单ID */
    Long issueId;
    /** 委外订单ID */
    Long subcontractId;
    /** 委外单号(冗余, 流水备注直达追溯) */
    String subcontractNo;
    /** BOM快照行ID(prd_subcontract_bom_line) */
    Long bomLineId;
    /** 物料ID */
    Long materialId;
    /** 物料编码 */
    String materialCode;
    /** 物料名称 */
    String materialName;
    /** 规格型号 */
    String materialSpec;
    /** 单位名称 */
    String baseUomName;
    /** 本次发料量 */
    BigDecimal issueQty;
    /** 分配明细JSON([{stockId,batchId,batchCode,locationId,deductQty}]) */
    String allocJson;
    /** 备注 */
    String remark;
}

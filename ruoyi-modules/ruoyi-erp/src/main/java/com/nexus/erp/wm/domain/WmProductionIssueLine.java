package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_production_issue_line")
public class WmProductionIssueLine extends TenantEntity {

    @TableId(value = "line_id")
    private Long lineId;

    private Long issueId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private Long batchId;
    private String batchCode;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private BigDecimal quantity;
    /** BOM标准用量（单位产品用量） */
    private BigDecimal bomQty;
    /** 损耗率(%) */
    private BigDecimal lossRate;
    /** 应领数量（BOM定额×工单数量） */
    private BigDecimal requiredQty;
    /** 已领数量（累计已发） */
    private BigDecimal issuedQty;
    /** 本次领料数量 */
    private BigDecimal currentQty;
    /** 欠领数量（应领-已领） */
    private BigDecimal shortQty;
    /** 物料需求明细ID（追溯工单BOM需求） */
    private Long requirementDetailId;
    /** 行状态(PENDING/PARTIAL/ISSUED) */
    private String lineStatus;
    private BigDecimal unitPrice;
    private BigDecimal amount;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableLogic
    private String delFlag;
}

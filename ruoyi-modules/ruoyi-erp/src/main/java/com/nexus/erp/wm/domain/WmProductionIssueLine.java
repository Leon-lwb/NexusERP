package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 生产领料单行对象 wm_production_issue_line
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_production_issue_line")
public class WmProductionIssueLine extends TenantEntity {

    /**
     * 行ID
     */
    @TableId(value = "line_id")
    private Long lineId;

    /**
     * 领料单ID
     */
    private Long issueId;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    private String warehouseCode;

    /**
     * 仓库名称
     */
    private String warehouseName;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 物料规格
     */
    private String materialSpec;

    /**
     * 基本单位ID
     */
    private String baseUomId;

    /**
     * 基本单位名称
     */
    private String baseUomName;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次编码
     */
    private String batchCode;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    private String locationCode;

    /**
     * 库位名称
     */
    private String locationName;

    /**
     * 数量
     */
    private BigDecimal quantity;

    /**
     * BOM标准用量（单位产品用量）
     */
    private BigDecimal bomQty;

    /**
     * 损耗率(%)
     */
    private BigDecimal lossRate;

    /**
     * 应领数量（BOM定额×工单数量）
     */
    private BigDecimal requiredQty;

    /**
     * 已领数量（累计已发）
     */
    private BigDecimal issuedQty;

    /**
     * 本次领料数量
     */
    private BigDecimal currentQty;

    /**
     * 欠领数量（应领-已领）
     */
    private BigDecimal shortQty;

    /**
     * 物料需求明细ID（追溯工单BOM需求）
     */
    private Long requirementDetailId;

    /**
     * 行状态(PENDING/PARTIAL/ISSUED/SHORT_CLOSED)
     */
    private String lineStatus;

    /**
     * 缺货原因(缺货关闭时填写)
     */
    private String shortageReason;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 预留字段1
     */
    private String attr1;

    /**
     * 预留字段2
     */
    private String attr2;

    /**
     * 预留字段3
     */
    private Long attr3;

    /**
     * 预留字段4
     */
    private Long attr4;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

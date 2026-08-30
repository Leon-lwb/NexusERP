package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 生产退料单行对象 wm_material_return_line (M2 批次2)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_return_line")
public class WmMaterialReturnLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 退料单ID */
    private Long returnId;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    private String materialCode;

    /** 物料名称 */
    private String materialName;

    /** 规格型号 */
    private String materialSpec;

    /** 基本单位 */
    private String baseUomId;

    /** 基本单位名称 */
    private String baseUomName;

    /** 批次ID */
    private Long batchId;

    /** 批次号 */
    private String batchCode;

    /** 目标库位ID */
    private Long locationId;

    /** 目标库位编码 */
    private String locationCode;

    /** 目标库位名称 */
    private String locationName;

    /** 计划退料数量 */
    private BigDecimal quantity;

    /** 实退数量(PDA核销累计) */
    private BigDecimal actualQty;

    /** 备注 */
    private String remark;
}

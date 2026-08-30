package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 上料交接单行对象 wm_material_handover_line (M3 批次2)
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_handover_line")
public class WmMaterialHandoverLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 交接单ID */
    private Long handoverId;

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

    /** 源库位ID(源仓库内) */
    private Long fromLocationId;

    /** 源库位编码 */
    private String fromLocationCode;

    /** 源库位名称 */
    private String fromLocationName;

    /** 线边库位ID */
    private Long toLocationId;

    /** 线边库位编码 */
    private String toLocationCode;

    /** 线边库位名称 */
    private String toLocationName;

    /** 计划交接数量 */
    private BigDecimal quantity;

    /** 实交数量(PDA核销累计) */
    private BigDecimal actualQty;

    /** 备注 */
    private String remark;
}

package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmMaterialStockTx;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存事务业务对象 wm_material_stock_tx
 *
 * @author nexus
 * @date 2025-12-12
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMaterialStockTx.class, reverseConvertGenerate = false)
public class WmMaterialStockTxBo extends BaseEntity {

    /**
     * 事务ID
     */
    private Long txId;

    /**
     * 事务类型
     */
    private String txType;

    /**
     * 产品物料ID
     */
    private Long materialId;

    /**
     * 产品物料编码
     */
    private String materialCode;

    /**
     * 产品物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 单位
     */
    private String baseUomId;

    /**
     * 单位名称
     */
    private String baseUomName;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次号
     */
    private String batchCode;

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
     * 库区ID
     */
    private Long areaId;

    /**
     * 库区编码
     */
    private String areaCode;

    /**
     * 库区名称
     */
    private String areaName;

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
     * 容器ID
     */
    private Long packageId;

    /**
     * 容器编号
     */
    private String packageCode;

    /**
     * 单据类型
     */
    private String sourceDocType;

    /**
     * 单据ID
     */
    private Long sourceDocId;

    /**
     * 单据编号
     */
    private String sourceDocCode;

    /**
     * 单据行ID
     */
    private Long sourceDocLineId;

    /**
     * 库存记录ID
     */
    private Long materialStockId;

    /**
     * 库存方向
     */
    private Integer txFlag;

    /**
     * 事务数量
     */
    private BigDecimal txQuantity;

    /**
     * 变动前数量
     */
    private BigDecimal beforeQuantity;

    /**
     * 变动后数量
     */
    private BigDecimal afterQuantity;

    /**
     * 事务日期
     */
    private Date txDate;

    /**
     * 关联的事务ID
     */
    private Long relatedTxId;

    /**
     * ERP账期
     */
    private Date erpDate;

    /**
     * 接收日期
     */
    private Date recptDate;

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
     * 备注
     */
    private String remark;
}

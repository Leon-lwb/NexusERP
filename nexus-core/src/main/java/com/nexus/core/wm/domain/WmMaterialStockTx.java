package com.nexus.core.wm.domain;

import java.math.BigDecimal;
import java.util.Date;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 库存事务对象 wm_material_stock_tx
 * 
 * @author nexus
 * @date 2025-12-12
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName("wm_material_stock_tx")
public class WmMaterialStockTx extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /** 事务ID */
    @TableId(value = "tx_id", type = IdType.AUTO)
    private Long txId;

    /** 事务类型 */
    @Excel(name = "事务类型")
    private String txType;

    /** 产品物料ID */
    @Excel(name = "产品物料ID")
    private Long materialId;

    /** 产品物料编码 */
    @Excel(name = "产品物料编码")
    private String materialCode;

    /** 产品物料名称 */
    @Excel(name = "产品物料名称")
    private String materialName;

    /** 规格型号 */
    @Excel(name = "规格型号")
    private String materialSpec;

    /** 单位 */
    private String baseUomId;

    /** 单位名称 */
    @Excel(name = "单位名称")
    private String baseUomName;

    /** 批次ID */
    private Long batchId;

    /** 批次号 */
    @Excel(name = "批次号")
    private String batchCode;

    /** 仓库ID */
    private Long warehouseId;

    /** 仓库编码 */
    @Excel(name = "仓库编码")
    private String warehouseCode;

    /** 仓库名称 */
    @Excel(name = "仓库名称")
    private String warehouseName;

    /** 库区ID */
    private Long areaId;

    /** 库区编码 */
    @Excel(name = "库区编码")
    private String areaCode;

    /** 库区名称 */
    @Excel(name = "库区名称")
    private String areaName;

    /** 库位ID */
    private Long locationId;

    /** 库位编码 */
    @Excel(name = "库位编码")
    private String locationCode;

    /** 库位名称 */
    @Excel(name = "库位名称")
    private String locationName;

    /** 容器ID */
    private Long packageId;

    /** 容器编号 */
    @Excel(name = "容器编号")
    private String packageCode;

    /** 单据类型 */
    @Excel(name = "单据类型")
    private String sourceDocType;

    /** 单据ID */
    private Long sourceDocId;

    /** 单据编号 */
    @Excel(name = "单据编号")
    private String sourceDocCode;

    /** 单据行ID */
    private Long sourceDocLineId;

    /** 库存记录ID */
    private Long materialStockId;

    /** 库存方向 */
    @Excel(name = "库存方向")
    private Integer txFlag;

    /** 事务数量 */
    @Excel(name = "事务数量")
    private BigDecimal txQuantity;

    /** 变动前数量 */
    @Excel(name = "变动前数量")
    private BigDecimal beforeQuantity;

    /** 变动后数量 */
    @Excel(name = "变动后数量")
    private BigDecimal afterQuantity;

    /** 事务日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "事务日期", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date txDate;

    /** 关联的事务ID */
    private Long relatedTxId;

    /** ERP账期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "ERP账期", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date erpDate;

    /** 接收日期 */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Excel(name = "接收日期", width = 30, dateFormat = "yyyy-MM-dd HH:mm:ss")
    private Date recptDate;

    /** 预留字段1 */
    private String attr1;

    /** 预留字段2 */
    private String attr2;

    /** 预留字段3 */
    private Long attr3;

    /** 预留字段4 */
    private Long attr4;
}
package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
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
 * 库存事务对象 wm_material_stock_tx
 *
 * @author ruoyi
 * @date 2025-12-12
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_material_stock_tx")
public class WmMaterialStockTx extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 事务ID
     */
    @TableId(value = "tx_id")
    private Long txId;

    /**
     * 事务类型
     */
    @ExcelProperty("事务类型")
    private String txType;

    /**
     * 产品物料ID
     */
    @ExcelProperty("产品物料ID")
    private Long materialId;

    /**
     * 产品物料编码
     */
    @ExcelProperty("产品物料编码")
    private String materialCode;

    /**
     * 产品物料名称
     */
    @ExcelProperty("产品物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty("规格型号")
    private String materialSpec;

    /**
     * 单位
     */
    private String baseUomId;

    /**
     * 单位名称
     */
    @ExcelProperty("单位名称")
    private String baseUomName;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次号
     */
    @ExcelProperty("批次号")
    private String batchCode;

    /**
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty("仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty("仓库名称")
    private String warehouseName;

    /**
     * 库区ID
     */
    private Long areaId;

    /**
     * 库区编码
     */
    @ExcelProperty("库区编码")
    private String areaCode;

    /**
     * 库区名称
     */
    @ExcelProperty("库区名称")
    private String areaName;

    /**
     * 库位ID
     */
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty("库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty("库位名称")
    private String locationName;

    /**
     * 容器ID
     */
    private Long packageId;

    /**
     * 容器编号
     */
    @ExcelProperty("容器编号")
    private String packageCode;

    /**
     * 单据类型
     */
    @ExcelProperty("单据类型")
    private String sourceDocType;

    /**
     * 单据ID
     */
    private Long sourceDocId;

    /**
     * 单据编号
     */
    @ExcelProperty("单据编号")
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
    @ExcelProperty("库存方向")
    private Integer txFlag;

    /**
     * 事务数量
     */
    @ExcelProperty("事务数量")
    private BigDecimal txQuantity;

    /**
     * 单位成本
     */
    @ExcelProperty("单位成本")
    private BigDecimal unitCost;

    /**
     * 事务金额
     */
    @ExcelProperty("事务金额")
    private BigDecimal txAmount;

    /**
     * 变动前数量
     */
    @ExcelProperty("变动前数量")
    private BigDecimal beforeQuantity;

    /**
     * 变动后数量
     */
    @ExcelProperty("变动后数量")
    private BigDecimal afterQuantity;

    /**
     * 事务日期
     */
    @ExcelProperty("事务日期")
    private Date txDate;

    /**
     * 关联的事务ID
     */
    private Long relatedTxId;

    /**
     * ERP账期
     */
    @ExcelProperty("ERP账期")
    private Date erpDate;

    /**
     * 接收日期
     */
    @ExcelProperty("接收日期")
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
     * 删除标志
     */
    @TableLogic
    private String delFlag;

    /**
     * 备注
     */
    private String remark;
}

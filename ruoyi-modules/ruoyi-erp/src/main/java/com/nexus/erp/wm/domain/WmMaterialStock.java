package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存清单对象 wm_material_stock
 *
 * @author ruoyi
 * @date 2025-12-03
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@TableName("wm_material_stock")
public class WmMaterialStock extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 库存ID
     */
    @TableId(value = "stock_id")
    private Long stockId;

    /**
     * 物料类型ID
     */
    @ExcelProperty("物料类型ID")
    private Long categoryId;

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
     * 单位id
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
     * 入库批次号
     */
    @ExcelProperty("入库批次号")
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
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编号
     */
    @ExcelProperty("供应商编号")
    private String vendorCode;

    /**
     * 供应商名称
     */
    @ExcelProperty("供应商名称")
    private String vendorName;

    /**
     * 供应商简称
     */
    @ExcelProperty("供应商简称")
    private String vendorNick;

    /**
     * 在库数量
     */
    @ExcelProperty("在库数量")
    private BigDecimal stockQuantity;

    /**
     * 单位成本
     */
    @ExcelProperty("单位成本")
    private BigDecimal unitCost;

    /**
     * 库存总金额
     */
    @ExcelProperty("库存总金额")
    private BigDecimal totalCost;

    /**
     * 生产工单ID
     */
    @ExcelProperty("生产工单ID")
    private Long woId;

    /**
     * 生产工单编号
     */
    @ExcelProperty("生产工单编号")
    private String woNo;

    /**
     * 生产日期
     */
    @ExcelProperty("生产日期")
    private Date productionDate;

    /**
     * 入库时间
     */
    @ExcelProperty("入库时间")
    private Date recptDate;

    /**
     * 库存有效期
     */
    @ExcelProperty("库存有效期")
    private Date expireDate;

    /**
     * 冻结标志
     */
    @ExcelProperty("冻结标志")
    private Long frozenFlag;

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

    /**
     * 乐观锁版本号
     */
    @Version
    private Integer version;
}

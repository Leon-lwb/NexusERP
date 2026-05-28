package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmMaterialStock;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存清单业务对象 wm_material_stock
 *
 * @author ruoyi
 * @date 2025-12-03
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMaterialStock.class, reverseConvertGenerate = false)
public class WmMaterialStockBo extends BaseEntity {

    /**
     * 库存ID
     */
    private Long stockId;

    /**
     * 物料类型ID
     */
    private Long categoryId;

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
     * 单位id
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
     * 入库批次号
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
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编号
     */
    private String vendorCode;

    /**
     * 供应商名称
     */
    private String vendorName;

    /**
     * 供应商简称
     */
    private String vendorNick;

    /**
     * 在库数量
     */
    private BigDecimal stockQuantity;

    /**
     * 生产工单ID
     */
    private Long woId;

    /**
     * 生产工单编号
     */
    private String woNo;

    /**
     * 生产日期
     */
    private Date productionDate;

    /**
     * 入库时间
     */
    private Date recptDate;

    /**
     * 库存有效期
     */
    private Date expireDate;

    /**
     * 冻结标志
     */
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
     * 备注
     */
    private String remark;
}

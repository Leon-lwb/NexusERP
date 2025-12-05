
package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 库存清单对象 wm_material_stock vo
 *
 * @author nexus
 * @date 2025-12-03
 */
@Data
public class WmMaterialStockVo extends BasePageDtoRequest {

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
     * 单位名称
     */
    private String baseUomName;
    /**
     * 入库批次号
     */
    private String batchCode;
    /**
     * 仓库编码
     */
    private String warehouseCode;
    /**
     * 仓库名称
     */
    private String warehouseName;
    /**
     * 库区编码
     */
    private String areaCode;
    /**
     * 库区名称
     */
    private String areaName;
    /**
     * 库位编码
     */
    private String locationCode;
    /**
     * 库位名称
     */
    private String locationName;
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
    private LocalDateTime productionDate;
    /**
     * 入库时间
     */
    private LocalDateTime recptDate;
    /**
     * 库存有效期
     */
    private LocalDateTime expireDate;
    /**
     * 冻结标志
     */
    private Long frozenFlag;

}
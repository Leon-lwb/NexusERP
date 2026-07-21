package com.nexus.erp.wm.domain.bo;

import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmStockTakingLine;
import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;

/**
 * 盘点单行业务对象 wm_stock_taking_line
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmStockTakingLine.class)
public class WmStockTakingLineBo extends BaseEntity {

    /**
     * 盘点行ID
     */
    @NotNull(message = "盘点行ID不能为空", groups = {EditGroup.class})
    private Long lineId;

    /**
     * 盘点计划ID
     */
    @NotNull(message = "盘点计划ID不能为空", groups = {AddGroup.class, EditGroup.class})
    private Long planId;

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
     * 库存ID
     */
    private Long stockId;

    /**
     * 账面数量
     */
    private BigDecimal bookQuantity;

    /**
     * 实盘数量
     */
    private BigDecimal actualQuantity;

    /**
     * 差异数量
     */
    private BigDecimal differenceQuantity;

    /**
     * 状态
     */
    private String status;

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
}

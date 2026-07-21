package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.core.validate.AddGroup;
import com.nexus.common.core.validate.EditGroup;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmProductionRecpt;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 生产入库单业务对象 wm_production_recpt
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmProductionRecpt.class)
public class WmProductionRecptBo extends BaseEntity {

    /**
     * 生产入库单ID
     */
    @NotNull(message = "生产入库ID不能为空", groups = {EditGroup.class})
    private Long recptId;

    /**
     * 生产入库单编码
     */
    @NotBlank(message = "入库单号不能为空", groups = {AddGroup.class, EditGroup.class})
    private String recptCode;

    /**
     * 生产工单ID
     */
    private Long woId;

    /**
     * 生产工单号
     */
    private String woNo;

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
     * 入库数量
     */
    private BigDecimal quantity;

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
     * 入库日期
     */
    private Date recptDate;

    /**
     * 单据状态
     */
    private String status;

    /**
     * 备注
     */
    private String remark;
}

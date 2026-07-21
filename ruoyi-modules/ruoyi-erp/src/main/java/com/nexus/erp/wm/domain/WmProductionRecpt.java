package com.nexus.erp.wm.domain;

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
 * 生产入库单对象 wm_production_recpt
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_production_recpt")
public class WmProductionRecpt extends TenantEntity {

    /**
     * 生产入库单ID
     */
    @TableId(value = "recpt_id")
    private Long recptId;

    /**
     * 生产入库单编码
     */
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
     * 批次ID（生产入库时自动生成或引用）
     */
    private Long batchId;

    /**
     * 批次号（生产入库时自动生成或引用）
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

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

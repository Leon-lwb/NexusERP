package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 物料入库单明细对象 wm_material_receive_detail
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_receive_detail")
public class WmMaterialReceiveDetail extends TenantEntity {

    /**
     * 明细ID
     */
    @TableId(value = "detail_id")
    private Long detailId;

    /**
     * 入库单行ID
     */
    private Long lineId;

    /**
     * 入库单ID
     */
    private Long receiveId;

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
     * 数量
     */
    private BigDecimal quantity;

    /**
     * 批次ID
     */
    private Long batchId;

    /**
     * 批次号
     */
    private String batchCode;

    /**
     * 序列号ID（序列号管理物料上架时关联）
     */
    private Long snId;

    /**
     * 序列号编码（序列号管理物料上架时关联）
     */
    private String snCode;

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

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

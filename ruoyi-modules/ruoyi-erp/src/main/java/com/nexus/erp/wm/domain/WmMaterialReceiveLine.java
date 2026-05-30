package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 物料入库单行对象 wm_material_receive_line
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_receive_line")
public class WmMaterialReceiveLine extends TenantEntity {

    /** 行ID */
    @TableId(value = "line_id")
    private Long lineId;

    /** 入库单ID */
    private Long receiveId;
    /** 来源单据行ID */
    private Long sourceDocLineId;
    /** 物料ID */
    private Long materialId;
    /** 物料编码 */
    private String materialCode;
    /** 物料名称 */
    private String materialName;
    /** 规格型号 */
    private String materialSpec;
    /** 单位ID */
    private String baseUomId;
    /** 单位名称 */
    private String baseUomName;
    /** 入库数量 */
    private BigDecimal quantityReceived;
    /** 批次ID */
    private Long batchId;
    /** 批次号 */
    private String batchCode;
    /** 仓库ID */
    private Long warehouseId;
    /** 仓库编码 */
    private String warehouseCode;
    /** 仓库名称 */
    private String warehouseName;
    /** 库区ID */
    private Long areaId;
    /** 库区编码 */
    private String areaCode;
    /** 库区名称 */
    private String areaName;
    /** 库位ID */
    private Long locationId;
    /** 库位编码 */
    private String locationCode;
    /** 库位名称 */
    private String locationName;
    /** 备注 */
    private String remark;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;

    /** 入库单明细列表 */
    @TableField(exist = false)
    private List<WmMaterialReceiveDetail> wmMaterialReceiveLineDetailList;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

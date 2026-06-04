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

    @TableId(value = "detail_id")
    private Long detailId;

    private Long lineId;
    private Long receiveId;
    private Long materialId;
    private String materialCode;
    private String materialName;
    private String materialSpec;
    private String baseUomId;
    private String baseUomName;
    private BigDecimal quantity;
    private Long batchId;
    private String batchCode;
    /** 序列号ID（序列号管理物料上架时关联） */
    private Long snId;
    /** 序列号编码（序列号管理物料上架时关联） */
    private String snCode;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableLogic
    private String delFlag;
}

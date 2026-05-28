package com.nexus.erp.wm.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

/**
 * 物料入库单对象 wm_material_receive
 *
 * @author ruoyi
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_material_receive")
public class WmMaterialReceive extends TenantEntity {

    @TableId(value = "receive_id")
    private Long receiveId;

    private String receiveCode;
    private String receiveName;
    private Long iqcId;
    private String iqcCode;
    private Long noticeId;
    private String noticeCode;
    private Long purId;
    private String purCode;
    private Long vendorId;
    private String vendorCode;
    private String vendorName;
    private String vendorNick;
    private Long warehouseId;
    private String warehouseCode;
    private String warehouseName;
    private Long areaId;
    private String areaCode;
    private String areaName;
    private Long locationId;
    private String locationCode;
    private String locationName;
    private Date receiveDate;
    private String status;
    private Long selectFlowId;
    private String remark;
    private String attr1;
    private String attr2;
    private Long attr3;
    private Long attr4;

    @TableField(exist = false)
    private List<WmMaterialReceiveLine> wmMaterialReceiveLineList;

    @TableLogic
    private String delFlag;
}

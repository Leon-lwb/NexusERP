package com.nexus.erp.wm.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmMaterialReceive;

import java.util.Date;
import java.util.List;

/**
 * 物料入库单业务对象 wm_material_receive
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = WmMaterialReceive.class, reverseConvertGenerate = false)
public class WmMaterialReceiveBo extends BaseEntity {

    private Long receiveId;

    @NotBlank(message = "入库单编号不能为空")
    private String receiveCode;

    @NotBlank(message = "入库单名称不能为空")
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

    private List<WmMaterialReceiveLineBo> wmMaterialReceiveLineList;

    private Long[] ids;
}

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

    /**
     * 入库单ID
     */
    private Long receiveId;

    /**
     * 入库单编码
     */
    private String receiveCode;

    /**
     * 入库单名称
     */
    @NotBlank(message = "入库单名称不能为空")
    private String receiveName;

    /**
     * 质检单ID
     */
    private Long iqcId;

    /**
     * 质检单号
     */
    private String iqcCode;

    /**
     * 到货通知ID
     */
    private Long noticeId;

    /**
     * 到货通知编码
     */
    private String noticeCode;

    /**
     * 采购订单ID
     */
    private Long purId;

    /**
     * 采购订单号
     */
    private String purCode;

    /**
     * 供应商ID
     */
    private Long vendorId;

    /**
     * 供应商编码
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
     * 入库日期
     */
    private Date receiveDate;

    /**
     * 状态
     */
    private String status;

    /**
     * 关联流程ID
     */
    private Long selectFlowId;

    /**
     * 上架人ID
     */
    private Long shelfBy;

    /**
     * 上架时间
     */
    private Date shelfTime;

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
     * 物料入库明细列表
     */
    private List<WmMaterialReceiveLineBo> wmMaterialReceiveLineList;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;
}

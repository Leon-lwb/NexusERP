package com.nexus.core.wm.domain.vo;

import lombok.Data;
import com.nexus.common.core.plus.dto.BasePageDtoRequest;

import java.time.LocalDateTime;

/**
 * 物料入库单对象 wm_material_receive vo
 *
 * @author nexus
 * @date 2025-12-08
 */
@Data
public class WmMaterialReceiveVo extends BasePageDtoRequest {

    /**
     * 入库单ID
     */
    private Long receiveId;
    /**
     * 入库单编号
     */
    private String receiveCode;
    /**
     * 入库单名称
     */
    private String receiveName;
    /**
     * 来料检验单ID
     */
    private Long iqcId;
    /**
     * 来料检验单编号
     */
    private String iqcCode;
    /**
     * 到货通知单ID
     */
    private Long noticeId;
    /**
     * 到货通知单编号
     */
    private String noticeCode;
    /**
     * 采购订单ID
     */
    private Long purId;
    /**
     * 采购订单编号
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
    private LocalDateTime receiveDate;
    /**
     * 单据状态
     */
    private String status;

}
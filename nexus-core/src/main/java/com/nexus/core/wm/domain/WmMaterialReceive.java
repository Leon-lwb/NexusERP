package com.nexus.core.wm.domain;

import java.util.List;
import java.util.Date;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 物料入库单对象 wm_material_receive
 *
 * @author nexus
 * @date 2025-12-08
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmMaterialReceiveMapper.WmMaterialReceiveResult")
public class WmMaterialReceive extends BaseEntity {
    private static final long serialVersionUID = 1L;

    /**
     * 入库单ID
     */
    @TableId(value = "receive_id", type = IdType.AUTO)
    private Long receiveId;
    /**
     * 入库单编号
     */
    @Excel(name = "入库单编号")
    private String receiveCode;
    /**
     * 入库单名称
     */
    @Excel(name = "入库单名称")
    private String receiveName;
    /**
     * 来料检验单ID
     */
    @Excel(name = "来料检验单ID")
    private Long iqcId;
    /**
     * 来料检验单编号
     */
    @Excel(name = "来料检验单编号")
    private String iqcCode;
    /**
     * 到货通知单ID
     */
    @Excel(name = "到货通知单ID")
    private Long noticeId;
    /**
     * 到货通知单编号
     */
    @Excel(name = "到货通知单编号")
    private String noticeCode;
    /**
     * 采购订单ID
     */
    @Excel(name = "采购订单ID")
    private Long purId;
    /**
     * 采购订单编号
     */
    @Excel(name = "采购订单编号")
    private String purCode;
    /**
     * 供应商ID
     */
    @Excel(name = "供应商ID")
    private Long vendorId;
    /**
     * 供应商编码
     */
    @Excel(name = "供应商编码")
    private String vendorCode;
    /**
     * 供应商名称
     */
    @Excel(name = "供应商名称")
    private String vendorName;
    /**
     * 供应商简称
     */
    @Excel(name = "供应商简称")
    private String vendorNick;
    /**
     * 仓库ID
     */
    @Excel(name = "仓库ID")
    private Long warehouseId;
    /**
     * 仓库编码
     */
    @Excel(name = "仓库编码")
    private String warehouseCode;
    /**
     * 仓库名称
     */
    @Excel(name = "仓库名称")
    private String warehouseName;
    /**
     * 库区ID
     */
    @Excel(name = "库区ID")
    private Long areaId;
    /**
     * 库区编码
     */
    @Excel(name = "库区编码")
    private String areaCode;
    /**
     * 库区名称
     */
    @Excel(name = "库区名称")
    private String areaName;
    /**
     * 库位ID
     */
    @Excel(name = "库位ID")
    private Long locationId;
    /**
     * 库位编码
     */
    @Excel(name = "库位编码")
    private String locationCode;
    /**
     * 库位名称
     */
    @Excel(name = "库位名称")
    private String locationName;
    /**
     * 入库日期
     */
    @JsonFormat(pattern = "yyyy-MM-dd")
    @Excel(name = "入库日期", width = 30, dateFormat = "yyyy-MM-dd")
    private Date receiveDate;
    /**
     * 单据状态
     */
    @Excel(name = "单据状态")
    private String status;
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
     * 物料入库单行信息
     */
    private List<WmMaterialReceiveLine> wmMaterialReceiveLineList;
}
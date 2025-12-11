package com.nexus.core.wm.domain;

import java.math.BigDecimal;
import java.util.List;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 仓库设置对象 wm_warehouse
 *
 * @author nexus
 * @date 2025-09-30
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmWarehouseMapper.WmWarehouseResult")
public class WmWarehouse extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 仓库ID */
    @TableId(value = "warehouse_id",type = IdType.AUTO)
    private Long warehouseId;
    /** 仓库编码 */
    @Excel(name = "仓库编码")
    private String warehouseCode;
    /** 仓库名称 */
    @Excel(name = "仓库名称")
    private String warehouseName;
    /** 位置 */
    @Excel(name = "位置")
    private String location;
    /** 面积 */
    @Excel(name = "面积")
    private BigDecimal area;
    /** 负责人 */
    @Excel(name = "负责人")
    private String manager;
    /** user_id */
    private Long managerId;
    /** 仓库状态 */
    @Excel(name = "仓库状态")
    private Long status;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
    /**
     * 省份行政区划ID
     */
    private Long provinceAreaId;
    /**
     * 城市行政区划ID
     */
    private Long cityAreaId;
    /**
     * 区县行政区划ID
     */
    private Long districtAreaId;
    /**
     * 详细地址(街道、门牌号等)
     */
    private String streetId;
    /**
     * 地址纬度
     */
    private String addressLat;
    /**
     * 地址经度
     */
    private String addressLng;

    /**
     * 库区列表
     */
    private List<WmStorageArea> children;
}
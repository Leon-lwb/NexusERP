package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
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
 * 仓库设置对象 wm_warehouse
 *
 * @author ruoyi
 * @date 2025-09-30
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_warehouse")
public class WmWarehouse extends TenantEntity {

    /**
     * 仓库ID
     */
    @TableId(value = "warehouse_id")
    private Long warehouseId;

    /**
     * 仓库编码
     */
    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    /**
     * 仓库名称
     */
    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    /**
     * 位置
     */
    @ExcelProperty(value = "位置")
    private String location;

    /**
     * 面积
     */
    @ExcelProperty(value = "面积")
    private BigDecimal area;

    /**
     * 负责人
     */
    @ExcelProperty(value = "负责人")
    private String manager;

    /**
     * user_id
     */
    private Long managerId;

    /**
     * 仓库状态
     */
    @ExcelProperty(value = "仓库状态")
    private Long status;

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
    @TableField(exist = false)
    private List<WmStorageArea> children;

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

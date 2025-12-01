package com.nexus.core.wm.domain;

import java.math.BigDecimal;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.common.annotation.Excel;
import com.nexus.common.core.domain.BaseEntity;

/**
 * 库区对象 wm_storage_area
 *
 * @author nexus
 * @date 2025-12-01
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@TableName(resultMap = "com.nexus.core.wm.mapper.WmStorageAreaMapper.WmStorageAreaResult")
public class WmStorageArea extends BaseEntity
        {
private static final long serialVersionUID = 1L;

    /** 库区ID */
    @TableId(value = "area_id",type = IdType.AUTO)
    private Long areaId;
    /** 库区编码 */
    @Excel(name = "库区编码")
    private String areaCode;
    /** 库区名称 */
    @Excel(name = "库区名称")
    private String areaName;
    /** 仓库ID */
    @Excel(name = "仓库ID")
    private Long warehouseId;
    /** 面积 */
    @Excel(name = "面积")
    private BigDecimal area;
    /** 是否开启库位管理 */
    @Excel(name = "是否开启库位管理")
    private Long locationFlag;
    /** 是否启用 */
    @Excel(name = "是否启用")
    private Long enableFlag;
    /** 是否冻结 */
    @Excel(name = "是否冻结")
    private Long frozenFlag;
    /** 预留字段1 */
    private String attr1;
    /** 预留字段2 */
    private String attr2;
    /** 预留字段3 */
    private Long attr3;
    /** 预留字段4 */
    private Long attr4;
    /** 布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货 */
    @Excel(name = "布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货")
    private String layoutType;
    /** 3D坐标X(米) */
    private BigDecimal posX;
    /** 3D坐标Y(米) */
    private BigDecimal posY;
    /** 3D坐标Z(米) */
    private BigDecimal posZ;
    /** 库区宽度(米) */
    private BigDecimal areaWidth;
    /** 库区长度(米) */
    private BigDecimal areaLength;
    /** 库区高度(米) */
    private BigDecimal areaHeight;
    /** X轴旋转角度 */
    private BigDecimal rotationX;
    /** Y轴旋转角度 */
    private BigDecimal rotationY;
    /** Z轴旋转角度 */
    private BigDecimal rotationZ;
    /** 显示颜色 */
    private String color;
    /** 透明度 */
    private BigDecimal opacity;
    /** 模型类型: CUBE-立方体, CUSTOM-自定义 */
    private String modelType;
    /** 自定义模型URL */
    private String modelUrl;
    /** 是否显示边界墙 */
    private Long showWalls;
    /** 是否显示标签 */
    private Long showLabel;
    /** 是否显示网格 */
    private Long gridVisible;
    /** 3D配置JSON */
    private String configJson;
}
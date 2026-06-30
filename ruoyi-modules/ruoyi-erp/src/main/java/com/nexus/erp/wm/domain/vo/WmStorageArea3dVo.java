package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 库区 3D 可视化视图对象
 * <p>
 * 字段对齐 {@code wm_storage_area} 表的 3D 相关列，供前端渲染。
 *
 * @author nexus
 * @date 2026-06-30
 */
@Data
public class WmStorageArea3dVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 仓库ID
     */
    private Long warehouseId;

    /**
     * 布局类型: GRID-网格, RACK-货架, FLUID-流体, BULK-散货
     */
    private String layoutType;

    /**
     * 3D坐标X(米)
     */
    private BigDecimal posX;

    /**
     * 3D坐标Y(米)
     */
    private BigDecimal posY;

    /**
     * 3D坐标Z(米)
     */
    private BigDecimal posZ;

    /**
     * 库区宽度(米)
     */
    private BigDecimal areaWidth;

    /**
     * 库区长度(米)
     */
    private BigDecimal areaLength;

    /**
     * 库区高度(米)
     */
    private BigDecimal areaHeight;

    /**
     * X轴旋转角度
     */
    private BigDecimal rotationX;

    /**
     * Y轴旋转角度
     */
    private BigDecimal rotationY;

    /**
     * Z轴旋转角度
     */
    private BigDecimal rotationZ;

    /**
     * 显示颜色
     */
    private String color;

    /**
     * 透明度
     */
    private BigDecimal opacity;

    /**
     * 模型类型: CUBE-立方体, CUSTOM-自定义
     */
    private String modelType;

    /**
     * 自定义模型URL
     */
    private String modelUrl;

    /**
     * 是否显示边界墙
     */
    private Long showWalls;

    /**
     * 是否显示标签
     */
    private Long showLabel;

    /**
     * 是否显示网格
     */
    private Long gridVisible;

    /**
     * 3D配置JSON
     */
    private String configJson;

    /**
     * 库位列表
     */
    private List<WmStorageLocation3dVo> locations;
}

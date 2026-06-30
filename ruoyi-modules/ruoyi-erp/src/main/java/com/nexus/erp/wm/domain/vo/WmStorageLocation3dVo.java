package com.nexus.erp.wm.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 库位 3D 可视化视图对象
 * <p>
 * 字段对齐 {@code wm_storage_location} 表的 3D 相关列，并附带由 Service 计算的运行时状态。
 *
 * @author nexus
 * @date 2026-06-30
 */
@Data
public class WmStorageLocation3dVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

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
     * 库区ID
     */
    private Long areaId;

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
     * 库位宽度(米)
     */
    private BigDecimal width;

    /**
     * 库位高度(米)
     */
    private BigDecimal height;

    /**
     * 库位深度(米)
     */
    private BigDecimal locationDepth;

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
     * 空置时颜色
     */
    private String colorEmpty;

    /**
     * 占用时颜色
     */
    private String colorOccupied;

    /**
     * 锁定时颜色
     */
    private String colorLocked;

    /**
     * 预警时颜色
     */
    private String colorWarning;

    /**
     * 透明度
     */
    private BigDecimal opacity;

    /**
     * 是否显示边框
     */
    private Long showBorder;

    /**
     * 是否显示标签
     */
    private Long showLabel;

    /**
     * 边框颜色
     */
    private String borderColor;

    /**
     * 边框宽度
     */
    private BigDecimal borderWidth;

    /**
     * 标签位置: TOP-顶部, FRONT-前面标签位
     */
    private String labelPosition;

    /**
     * 标签大小
     */
    private BigDecimal labelSize;

    /**
     * 是否冻结
     */
    private Long frozenFlag;

    /**
     * 是否启用
     */
    private Long enableFlag;

    /**
     * 所属货架ID
     */
    private String rackId;

    /**
     * 层数(从下往上)
     */
    private Long layer;

    /**
     * 列数(从左往右)
     */
    private Long col;

    /**
     * 行数(从前到后)
     */
    private Long rowNum;

    /**
     * 占用率(0-100)
     */
    private BigDecimal occupancyRate;

    // ===== 运行时状态（Service 装配，非库表列） =====

    /**
     * 库位状态: EMPTY-空置, OCCUPIED-占用, WARNING-预警, LOCKED-锁定
     */
    private String status;

    /**
     * 在库数量
     */
    private BigDecimal stockQuantity;

    /**
     * 已预留数量
     */
    private BigDecimal reservedQuantity;

    /**
     * 库存记录数
     */
    private Integer stockCount;
}

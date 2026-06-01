package com.nexus.erp.wm.domain;

import cn.idev.excel.annotation.ExcelProperty;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 库位对象 wm_storage_location
 *
 * @author nexus
 * @date 2025-12-02
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("wm_storage_location")
public class WmStorageLocation extends TenantEntity {

    /**
     * 库位ID
     */
    @TableId(value = "location_id")
    private Long locationId;

    /**
     * 库位编码
     */
    @ExcelProperty(value = "库位编码")
    private String locationCode;

    /**
     * 库位名称
     */
    @ExcelProperty(value = "库位名称")
    private String locationName;

    /**
     * 仓库ID
     */
    @ExcelProperty(value = "仓库ID")
    private Long warehouseId;

    /**
     * 库区ID
     */
    private Long areaId;

    /**
     * 绑定的物料类别ID
     */
    @ExcelProperty(value = "物料类别ID")
    private Long categoryId;

    /**
     * 面积
     */
    @ExcelProperty(value = "面积")
    private BigDecimal area;

    /**
     * 最大载重量
     */
    @ExcelProperty(value = "最大载重量")
    private BigDecimal maxLoa;

    /**
     * 库位位置X
     */
    private Long positionX;

    /**
     * 库位位置Y
     */
    private Long positionY;

    /**
     * 库位位置Z
     */
    private Long positionZ;

    /**
     * 是否允许产品混放
     */
    @ExcelProperty(value = "是否允许产品混放")
    private Long productMixing;

    /**
     * 是否允许批次混放
     */
    @ExcelProperty(value = "是否允许批次混放")
    private Long batchMixing;

    /**
     * 是否启用
     */
    @ExcelProperty(value = "是否启用")
    private Long enableFlag;

    /**
     * 是否冻结
     */
    @ExcelProperty(value = "是否冻结")
    private Long frozenFlag;

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
     * 库位类型
     */
    @ExcelProperty(value = "库位类型")
    private String locationType;

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
     * 边框颜色
     */
    private String borderColor;

    /**
     * 边框宽度
     */
    private BigDecimal borderWidth;

    /**
     * 是否显示标签
     */
    private Long showLabel;

    /**
     * 位置: TOP-顶部, FRONT-前面标签位
     */
    private String labelPosition;

    /**
     * 标签大小
     */
    private BigDecimal labelSize;

    /**
     * 库位状态
     */
    @ExcelProperty(value = "库位状态")
    private Long status;

    /**
     * 占用率(0-100)
     */
    @ExcelProperty(value = "占用率")
    private BigDecimal occupancyRate;

    /**
     * 最后入库时间
     */
    @ExcelProperty(value = "最后入库时间")
    private Date lastInboundTime;

    /**
     * 最后出库时间
     */
    @ExcelProperty(value = "最后出库时间")
    private Date lastOutboundTime;

    /**
     * 3D配置JSON数据
     */
    private String configJson;

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

package com.nexus.erp.wm.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.wm.domain.WmStockAlert;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 安全库存预警视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = WmStockAlert.class)
public class WmStockAlertVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 预警ID
     */
    @ExcelProperty(value = "预警ID")
    private Long alertId;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /**
     * 物料名称
     */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /**
     * 规格型号
     */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /**
     * 单位名称
     */
    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    /**
     * 仓库ID
     */
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
     * 当前库存
     */
    @ExcelProperty(value = "当前库存")
    private BigDecimal currentQty;

    /**
     * 安全库存
     */
    @ExcelProperty(value = "安全库存")
    private BigDecimal safetyStock;

    /**
     * 最低库存
     */
    @ExcelProperty(value = "最低库存")
    private BigDecimal minStock;

    /**
     * 最高库存
     */
    @ExcelProperty(value = "最高库存")
    private BigDecimal maxStock;

    /**
     * 预警类型
     */
    @ExcelProperty(value = "预警类型")
    private String alertType;

    /**
     * 预警级别
     */
    @ExcelProperty(value = "预警级别")
    private String alertLevel;

    /**
     * 处理状态
     */
    @ExcelProperty(value = "处理状态")
    private Integer handledFlag;

    /**
     * 处理时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date handledTime;

    /**
     * 处理人ID
     */
    private Long handledBy;

    /**
     * 处理说明
     */
    @ExcelProperty(value = "处理说明")
    private String handledRemark;

    /**
     * 备注
     */
    @ExcelProperty(value = "备注")
    private String remark;
}

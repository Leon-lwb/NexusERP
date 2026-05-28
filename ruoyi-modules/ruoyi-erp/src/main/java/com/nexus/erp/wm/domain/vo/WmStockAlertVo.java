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

    @ExcelProperty(value = "预警ID")
    private Long alertId;

    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    private Long warehouseId;

    @ExcelProperty(value = "仓库编码")
    private String warehouseCode;

    @ExcelProperty(value = "仓库名称")
    private String warehouseName;

    @ExcelProperty(value = "当前库存")
    private BigDecimal currentQty;

    @ExcelProperty(value = "安全库存")
    private BigDecimal safetyStock;

    @ExcelProperty(value = "最低库存")
    private BigDecimal minStock;

    @ExcelProperty(value = "最高库存")
    private BigDecimal maxStock;

    @ExcelProperty(value = "预警类型")
    private String alertType;

    @ExcelProperty(value = "预警级别")
    private String alertLevel;

    @ExcelProperty(value = "处理状态")
    private Integer handledFlag;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date handledTime;

    private Long handledBy;

    @ExcelProperty(value = "处理说明")
    private String handledRemark;

    @ExcelProperty(value = "备注")
    private String remark;
}

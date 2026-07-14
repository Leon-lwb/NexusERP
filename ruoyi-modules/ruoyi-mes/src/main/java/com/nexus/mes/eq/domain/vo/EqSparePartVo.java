package com.nexus.mes.eq.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.eq.domain.EqSparePart;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * MES备件主数据视图对象 eq_spare_part
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = EqSparePart.class)
public class EqSparePartVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 备件ID */
    private Long spareId;

    /** 备件编码 */
    @ExcelProperty("备件编码")
    private String spareCode;

    /** 备件名称 */
    @ExcelProperty("备件名称")
    private String spareName;

    /** 规格型号 */
    @ExcelProperty("规格型号")
    private String spec;

    /** 型号 */
    private String model;

    /** 单位 */
    @ExcelProperty("单位")
    private String unit;

    /** 分类 */
    @ExcelProperty("分类")
    private String category;

    /** 安全库存 */
    @ExcelProperty("安全库存")
    private BigDecimal safetyStock;

    /** 当前库存 */
    @ExcelProperty("当前库存")
    private BigDecimal currentStock;

    /** 是否低于安全库存(联表/计算字段) */
    private Boolean lowStock;

    /** 备注 */
    private String remark;
}

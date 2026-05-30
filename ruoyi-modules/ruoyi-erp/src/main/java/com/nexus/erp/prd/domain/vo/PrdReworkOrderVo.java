package com.nexus.erp.prd.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import io.github.linpeilie.annotations.AutoMapper;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.prd.domain.PrdReworkOrder;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 返工订单视图对象
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = PrdReworkOrder.class)
public class PrdReworkOrderVo extends BaseEntity implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 返工单ID */
    @ExcelProperty(value = "返工单ID")
    private Long reworkId;

    /** 返工单号 */
    @ExcelProperty(value = "返工单号")
    private String reworkNo;

    /** 来源工单ID */
    private Long sourceWoId;

    /** 来源工单编号 */
    @ExcelProperty(value = "来源工单编号")
    private String sourceWoNo;

    /** 物料ID */
    private Long materialId;

    /** 物料编码 */
    @ExcelProperty(value = "物料编码")
    private String materialCode;

    /** 物料名称 */
    @ExcelProperty(value = "物料名称")
    private String materialName;

    /** 规格型号 */
    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    /** 单位名称 */
    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    /** 返工数量 */
    @ExcelProperty(value = "返工数量")
    private BigDecimal reworkQty;

    /** 返工原因 */
    @ExcelProperty(value = "返工原因")
    private String reworkReason;

    /** 实际完成数量 */
    @ExcelProperty(value = "实际数量")
    private BigDecimal actualQty;

    /** 报废数量 */
    @ExcelProperty(value = "报废数量")
    private BigDecimal scrapQty;

    /** 返工状态 */
    @ExcelProperty(value = "返工状态")
    private String status;

    /** 备注 */
    @ExcelProperty(value = "备注")
    private String remark;
}

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

    @ExcelProperty(value = "返工单ID")
    private Long reworkId;

    @ExcelProperty(value = "返工单号")
    private String reworkNo;

    private Long sourceWoId;

    @ExcelProperty(value = "来源工单编号")
    private String sourceWoNo;

    private Long materialId;

    @ExcelProperty(value = "物料编码")
    private String materialCode;

    @ExcelProperty(value = "物料名称")
    private String materialName;

    @ExcelProperty(value = "规格型号")
    private String materialSpec;

    @ExcelProperty(value = "单位名称")
    private String baseUomName;

    @ExcelProperty(value = "返工数量")
    private BigDecimal reworkQty;

    @ExcelProperty(value = "返工原因")
    private String reworkReason;

    @ExcelProperty(value = "实际数量")
    private BigDecimal actualQty;

    @ExcelProperty(value = "报废数量")
    private BigDecimal scrapQty;

    @ExcelProperty(value = "返工状态")
    private String status;

    @ExcelProperty(value = "备注")
    private String remark;
}

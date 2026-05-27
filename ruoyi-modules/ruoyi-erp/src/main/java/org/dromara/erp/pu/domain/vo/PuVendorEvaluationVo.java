package org.dromara.erp.pu.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.dromara.common.mybatis.core.domain.BaseEntity;
import org.dromara.erp.pu.domain.PuVendorEvaluation;

import java.math.BigDecimal;

@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuVendorEvaluation.class)
public class PuVendorEvaluationVo extends BaseEntity {

    @ExcelProperty(value = "评估ID")
    private Long evaluationId;

    private Long vendorId;

    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    @ExcelProperty(value = "评估周期")
    private String evalPeriod;

    @ExcelProperty(value = "交货及时率(%)")
    private BigDecimal deliveryRate;

    @ExcelProperty(value = "质量合格率(%)")
    private BigDecimal qualityRate;

    @ExcelProperty(value = "价格竞争力")
    private BigDecimal priceScore;

    @ExcelProperty(value = "综合评分")
    private BigDecimal totalScore;

    @ExcelProperty(value = "评估等级")
    private String evalLevel;

    @ExcelProperty(value = "总订单数")
    private Integer totalOrders;

    @ExcelProperty(value = "按时交货订单数")
    private Integer onTimeOrders;

    @ExcelProperty(value = "总收货数量")
    private BigDecimal totalReceivedQty;

    @ExcelProperty(value = "合格数量")
    private BigDecimal qualifiedQty;

    @ExcelProperty(value = "退货数量")
    private BigDecimal returnQty;

    @ExcelProperty(value = "备注")
    private String remark;

    private String attr1;

    private String attr2;

    private Long attr3;

    private Long attr4;
}

package com.nexus.erp.pu.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuVendorEvaluation;

import java.math.BigDecimal;

/**
 * 供应商评估视图对象 VO
 * <p>
 * 用于供应商绩效评估的前端展示和 Excel 导出，通过 {@link ExcelProperty} 注解定义导出列映射。
 * 与 {@link PuVendorEvaluation} 实体通过 MapStruct 自动映射转换。
 * 展示供应商在各评估维度的得分、等级及统计明细。
 *
 * @author ruoyi
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuVendorEvaluation.class)
public class PuVendorEvaluationVo extends BaseEntity {

    /** 评估记录ID */
    @ExcelProperty(value = "评估ID")
    private Long evaluationId;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商编码 */
    @ExcelProperty(value = "供应商编码")
    private String vendorCode;

    /** 供应商名称 */
    @ExcelProperty(value = "供应商名称")
    private String vendorName;

    /** 评估周期，格式yyyy-MM */
    @ExcelProperty(value = "评估周期")
    private String evalPeriod;

    /** 交货及时率（%） */
    @ExcelProperty(value = "交货及时率(%)")
    private BigDecimal deliveryRate;

    /** 质量合格率（%） */
    @ExcelProperty(value = "质量合格率(%)")
    private BigDecimal qualityRate;

    /** 价格竞争力得分 */
    @ExcelProperty(value = "价格竞争力")
    private BigDecimal priceScore;

    /** 综合评分 */
    @ExcelProperty(value = "综合评分")
    private BigDecimal totalScore;

    /** 评估等级（A/B/C/D） */
    @ExcelProperty(value = "评估等级")
    private String evalLevel;

    /** 统计周期内总订单数 */
    @ExcelProperty(value = "总订单数")
    private Integer totalOrders;

    /** 统计周期内按时交货订单数 */
    @ExcelProperty(value = "按时交货订单数")
    private Integer onTimeOrders;

    /** 统计周期内总收货数量 */
    @ExcelProperty(value = "总收货数量")
    private BigDecimal totalReceivedQty;

    /** 统计周期内合格数量 */
    @ExcelProperty(value = "合格数量")
    private BigDecimal qualifiedQty;

    /** 统计周期内退货总数量 */
    @ExcelProperty(value = "退货数量")
    private BigDecimal returnQty;

    /** 备注 */
    @ExcelProperty(value = "备注")
    private String remark;

    /** 扩展字段1 */
    private String attr1;

    /** 扩展字段2 */
    private String attr2;

    /** 扩展字段3 */
    private Long attr3;

    /** 扩展字段4 */
    private Long attr4;
}

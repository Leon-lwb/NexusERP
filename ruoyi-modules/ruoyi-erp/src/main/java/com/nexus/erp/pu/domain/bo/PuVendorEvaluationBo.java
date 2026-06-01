package com.nexus.erp.pu.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuVendorEvaluation;

import java.math.BigDecimal;

/**
 * 供应商评估业务对象 BO
 * <p>
 * 用于供应商绩效评估的前后端数据交互，承载新增、修改、查询等操作的请求参数。
 * 与 {@link PuVendorEvaluation} 实体通过 MapStruct 自动映射转换。
 * 包含供应商在各评估维度的得分、等级及统计明细。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuVendorEvaluation.class, reverseConvertGenerate = false)
public class PuVendorEvaluationBo extends BaseEntity {

    /** 评估记录ID */
    @NotNull(message = "评估ID不能为空", groups = {com.nexus.common.core.validate.EditGroup.class})
    private Long evaluationId;

    /** 供应商ID */
    @NotNull(message = "供应商ID不能为空")
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 评估周期，格式yyyy-MM */
    @NotBlank(message = "评估周期不能为空")
    private String evalPeriod;

    /** 交货及时率（%） */
    private BigDecimal deliveryRate;

    /** 质量合格率（%） */
    private BigDecimal qualityRate;

    /** 价格竞争力得分 */
    private BigDecimal priceScore;

    /** 综合评分 */
    private BigDecimal totalScore;

    /** 评估等级（A/B/C/D） */
    private String evalLevel;

    /** 统计周期内总订单数 */
    private Integer totalOrders;

    /** 统计周期内按时交货订单数 */
    private Integer onTimeOrders;

    /** 统计周期内总收货数量 */
    private BigDecimal totalReceivedQty;

    /** 统计周期内合格数量 */
    private BigDecimal qualifiedQty;

    /** 统计周期内退货总数量 */
    private BigDecimal returnQty;

    /** 备注 */
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

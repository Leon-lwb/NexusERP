package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;

/**
 * 供应商评估实体对象 pu_vendor_evaluation
 * <p>
 * 存储供应商绩效评估结果，基于采购订单、退货单等历史数据，
 * 从交货及时率、质量合格率、价格竞争力三个维度综合计算供应商得分和等级。
 * 评估周期通常为自然月，评估结果用于供应商分级管理和采购决策优化。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_vendor_evaluation")
public class PuVendorEvaluation extends TenantEntity {

    /** 评估记录ID，主键 */
    @TableId(value = "evaluation_id")
    private Long evaluationId;

    /** 供应商ID，关联bd_vendor表 */
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 评估周期，格式yyyy-MM */
    private String evalPeriod;

    /** 交货及时率（%），按时交货订单数/总订单数 */
    private BigDecimal deliveryRate;

    /** 质量合格率（%），(总收货量-退货量)/总收货量 */
    private BigDecimal qualityRate;

    /** 价格竞争力得分（0-100），与市场均价对比计算 */
    private BigDecimal priceScore;

    /** 综合评分（0-100），加权计算：交货40%+质量35%+价格25% */
    private BigDecimal totalScore;

    /**
     * 评估等级
     * <ul>
     *   <li>A - 优秀（≥90分）</li>
     *   <li>B - 良好（≥80分）</li>
     *   <li>C - 合格（≥60分）</li>
     *   <li>D - 不合格（&lt;60分）</li>
     * </ul>
     */
    private String evalLevel;

    /** 统计周期内总订单数 */
    private Integer totalOrders;

    /** 统计周期内按时交货订单数 */
    private Integer onTimeOrders;

    /** 统计周期内总收货数量 */
    private BigDecimal totalReceivedQty;

    /** 统计周期内合格数量（总收货量-退货量） */
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

    /** 删除标志（0正常 1已删除） */
    @TableLogic
    private String delFlag;
}

package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoQuotation;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售报价视图对象 so_quotation
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoQuotation.class)
public class SoQuotationVo implements Serializable {

    /** 报价ID */
    @ExcelProperty("报价ID")
    private Long quotationId;

    /** 报价单号 */
    @ExcelProperty("报价单号")
    private String quotationNo;

    /** 客户ID */
    @ExcelProperty("客户ID")
    private Long clientId;

    /** 客户名称 */
    @ExcelProperty("客户名称")
    private String clientName;

    /** 销售代表 */
    @ExcelProperty("销售代表")
    private String salesRepName;

    /** 报价日期 */
    @ExcelProperty("报价日期")
    private Date quotationDate;

    /** 有效期至 */
    @ExcelProperty("有效期至")
    private Date validUntil;

    /** 币种 */
    @ExcelProperty("币种")
    private String currency;

    /** 总金额 */
    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    /** 税额 */
    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    /** 折扣额 */
    @ExcelProperty("折扣额")
    private BigDecimal discountAmount;

    /** 状态 */
    @ExcelProperty("状态")
    private String status;

    /** 备注 */
    @ExcelProperty("备注")
    private String remark;

    /**
     * 报价明细行列表。
     * <p>用 {@link SoQuotationLineVo} 而非实体：原实现直接返回实体，会把 {@code tenantId}/
     * {@code delFlag}/{@code createBy} 等审计字段一并暴露给前端；且原来的
     * {@code @JSONField(serialize = false)} 是 fastjson2 注解，本项目 HTTP 走 Jackson，
     * 该注解完全不生效（即便生效也会让报价表单读不到明细行）。</p>
     */
    private List<SoQuotationLineVo> soQuotationLineList;
}

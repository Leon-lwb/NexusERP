package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import com.alibaba.fastjson.annotation.JSONField;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoQuotation;
import com.nexus.erp.so.domain.SoQuotationLine;

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

    /** 报价明细行列表（不参与序列化） */
    @JSONField(serialize = false)
    private List<SoQuotationLine> soQuotationLineList;
}

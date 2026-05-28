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

@Data
@AutoMapper(target = SoQuotation.class)
public class SoQuotationVo implements Serializable {

    @ExcelProperty("报价ID")
    private Long quotationId;

    @ExcelProperty("报价单号")
    private String quotationNo;

    @ExcelProperty("客户ID")
    private Long clientId;

    @ExcelProperty("客户名称")
    private String clientName;

    @ExcelProperty("销售代表")
    private String salesRepName;

    @ExcelProperty("报价日期")
    private Date quotationDate;

    @ExcelProperty("有效期至")
    private Date validUntil;

    @ExcelProperty("币种")
    private String currency;

    @ExcelProperty("总金额")
    private BigDecimal totalAmount;

    @ExcelProperty("税额")
    private BigDecimal taxAmount;

    @ExcelProperty("折扣额")
    private BigDecimal discountAmount;

    @ExcelProperty("状态")
    private String status;

    @ExcelProperty("备注")
    private String remark;

    @JSONField(serialize = false)
    private List<SoQuotationLine> soQuotationLineList;
}

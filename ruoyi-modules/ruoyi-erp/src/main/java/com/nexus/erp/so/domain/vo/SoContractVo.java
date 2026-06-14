package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoContract;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售合同视图对象 so_contract
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = SoContract.class)
public class SoContractVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 合同ID
     */
    @ExcelProperty(value = "合同ID")
    private Long contractId;

    /**
     * 合同编号
     */
    @ExcelProperty(value = "合同编号")
    private String contractNo;

    /**
     * 合同名称
     */
    @ExcelProperty(value = "合同名称")
    private String contractName;

    /**
     * 合同类型
     */
    @ExcelProperty(value = "合同类型")
    private String contractType;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户名称
     */
    @ExcelProperty(value = "客户名称")
    private String clientName;

    /**
     * 签订日期
     */
    @ExcelProperty(value = "签订日期")
    private Date signDate;

    /**
     * 生效日期
     */
    @ExcelProperty(value = "生效日期")
    private Date effectiveDate;

    /**
     * 失效日期
     */
    @ExcelProperty(value = "失效日期")
    private Date expiryDate;

    /**
     * 合同金额
     */
    @ExcelProperty(value = "合同金额")
    private BigDecimal totalAmount;

    /**
     * 币种
     */
    @ExcelProperty(value = "币种")
    private String currency;

    /**
     * 付款条款
     */
    private String paymentTerms;

    /**
     * 销售代表ID
     */
    private Long salesRepId;

    /**
     * 销售代表名称
     */
    @ExcelProperty(value = "销售代表")
    private String salesRepName;

    /**
     * 合同状态
     */
    @ExcelProperty(value = "合同状态")
    private String status;

    /**
     * 备注
     */
    private String remark;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 更新时间
     */
    private Date updateTime;
}

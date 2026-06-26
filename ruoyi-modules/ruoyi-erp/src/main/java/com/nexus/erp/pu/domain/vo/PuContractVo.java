package com.nexus.erp.pu.domain.vo;

import cn.idev.excel.annotation.ExcelProperty;
import lombok.Data;
import com.nexus.erp.pu.domain.PuContractLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购合同视图对象
 *
 * @author nexus
 * @date 2026-06-15
 */
@Data
public class PuContractVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 合同ID */
    private Long contractId;

    /** 合同编号 */
    private String contractNo;

    /** 合同名称 */
    private String contractName;

    /** 供应商ID */
    private Long vendorId;

    /** 供应商编码 */
    private String vendorCode;

    /** 供应商名称 */
    private String vendorName;

    /** 合同类型 */
    private String contractType;

    /** 签订日期 */
    private Date signDate;

    /** 生效日期 */
    private Date effectiveDate;

    /** 失效日期 */
    private Date expiryDate;

    /** 合同总金额 */
    private BigDecimal totalAmount;

    /** 币种 */
    private String currency;

    /** 付款条款 */
    private String paymentTerms;

    /** 合同状态 */
    private String status;

    /** 审批流程ID */
    private Long selectFlowId;

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

    /** 创建时间 */
    private Date createTime;

    /** 更新时间 */
    @ExcelProperty("更新时间")
    private Date updateTime;

    /** 合同明细行列表 */
    private List<PuContractLine> puContractLineList;
}

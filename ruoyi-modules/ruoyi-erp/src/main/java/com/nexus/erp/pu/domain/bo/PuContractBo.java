package com.nexus.erp.pu.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.nexus.erp.pu.domain.PuContract;
import com.nexus.erp.pu.domain.PuContractLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 采购合同业务对象
 *
 * @author nexus
 * @date 2026-06-15
 */
@Data
@NoArgsConstructor
@AutoMapper(target = PuContract.class, reverseConvertGenerate = false)
public class PuContractBo {

    /** 合同ID */
    private Long contractId;

    /** 合同编号（库表 NOT NULL，手工录入） */
    @NotBlank(message = "合同编号不能为空")
    private String contractNo;

    /** 合同名称 */
    @NotBlank(message = "合同名称不能为空")
    private String contractName;

    /** 供应商ID */
    @NotNull(message = "供应商不能为空")
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

    /** 合同总金额（含税，服务端按行金额汇总重算） */
    private BigDecimal totalAmount;

    /** 税额合计（服务端按行税额汇总重算） */
    private BigDecimal taxAmount;

    /** 不含税总金额（服务端按行不含税金额汇总重算） */
    private BigDecimal amountExcludingTax;

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

    /** 合同明细行列表 */
    private List<PuContractLine> puContractLineList;
}

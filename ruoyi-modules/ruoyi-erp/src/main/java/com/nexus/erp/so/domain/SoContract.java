package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售合同对象 so_contract
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_contract")
public class SoContract extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 合同ID
     */
    @TableId(value = "contract_id")
    private Long contractId;

    /**
     * 合同编号
     */
    private String contractNo;

    /**
     * 合同名称
     */
    private String contractName;

    /**
     * 合同类型
     */
    private String contractType;

    /**
     * 客户ID
     */
    private Long clientId;

    /**
     * 客户名称
     */
    private String clientName;

    /**
     * 签订日期
     */
    private Date signDate;

    /**
     * 生效日期
     */
    private Date effectiveDate;

    /**
     * 失效日期
     */
    private Date expiryDate;

    /**
     * 合同金额
     */
    private BigDecimal totalAmount;

    /**
     * 币种
     */
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
    private String salesRepName;

    /**
     * 合同状态 DRAFT-草稿 APPROVING-审批中 EFFECTIVE-已生效 EXPIRED-已到期 TERMINATED-已终止
     */
    private String status;

    /**
     * 审批流程ID
     */
    private Long selectFlowId;

    /**
     * 备注
     */
    private String remark;

    /**
     * 扩展字段1
     */
    private String attr1;

    /**
     * 扩展字段2
     */
    private String attr2;

    /**
     * 扩展字段3
     */
    private Long attr3;

    /**
     * 扩展字段4
     */
    private Long attr4;

    /**
     * 合同明细行列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<SoContractLine> soContractLineList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

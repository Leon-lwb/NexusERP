package com.nexus.erp.so.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 销售合同业务对象 so_contract
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = SoContract.class, reverseConvertGenerate = false)
public class SoContractBo extends BaseEntity {

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 合同编号
     */
    @NotBlank(message = "合同编号不能为空")
    private String contractNo;

    /**
     * 合同名称
     */
    @NotBlank(message = "合同名称不能为空")
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
     * 合同状态
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
     * 模糊搜索关键字（合同号 / 合同名称 / 客户名称任一匹配）
     * <p>供移动端单输入框搜索使用；与 contractNo、contractName、clientName（均为模糊）为「与」关系，
     * 为空时不参与查询，不影响既有调用方。
     */
    private String keyword;

    /**
     * 合同明细行列表
     */
    private List<SoContractLine> soContractLineList;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}

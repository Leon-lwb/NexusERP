package com.nexus.erp.so.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

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

    /**
     * 正文签署状态（{@code es_sign_document.sign_status}：0未签 1部分签 2已签）
     * <p>so_contract 表中没有这一列，由列表查询批量补充（来源库为主数据源）；
     * 该合同尚无正文时为 null，前端显示占位符。</p>
     */
    private String docSignStatus;

    /**
     * 合同明细行列表。
     * <p>用 {@link SoContractLineVo} 而非实体，避免把 tenantId/delFlag/createBy 等审计字段暴露给前端。</p>
     */
    private List<SoContractLineVo> soContractLineList;
}

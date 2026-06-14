package com.nexus.erp.so.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 销售合同明细行对象 so_contract_line
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("so_contract_line")
public class SoContractLine extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 合同行ID
     */
    @TableId(value = "line_id")
    private Long lineId;

    /**
     * 合同ID
     */
    private Long contractId;

    /**
     * 行号
     */
    private String lineNo;

    /**
     * 物料ID
     */
    private Long materialId;

    /**
     * 物料编码
     */
    private String materialCode;

    /**
     * 物料名称
     */
    private String materialName;

    /**
     * 规格型号
     */
    private String materialSpec;

    /**
     * 数量
     */
    private BigDecimal quantity;

    /**
     * 单位
     */
    private String unit;

    /**
     * 单价
     */
    private BigDecimal unitPrice;

    /**
     * 行金额
     */
    private BigDecimal lineAmount;

    /**
     * 税率
     */
    private BigDecimal taxRate;

    /**
     * 税额
     */
    private BigDecimal taxAmount;

    /**
     * 交货日期
     */
    private Date deliveryDate;

    /**
     * 已发货数量
     */
    private BigDecimal deliveredQty;

    /**
     * 已开票数量
     */
    private BigDecimal invoicedQty;

    /**
     * 行状态 DRAFT-草稿 ACTIVE-生效 COMPLETED-完成 CANCELLED-取消
     */
    private String lineStatus;

    /**
     * 排序
     */
    private Integer sortOrder;

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
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

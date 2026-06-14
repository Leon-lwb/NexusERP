package com.nexus.erp.po.domain;

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
 * 部门请购单明细对象 po_requisition_detail
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("po_requisition_detail")
public class PoRequisitionDetail extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 明细ID
     */
    @TableId(value = "detail_id")
    private Long detailId;

    /**
     * 请购单ID
     */
    private Long requisitionId;

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
     * 物料规格
     */
    private String materialSpec;

    /**
     * 单位
     */
    private String unit;

    /**
     * 需求数量
     */
    private BigDecimal requiredQuantity;

    /**
     * 已发料数量
     */
    private BigDecimal issuedQuantity;

    /**
     * 库存数量
     */
    private BigDecimal stockQuantity;

    /**
     * 缺料数量
     */
    private BigDecimal shortageQuantity;

    /**
     * 需求日期
     */
    private Date requiredDate;

    /**
     * 状态 draft-草稿 approved-已批准 ordered-已转单
     */
    private String status;

    /**
     * 来源类型
     */
    private String sourceType;

    /**
     * 备注
     */
    private String remark;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

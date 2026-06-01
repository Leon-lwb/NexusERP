package com.nexus.erp.pu.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;
import java.util.List;

/**
 * 采购申请实体对象 pu_purchase_requisition
 * <p>
 * 存储采购申请单的主表信息，记录内部发起的采购需求。
 * 采购申请由企业内部人员根据生产计划或库存需求发起，经审批通过后
 * 可转换为采购订单，正式向供应商下达采购指令。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("pu_purchase_requisition")
public class PuPurchaseRequisition extends TenantEntity {

    /** 采购申请ID，主键 */
    @TableId(value = "requisition_id")
    private Long requisitionId;

    /** 采购申请单号，系统自动生成 */
    private String requisitionNo;

    /** 采购申请名称，对本次采购需求的简要描述 */
    private String requisitionName;

    /** 申请人ID，关联sys_user表 */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 申请日期 */
    private Date requisitionDate;

    /** 需求日期，期望到货的截止日期 */
    private Date requiredDate;

    /** 来源类型，如手动录入、MRP运算生成等 */
    private String sourceType;

    /**
     * 申请单状态
     * <ul>
     *   <li>DRAFT - 草稿</li>
     *   <li>SUBMITTED - 已提交</li>
     *   <li>APPROVING - 审批中</li>
     *   <li>APPROVED - 已批准</li>
     *   <li>ORDERED - 已转单</li>
     *   <li>CLOSED - 已关闭</li>
     *   <li>CANCELLED - 已作废</li>
     * </ul>
     */
    private String status;

    /** 选用的审批流ID */
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

    /** 采购申请明细行列表，非数据库字段 */
    @TableField(exist = false)
    private List<PuPurchaseRequisitionLine> puPurchaseRequisitionLineList;

    /** 删除标志（0正常 1已删除） */
    @TableLogic
    private String delFlag;
}

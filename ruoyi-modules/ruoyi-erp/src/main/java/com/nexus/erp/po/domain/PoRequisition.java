package com.nexus.erp.po.domain;

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
 * 部门请购单对象 po_requisition
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("po_requisition")
public class PoRequisition extends TenantEntity {

    private static final long serialVersionUID = 1L;

    /**
     * 请购单ID
     */
    @TableId(value = "requisition_id")
    private Long requisitionId;

    /**
     * 请购单号
     */
    private String requisitionNo;

    /**
     * 状态 draft-草稿 submitted-已提交 approved-已批准 rejected-已拒绝 closed-已关闭
     */
    private String status;

    /**
     * 部门
     */
    private String departmentName;

    /**
     * 申请人
     */
    private String applicantName;

    /**
     * 需求日期
     */
    private Date requiredDate;

    /**
     * 备注
     */
    private String remark;

    /**
     * 请购单明细列表（非数据库字段）
     */
    @TableField(exist = false)
    private List<PoRequisitionDetail> poRequisitionDetailList;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

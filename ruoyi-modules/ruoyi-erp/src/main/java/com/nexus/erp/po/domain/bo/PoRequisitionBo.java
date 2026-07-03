package com.nexus.erp.po.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.po.domain.PoRequisition;
import com.nexus.erp.po.domain.PoRequisitionDetail;

import java.util.Date;
import java.util.List;

/**
 * 部门请购单业务对象 po_requisition
 *
 * @author nexus
 * @date 2026-06-14
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PoRequisition.class, reverseConvertGenerate = false)
public class PoRequisitionBo extends BaseEntity {

    /**
     * 请购单ID
     */
    private Long requisitionId;

    /**
     * 请购单号
     */
    @NotBlank(message = "请购单号不能为空")
    private String requisitionNo;

    /**
     * 状态
     */
    private String status;

    /**
     * 部门ID（关联sys_dept）
     */
    private Long deptId;

    /**
     * 部门名称
     */
    private String deptName;

    /**
     * 申请人ID（关联sys_user）
     */
    private Long applicantId;

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
     * 请购单明细列表
     */
    private List<PoRequisitionDetail> poRequisitionDetailList;

    /**
     * 批量删除ID集合
     */
    private Long[] ids;
}

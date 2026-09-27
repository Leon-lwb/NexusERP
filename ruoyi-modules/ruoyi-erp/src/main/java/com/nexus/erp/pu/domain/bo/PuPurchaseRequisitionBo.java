package com.nexus.erp.pu.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;

import java.util.Date;
import java.util.List;

/**
 * 采购申请业务对象 BO
 * <p>
 * 用于采购申请的前后端数据交互，承载新增、修改、查询等操作的请求参数。
 * 与 {@link PuPurchaseRequisition} 实体通过 MapStruct 自动映射转换。
 * 包含申请头信息和明细行列表 {@link #puPurchaseRequisitionLineList}。
 *
 * @author nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = PuPurchaseRequisition.class, reverseConvertGenerate = false)
public class PuPurchaseRequisitionBo extends BaseEntity {

    @NotNull(message = "采购申请ID不能为空", groups = {com.nexus.common.core.validate.EditGroup.class})
    private Long requisitionId;

    @NotBlank(message = "采购申请单号不能为空", groups = {com.nexus.common.core.validate.AddGroup.class, com.nexus.common.core.validate.EditGroup.class})
    private String requisitionNo;

    /** 采购申请名称 */
    private String requisitionName;

    /** 申请人ID */
    private Long applicantId;

    /** 申请人姓名 */
    private String applicantName;

    /** 采购部门ID（关联sys_dept） */
    private Long deptId;

    /** 采购部门名称 */
    private String deptName;

    /** 申请日期 */
    private Date requisitionDate;

    /** 需求日期 */
    private Date requiredDate;

    /** 来源类型 */
    private String sourceType;

    /** 申请单状态 */
    private String status;

    /** AI决策运行ID */
    private Long decisionRunId;

    /** AI决策动作ID */
    private Long decisionActionId;

    /** 租户内决策幂等键 */
    private String decisionIdempotencyKey;

    /** 选用的审批流ID */
    private Long selectFlowId;

    /** 乐观锁版本 */
    private Integer version = 0;

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

    /** 采购申请明细行列表 */
    private List<PuPurchaseRequisitionLine> puPurchaseRequisitionLineList;
}

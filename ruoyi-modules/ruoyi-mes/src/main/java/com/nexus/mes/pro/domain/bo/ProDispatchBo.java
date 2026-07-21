package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProDispatch;

import java.math.BigDecimal;

/**
 * MES派工单业务对象 pro_dispatch
 * <p>属于 MES 生产管理模块，承载 工序派工 的增改入参。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProDispatch.class, reverseConvertGenerate = false)
public class ProDispatchBo extends BaseEntity {

    /** 派工单ID */
    private Long dispatchId;

    /** 派工单编号 */
    private String dispatchNo;

    /** 关联MES工单ID */
    @NotNull(message = "MES工单ID不能为空")
    private Long mesWoId;

    /** 关联流转卡ID */
    private Long cardId;

    /** 关联流转卡工序记录ID */
    @NotNull(message = "工序记录ID不能为空")
    private Long stepRecordId;

    /** 工序ID */
    private Long processId;

    /** 工作中心ID */
    private Long workcenterId;

    /** 操作员ID */
    private Long operatorId;

    /** 操作员姓名 */
    private String operatorName;

    /** 派工数量 */
    @NotNull(message = "派工数量不能为空")
    @DecimalMin(value = "0.0001", message = "派工数量必须大于0")
    private BigDecimal plannedQty;

    /** 派工状态 */
    private String dispatchStatus;

    /** 计划开始时间 */
    private java.util.Date planStartTime;

    /** 计划结束时间 */
    private java.util.Date planEndTime;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

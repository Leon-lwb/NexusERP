package com.nexus.mes.pro.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.pro.domain.ProWorkOrder;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工单业务对象 pro_work_order
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = ProWorkOrder.class, reverseConvertGenerate = false)
public class ProWorkOrderBo extends BaseEntity {

    /** MES工单ID */
    private Long mesWoId;

    /** 关联ERP工单ID */
    private Long erpWoId;

    /** MES工单编号 */
    @NotBlank(message = "工单编号不能为空")
    private String woNo;

    /** 产品物料ID */
    @NotNull(message = "产品物料ID不能为空")
    private Long materialId;

    /** 产品编码 */
    private String materialCode;

    /** 产品名称 */
    private String materialName;

    /** 计划数量 */
    @NotNull(message = "计划数量不能为空")
    @DecimalMin(value = "0.0001", message = "计划数量必须大于0")
    private BigDecimal plannedQty;

    /** 单位ID */
    private Long unitId;

    /** 单位名称 */
    private String unitName;

    /** 计划开工日期 */
    private Date planStartDate;

    /** 计划完工日期 */
    private Date planEndDate;

    /** 车间ID */
    private Long workshopId;

    /** 车间名称 */
    private String workshopName;

    /** BOM ID */
    private Long bomId;

    /** 工艺路线ID */
    private Long routingId;

    /** 工单状态 */
    private String woStatus;

    /** 优先级 */
    private Integer priority;

    /** 备注 */
    private String remark;

    /** 已选审批流程模板ID */
    private Long selectFlowId;

    /** 批量删除ID集合 */
    private Long[] ids;
}

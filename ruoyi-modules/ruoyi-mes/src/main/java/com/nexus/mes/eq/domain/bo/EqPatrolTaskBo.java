package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqPatrolTask;

import java.util.Date;

/**
 * 属于MES 设备管理模块，巡检任务业务对象（新增/编辑入参）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqPatrolTask.class, reverseConvertGenerate = false)
public class EqPatrolTaskBo extends BaseEntity {
    private Long taskId;
    @NotNull(message = "路线ID不能为空")
    private Long routeId;
    @NotNull(message = "计划日期不能为空")
    private Date planDate;
    private Long assignedToId;
    private String assignedToName;
    private String status;
    private String remark;
    private Long[] ids;
}

package com.nexus.mes.eq.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.eq.domain.EqMaintenanceRecord;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES设备保养执行记录业务对象 eq_maintenance_record
 *
 * <p>属于MES 设备管理模块，保养记录业务对象（新增/编辑入参）。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = EqMaintenanceRecord.class, reverseConvertGenerate = false)
public class EqMaintenanceRecordBo extends BaseEntity {

    /** 记录ID */
    private Long recordId;

    /** 关联保养计划ID(可空, 手动保养无计划) */
    private Long planId;

    /** 设备ID */
    @NotNull(message = "设备ID不能为空")
    private Long equipmentId;

    /** 保养类型(冗余自计划) */
    private String maintenanceType;

    /** 执行人ID(新增/完成保养时自动填充当前用户) */
    private Long executorId;

    /** 执行人姓名 */
    private String executorName;

    /** 执行时间 */
    private Date executeTime;

    /** 保养内容/执行情况 */
    private String content;

    /** 结果: NORMAL/ABNORMAL */
    private String result;

    /** 保养占用时长(分钟), 供 OEE 的 maintenance_minutes 真实汇总(MES-EQ-03) */
    private BigDecimal durationMinutes;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

package com.nexus.mes.tg.domain.bo;

import io.github.linpeilie.annotations.AutoMapper;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;
import com.nexus.common.mybatis.core.domain.BaseEntity;
import com.nexus.mes.tg.domain.TgMaintenanceRecord;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装保养记录业务对象 tg_maintenance_record
 * <p>新增保养记录时,若 maintainType=PLANNED 且关联工装处于 MAINTENANCE,
 * 将触发工装归零(used_count/used_hours=0)并恢复 IDLE。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@AutoMapper(target = TgMaintenanceRecord.class, reverseConvertGenerate = false)
public class TgMaintenanceRecordBo extends BaseEntity {

    /** 记录ID */
    private Long recordId;

    /** 工装ID */
    @NotNull(message = "工装ID不能为空")
    private Long toolingId;

    /** 关联计划ID */
    private Long planId;

    /** 保养类型: PLANNED/REPAIR */
    private String maintainType;

    /** 保养时间(留空取当前时间) */
    private Date maintainTime;

    /** 保养人ID */
    private Long maintainerId;

    /** 保养人姓名(冗余) */
    private String maintainerName;

    /** 保养前次数(留空则自动取工装当前 used_count) */
    private Integer usedCountBefore;

    /** 保养后次数(归零,默认0) */
    private Integer usedCountAfter;

    /** 保养前时长(留空则自动取工装当前 used_hours) */
    private BigDecimal usedHoursBefore;

    /** 保养后时长(归零,默认0) */
    private BigDecimal usedHoursAfter;

    /** 保养内容 */
    private String description;

    /** 保养成本 */
    private BigDecimal cost;

    /** 备注 */
    private String remark;

    /** 批量删除ID集合 */
    private Long[] ids;
}

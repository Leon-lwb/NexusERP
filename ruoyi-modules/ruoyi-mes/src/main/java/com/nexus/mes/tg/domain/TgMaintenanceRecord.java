package com.nexus.mes.tg.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.math.BigDecimal;
import java.util.Date;

/**
 * MES工装保养记录对象 tg_maintenance_record
 * <p>保养类型: PLANNED(计划保养) / REPAIR(维修)</p>
 * <p>归零: 保养完成后 used_count_after=0, used_hours_after=0, 同步归零 tg_tooling.used_*</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("tg_maintenance_record")
public class TgMaintenanceRecord extends TenantEntity {

    /** 记录ID(主键) */
    @TableId(value = "record_id")
    private Long recordId;

    /** 工装ID */
    private Long toolingId;

    /** 关联计划ID */
    private Long planId;

    /** 保养类型: PLANNED/REPAIR */
    private String maintainType;

    /** 保养时间 */
    private Date maintainTime;

    /** 保养人ID */
    private Long maintainerId;

    /** 保养人姓名(冗余) */
    private String maintainerName;

    /** 保养前次数 */
    private Integer usedCountBefore;

    /** 保养后次数(归零=0) */
    private Integer usedCountAfter;

    /** 保养前时长 */
    private BigDecimal usedHoursBefore;

    /** 保养后时长(归零=0) */
    private BigDecimal usedHoursAfter;

    /** 保养内容 */
    private String description;

    /** 保养成本 */
    private BigDecimal cost;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

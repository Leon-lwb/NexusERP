package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES设备保养执行记录对象 eq_maintenance_record
 * <p>
 * 记录每次保养执行情况, 可关联保养计划(plan_id)或手动保养(无计划)。
 * 保养计划到期完成时自动写入一条记录, 也可由人工手动录入。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_maintenance_record")
public class EqMaintenanceRecord extends TenantEntity {

    /** 记录ID(主键) */
    @TableId(value = "record_id")
    private Long recordId;

    /** 关联保养计划ID(可空, 手动保养无计划) */
    private Long planId;

    /** 设备ID */
    private Long equipmentId;

    /** 保养类型(冗余自计划) */
    private String maintenanceType;

    /** 执行人ID */
    private Long executorId;

    /** 执行人姓名 */
    private String executorName;

    /** 执行时间 */
    private Date executeTime;

    /** 保养内容/执行情况 */
    private String content;

    /** 结果: NORMAL/ABNORMAL */
    private String result;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

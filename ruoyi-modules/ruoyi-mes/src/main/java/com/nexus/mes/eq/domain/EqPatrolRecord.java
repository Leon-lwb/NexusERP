package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES巡检记录对象 eq_patrol_record
 *
 * <p>属于MES 设备管理模块，巡检记录持久化实体。</p>
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("eq_patrol_record")
public class EqPatrolRecord extends TenantEntity {

    @TableId(value = "record_id")
    private Long recordId;

    /** 关联任务ID(可空, 手动巡检无任务) */
    private Long taskId;

    /** 关联路线ID(冗余) */
    private Long routeId;

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码(冗余) */
    private String equipmentCode;

    /** 设备名称(冗余) */
    private String equipmentName;

    /** 检查项目 */
    private String checkItem;

    /** 实际值/读数 */
    private String actualValue;

    /** 结果: NORMAL/ABNORMAL */
    private String result;

    /** 照片ossId */
    private String photoOssId;

    /** 执行人ID */
    private Long executorId;

    /** 执行人姓名 */
    private String executorName;

    /** 执行时间 */
    private Date executeTime;

    /** 备注 */
    private String remark;

    @TableLogic
    private String delFlag;
}

package com.nexus.mes.pro.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Date;

/**
 * MES安灯呼叫对象 pro_andon
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("pro_andon")
public class ProAndon extends TenantEntity {

    /** 安灯ID(主键) */
    @TableId(value = "andon_id")
    private Long andonId;

    /** 安灯编号 */
    private String andonNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号(冗余) */
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号(冗余) */
    private String cardNo;

    /** 关联流转卡工序记录ID */
    private Long stepRecordId;

    /** 呼叫类型: QUALITY/MATERIAL/EQUIPMENT/OTHER */
    private String callType;

    /** 呼叫原因 */
    private String callReason;

    /** 安灯状态: CALLED/RESPONDED/RESOLVED/CANCELLED */
    private String andonStatus;

    /** 呼叫人ID */
    private Long callerId;

    /** 呼叫人姓名 */
    private String callerName;

    /** 响应人ID */
    private Long responderId;

    /** 响应人姓名 */
    private String responderName;

    /** 呼叫时间 */
    private Date callTime;

    /** 响应时间 */
    private Date respondTime;

    /** 解决时间 */
    private Date resolveTime;

    /** 持续时长(分钟, 解决时计算) */
    private Integer durationMinutes;

    /** 来源: MANUAL/IoT */
    private String source;

    /** 备注 */
    private String remark;

    /** 删除标志 */
    @TableLogic
    private String delFlag;
}

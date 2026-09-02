package com.nexus.mes.eq.domain;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.util.Date;

/**
 * 设备健康评分记录对象 eq_health_record
 * <p>
 * 预测性维护：基于 IoT 24h 时序数据 EWMA 异常点 + 维修历史的设备健康评分，
 * 每日定时评估，LOW 级且评分下滑时触发安灯。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("eq_health_record")
public class EqHealthRecord extends TenantEntity {

    /**
     * 记录ID
     */
    @TableId(value = "record_id")
    private Long recordId;

    /**
     * 设备ID
     */
    private Long equipmentId;

    /**
     * 设备编码
     */
    private String equipmentCode;

    /**
     * 设备名称
     */
    private String equipmentName;

    /**
     * IoT设备ID
     */
    private Long iotDeviceId;

    /**
     * 健康评分(0-100)
     */
    private Integer score;

    /**
     * 等级: HIGH(>=80)/MEDIUM(>=60)/LOW(<60)
     */
    private String grade;

    /**
     * 24h异常数据点数(z>3)
     */
    private Integer anomalyPoints;

    /**
     * 90天内维修单数
     */
    @TableField("repair_count_90d")
    private Integer repairCount90d;

    /**
     * 未闭环维修单数
     */
    private Integer openRepairCount;

    /**
     * 明细JSON[{propertyCode,propertyName,mean,std,anomalyPoints}]
     */
    private String detailJson;

    /**
     * 前次评分
     */
    private Integer prevScore;

    /**
     * 评分变化(本次-前次)
     */
    private Integer scoreChange;

    /**
     * 评分日期
     */
    private Date recordDate;

    /**
     * 删除标志
     */
    @TableLogic
    private String delFlag;
}

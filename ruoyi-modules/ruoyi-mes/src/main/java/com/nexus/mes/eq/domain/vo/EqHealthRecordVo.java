package com.nexus.mes.eq.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * 设备健康评分记录视图对象
 *
 * @author nexus
 */
@Data
public class EqHealthRecordVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 记录ID
     */
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
     * 等级: HIGH/MEDIUM/LOW
     */
    private String grade;

    /**
     * 24h异常数据点数
     */
    private Integer anomalyPoints;

    /**
     * 90天内维修单数
     */
    private Integer repairCount90d;

    /**
     * 未闭环维修单数
     */
    private Integer openRepairCount;

    /**
     * 明细JSON
     */
    private String detailJson;

    /**
     * 前次评分
     */
    private Integer prevScore;

    /**
     * 评分变化
     */
    private Integer scoreChange;

    /**
     * 评分日期
     */
    private Date recordDate;

    /**
     * 创建时间
     */
    private Date createTime;
}

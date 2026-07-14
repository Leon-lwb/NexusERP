package com.nexus.mes.eq.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;

import com.nexus.common.mybatis.core.domain.BaseEntity;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;

/**
 * MES设备 OEE 看板查询参数
 * <p>
 * 用于看板各图表(概览/趋势/分布/TOP/明细)的统一筛选: 时间范围 + 工作中心 + 设备。
 *
 * @author Nexus
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class EqOeeQueryBo extends BaseEntity {

    /** 统计开始日期(yyyy-MM-dd) */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date startDate;

    /** 统计结束日期(yyyy-MM-dd) */
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date endDate;

    /** 设备ID(精确筛选单台设备) */
    private Long equipmentId;

    /** 工作中心ID(车间级聚合) */
    private Long workcenterId;

    /** 设备类型 */
    private String equipmentType;

    /** 统计粒度: DAY/WEEK/MONTH(默认 DAY) */
    private String granularity;
}

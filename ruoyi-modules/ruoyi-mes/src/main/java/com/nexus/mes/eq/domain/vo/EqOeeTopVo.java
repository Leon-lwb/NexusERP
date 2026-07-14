package com.nexus.mes.eq.domain.vo;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * OEE 停机设备 TOP 视图对象
 *
 * @author Nexus
 */
@Data
public class EqOeeTopVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 设备ID */
    private Long equipmentId;

    /** 设备编码 */
    private String equipmentCode;

    /** 设备名称 */
    private String equipmentName;

    /** 停机时长(分钟) */
    private BigDecimal downtimeMinutes;

    /** 故障次数 */
    private Integer faultCount;

    /** 平均稼动率(0~1) */
    private BigDecimal avgAvailability;
}

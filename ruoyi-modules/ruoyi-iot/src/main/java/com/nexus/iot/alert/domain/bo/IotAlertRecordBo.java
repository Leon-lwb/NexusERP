package com.nexus.iot.alert.domain.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.mybatis.core.domain.BaseEntity;

import java.math.BigDecimal;

/**
 * IoT告警记录查询/操作业务对象。
 * <p>
 * 仅作查询条件与操作入参载体，不参与 MapStruct 转换（无需 @AutoMapper）。
 * 时间区间用字符串承载（{@code yyyy-MM-dd} 或 {@code yyyy-MM-dd HH:mm:ss}），
 * 由 MySQL 对 datetime 列做隐式转换，避免前端仅传日期时反序列化失败。
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class IotAlertRecordBo extends BaseEntity {

    /**
     * 告警ID（确认/恢复时必填）
     */
    private Long alertId;

    /**
     * 设备ID
     */
    private Long deviceId;

    /**
     * 设备编码（模糊）
     */
    private String deviceCode;

    /**
     * 产品ID
     */
    private Long productId;

    /**
     * 属性编码（模糊）
     */
    private String propertyCode;

    /**
     * 状态: ACTIVE/ACKNOWLEDGED/RESOLVED
     */
    private String status;

    /**
     * 告警级别: HIGH/MEDIUM/LOW
     */
    private String alertLevel;

    /**
     * 告警类型
     */
    private String alertType;

    /**
     * 来源: RULE/DEFAULT/ANOMALY
     */
    private String source;

    /**
     * 触发时间起（含）
     */
    private String beginTime;

    /**
     * 触发时间止（含）
     */
    private String endTime;

    /**
     * 确认备注
     */
    private String ackRemark;

    /**
     * 恢复值
     */
    private BigDecimal resolveValue;

    /**
     * 恢复备注
     */
    private String resolveRemark;

    /**
     * 批量操作ID集合
     */
    private Long[] ids;

}

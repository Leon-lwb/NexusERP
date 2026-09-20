package com.nexus.iot.alert.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.iot.alert.domain.IotAlertNotifyLog;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * IoT告警通知投递留痕视图对象 iot_alert_notify_log
 *
 * @author nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = IotAlertNotifyLog.class)
public class IotAlertNotifyLogVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 日志ID
     */
    private Long logId;

    /**
     * 告警ID
     */
    private Long alertId;

    /**
     * 通知渠道: SSE/MES_ANDON
     */
    @ExcelProperty(value = "通知渠道")
    private String channel;

    /**
     * 投递目标
     */
    @ExcelProperty(value = "投递目标")
    private String target;

    /**
     * 投递结果: SUCCESS/FAILED/SKIPPED
     */
    @ExcelProperty(value = "投递结果")
    private String status;

    /**
     * 结果说明
     */
    @ExcelProperty(value = "结果说明")
    private String message;

    /**
     * 创建时间
     */
    @ExcelProperty(value = "创建时间")
    private Date createTime;

}

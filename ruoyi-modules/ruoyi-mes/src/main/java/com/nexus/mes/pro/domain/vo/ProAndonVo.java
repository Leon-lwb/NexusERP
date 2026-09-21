package com.nexus.mes.pro.domain.vo;

import cn.idev.excel.annotation.ExcelIgnoreUnannotated;
import cn.idev.excel.annotation.ExcelProperty;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;
import com.nexus.mes.pro.domain.ProAndon;

import java.io.Serial;
import java.io.Serializable;
import java.util.Date;

/**
 * MES安灯呼叫视图对象 pro_andon
 * <p>属于 MES 生产管理模块，作为 安灯告警 的查询/导出返回结构。</p>
 *
 * @author Nexus
 */
@Data
@ExcelIgnoreUnannotated
@AutoMapper(target = ProAndon.class)
public class ProAndonVo implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 安灯ID */
    @ExcelProperty("安灯ID")
    private Long andonId;

    /** 安灯编号 */
    @ExcelProperty("安灯编号")
    private String andonNo;

    /** 关联MES工单ID */
    private Long mesWoId;

    /** 工单编号 */
    @ExcelProperty("工单编号")
    private String woNo;

    /** 关联流转卡ID */
    private Long cardId;

    /** 流转卡编号 */
    @ExcelProperty("流转卡编号")
    private String cardNo;

    /** 关联流转卡工序记录ID */
    private Long stepRecordId;

    /** 工位编码(工位终端设备通道呼叫时冗余) */
    @ExcelProperty("工位编码")
    private String stationCode;

    /** 呼叫类型 */
    @ExcelProperty("呼叫类型")
    private String callType;

    /** 呼叫原因 */
    @ExcelProperty("呼叫原因")
    private String callReason;

    /** 安灯状态 */
    @ExcelProperty("状态")
    private String andonStatus;

    /** 呼叫人ID */
    private Long callerId;

    /** 呼叫人姓名 */
    @ExcelProperty("呼叫人")
    private String callerName;

    /** 响应人ID */
    private Long responderId;

    /** 响应人姓名 */
    @ExcelProperty("响应人")
    private String responderName;

    /** 呼叫时间 */
    @ExcelProperty("呼叫时间")
    private Date callTime;

    /** 响应时间 */
    private Date respondTime;

    /** 解决时间 */
    private Date resolveTime;

    /** 持续时长(分钟) */
    @ExcelProperty("持续时长(分钟)")
    private Integer durationMinutes;

    /** 来源 */
    @ExcelProperty("来源")
    private String source;

    /** 备注 */
    private String remark;

    /** 呼叫通知被通知人ID(逗号分隔) */
    private String notifyUserIds;

    /** 呼叫通知落库时间 */
    private Date notifyTime;
}

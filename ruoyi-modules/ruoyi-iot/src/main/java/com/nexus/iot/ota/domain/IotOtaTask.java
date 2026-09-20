package com.nexus.iot.ota.domain;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import com.nexus.common.tenant.core.TenantEntity;

import java.util.Date;

/**
 * IoT OTA 固件升级任务对象 iot_ota_task（审计 §4「功能缺失清单」#1 OTA MVP）。
 * <p>
 * 一条记录 = "把某个固件装到某台设备上"的一次尝试：
 * <ul>
 *   <li><b>目标</b>：{@code firmwareId} + {@code targetVersion}（冗余，固件删除后仍可追溯）
 *       + {@code deviceId}/{@code deviceCode}；{@code groupId}/{@code batchNo} 记录批量下发来源；</li>
 *   <li><b>下发留痕</b>：{@code commandId} 指向 {@code iot_device_command}（复用既有下行通道，
 *       本表不重复记录 Topic/载荷）；</li>
 *   <li><b>状态机</b>：见 {@link IotOtaStatus}；{@code progress}/{@code startTime}/{@code finishTime}/
 *       {@code failReason} 由设备回执驱动；</li>
 *   <li><b>幂等</b>：业务幂等键 = {@code (device_id, firmware_id)} 上"不存在进行中任务"，
 *       回执幂等 = 状态机 CAS（终态不可再流转）。</li>
 * </ul>
 *
 * @author nexus
 */
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("iot_ota_task")
public class IotOtaTask extends TenantEntity {

    /**
     * 升级任务ID
     */
    @TableId(value = "task_id")
    private Long taskId;

    /**
     * 固件ID
     */
    private Long firmwareId;

    /**
     * 目标版本（冗余）
     */
    private String targetVersion;

    /**
     * 下发时设备当前版本
     */
    private String currentVersion;

    /**
     * 目标设备ID
     */
    private Long deviceId;

    /**
     * 设备编码（冗余）
     */
    private String deviceCode;

    /**
     * 来源分组ID（按分组批量下发时记录）
     */
    private Long groupId;

    /**
     * 批量下发批次号
     */
    private String batchNo;

    /**
     * 关联下行指令ID（iot_device_command）
     */
    private Long commandId;

    /**
     * 状态: PENDING/SENT/UPGRADING/SUCCESS/FAILED/TIMEOUT/CANCELLED
     */
    private String status;

    /**
     * 升级进度(0-100)
     */
    private Integer progress;

    /**
     * 人工重试次数
     */
    private Integer retryCount;

    /**
     * 指令下发时间
     */
    private Date sentTime;

    /**
     * 设备开始升级时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date finishTime;

    /**
     * 失败/取消原因
     */
    private String failReason;

    /**
     * 最近一次回执的消息ID（Stream entry id，便于取证）
     */
    private String reportMsgId;

    /**
     * 下发人
     */
    private Long operatorId;

    /**
     * 下发人名称
     */
    private String operatorName;

    /**
     * 备注
     */
    private String remark;
}

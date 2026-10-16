package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/** 工程设备，仅保存凭证摘要；实体不得直接作为 HTTP 响应。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_engineering_device")
public class AiEngineeringDevice extends TenantEntity {
    /** 设备编号。 */
    @TableId("device_id")
    private Long deviceId;

    /** 所有者用户编号。 */
    private Long userId;

    /** 设备显示名称。 */
    private String deviceName;

    /** 设备状态。 */
    private String status;

    /** 访问凭证摘要。 */
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude private String accessHash;

    /** 刷新凭证摘要。 */
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String refreshHash;

    /** 凭证代数。 */
    private Long generation;

    /** 访问凭证到期时间。 */
    private LocalDateTime accessExpiresAt;

    /** 刷新凭证到期时间。 */
    private LocalDateTime refreshExpiresAt;

    /** 配对时认证状态内部摘要。 */
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private String credentialFingerprint;

    /** 设备实际能力与版本。 */
    private String capabilities;

    /** 配对允许能力集合。 */
    private String allowedCapabilities;

    /** 最近心跳时间。 */
    private LocalDateTime lastHeartbeat;

    /** 正在执行的任务。 */
    private Long busyTaskId;

    /** 创建时间。 */
    private LocalDateTime createdAt;
}

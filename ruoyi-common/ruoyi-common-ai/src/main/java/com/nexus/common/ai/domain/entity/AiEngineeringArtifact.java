package com.nexus.common.ai.domain.entity;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.nexus.common.tenant.core.TenantEntity;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import java.time.LocalDateTime;

/** 工程产物内部记录，仅确认后可见，对象键不对外暴露。 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("ai_engineering_artifact")
public class AiEngineeringArtifact extends TenantEntity {
    /** 产物编号。 */
    @TableId("artifact_id")
    private Long artifactId;

    /** 产物所有者。 */
    private Long userId;

    /** 所属任务。 */
    private Long taskId;

    /** 所属尝试编号。 */
    private Integer attempt;

    /** 产物类型。 */
    private String artifactType;

    /** 模型材质组。 */
    private String groupId;

    /** 组内相对路径，区分大小写。 */
    private String relativePath;

    /** 显式私有存储配置键。 */
    @JsonIgnore @ToString.Exclude private String service;

    /** 内部私有对象编号。 */
    @JsonIgnore @ToString.Exclude private Long ossId;

    /** 服务端对象键，不对外输出。 */
    @JsonIgnore @ToString.Exclude private String objectKey;

    /** 服务端复核内容摘要。 */
    private String sha256;

    /** 真实字节数。 */
    private Long size;

    /** 验证后媒体类型。 */
    private String mimeType;

    /** 安全显示文件名。 */
    private String originalFilename;

    /** 预留、确认或待清理状态。 */
    private String status;

    /** 服务端格式解析摘要。 */
    private String validationJson;

    /** 创建时间。 */
    private LocalDateTime createdAt;

    /** 未确认对象清理的 CAS 代数，旧进程不得覆盖新的清理者。 */
    private Long cleanupGeneration;

    /** 清理租约或失败后的下次重试时间；过期 CLEANING 可以重新抢占。 */
    private LocalDateTime cleanupExpiresAt;
}

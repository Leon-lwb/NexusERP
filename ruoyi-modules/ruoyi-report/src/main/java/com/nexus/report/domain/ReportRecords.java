package com.nexus.report.domain;

import java.time.Instant;

/** 租户化报表元数据记录，定义、发布、数据集、运行和任务保持固定引用。 */
public final class ReportRecords {
    /** 初始化报表记录并保存所需依赖或不可变值。 */
    private ReportRecords() {}

    /**
     * Catalog，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param parentId 当前契约的parentId配置或状态
     * @param name 契约名称
     * @param owner 资源创建者或当前尝试持有者
     */
    public record Catalog(String id, String parentId, String name, long owner) {}

    /**
     * 定义，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param tenant 租户隔离标识
     * @param code 当前契约的code配置或状态
     * @param name 契约名称
     * @param kind 当前契约的kind配置或状态
     * @param catalogId 当前契约的catalogId配置或状态
     * @param json 固定版本的契约正文
     * @param revision 当前契约的revision配置或状态
     * @param publishedVersion 当前契约的publishedVersion配置或状态
     * @param status 当前契约的status配置或状态
     * @param owner 资源创建者或当前尝试持有者
     * @param updatedAt 当前契约的updatedAt配置或状态
     */
    public record Definition(
            String id,
            String tenant,
            String code,
            String name,
            String kind,
            String catalogId,
            String json,
            long revision,
            String publishedVersion,
            String status,
            long owner,
            Instant updatedAt) {}

    /**
     * 版本，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param reportId 当前契约的reportId配置或状态
     * @param number 当前契约的number配置或状态
     * @param schema 当前契约的schema配置或状态
     * @param engine 当前契约的engine配置或状态
     * @param json 固定版本的契约正文
     * @param checksum 实际内容的SHA-256校验和
     * @param datasetVersion 当前契约的datasetVersion配置或状态
     * @param owner 资源创建者或当前尝试持有者
     * @param createdAt 当前契约的createdAt配置或状态
     */
    public record Version(
            String id,
            String reportId,
            int number,
            int schema,
            String engine,
            String json,
            String checksum,
            String datasetVersion,
            long owner,
            Instant createdAt) {}

    /**
     * 数据集，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param tenant 租户隔离标识
     * @param code 当前契约的code配置或状态
     * @param name 契约名称
     * @param provider 当前契约的provider配置或状态
     * @param publishedVersion 当前契约的publishedVersion配置或状态
     * @param status 当前契约的status配置或状态
     * @param owner 资源创建者或当前尝试持有者
     */
    public record Dataset(
            String id,
            String tenant,
            String code,
            String name,
            String provider,
            String publishedVersion,
            String status,
            long owner) {}

    /**
     * 数据集版本，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param datasetId 当前契约的datasetId配置或状态
     * @param number 当前契约的number配置或状态
     * @param provider 当前契约的provider配置或状态
     * @param providerVersion 当前契约的providerVersion配置或状态
     * @param json 固定版本的契约正文
     * @param checksum 实际内容的SHA-256校验和
     * @param owner 资源创建者或当前尝试持有者
     */
    public record DatasetVersion(
            String id,
            String datasetId,
            int number,
            String provider,
            int providerVersion,
            String json,
            String checksum,
            long owner) {}

    /**
     * Grant，负责当前模块对应的契约或处理职责。
     *
     * @param subjectType 受控授权主体类型
     * @param subjectId 当前租户主体标识
     * @param action 受控资源操作
     */
    public record Grant(String subjectType, String subjectId, String action) {}

    /**
     * Run，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param tenant 租户隔离标识
     * @param actor 执行用户
     * @param reportId 当前契约的reportId配置或状态
     * @param version 当前契约的version配置或状态
     * @param mode 当前契约的mode配置或状态
     * @param state 任务或资源状态
     * @param parameters 固定参数契约
     * @param scopeHash 当前契约的scopeHash配置或状态
     * @param snapshot 当前契约的snapshot配置或状态
     * @param plan 当前契约的plan配置或状态
     * @param rows 精确类型的数据行
     * @param dataTime 当前契约的dataTime配置或状态
     * @param expires 当前契约的expires配置或状态
     * @param error 当前契约的error配置或状态
     */
    public record Run(
            String id,
            String tenant,
            long actor,
            String reportId,
            String version,
            String mode,
            String state,
            String parameters,
            String scopeHash,
            String snapshot,
            String plan,
            long rows,
            Instant dataTime,
            Instant expires,
            String error) {}

    /**
     * 导出任务，负责当前模块对应的契约或处理职责。
     *
     * @param id 固定标识，按字符串传输以保留完整精度
     * @param tenant 租户隔离标识
     * @param actor 执行用户
     * @param runId 当前契约的runId配置或状态
     * @param format 受控格式
     * @param requestKey 当前契约的requestKey配置或状态
     * @param requestHash 当前契约的requestHash配置或状态
     * @param state 任务或资源状态
     * @param attempts 当前契约的attempts配置或状态
     * @param leaseOwner 当前契约的leaseOwner配置或状态
     * @param leaseUntil 当前契约的leaseUntil配置或状态
     * @param cancelled 当前契约的cancelled配置或状态
     * @param file 当前契约的file配置或状态
     * @param bytes 保留或实际占用的字节数
     * @param expires 当前契约的expires配置或状态
     * @param error 当前契约的error配置或状态
     * @param phase 当前契约的phase配置或状态
     * @param processedRows 当前契约的processedRows配置或状态
     * @param totalRows 当前契约的totalRows配置或状态
     * @param retryRound 当前契约的retryRound配置或状态
     * @param heartbeat 当前契约的heartbeat配置或状态
     */
    public record ExportJob(
            String id,
            String tenant,
            long actor,
            String runId,
            String format,
            String requestKey,
            String requestHash,
            String state,
            int attempts,
            String leaseOwner,
            Instant leaseUntil,
            boolean cancelled,
            String file,
            Long bytes,
            Instant expires,
            String error,
            String phase,
            long processedRows,
            Long totalRows,
            int retryRound,
            Instant heartbeat) {
        /**
         * 初始化导出任务并保存所需依赖或不可变值。
         *
         * @param id 资源标识
         * @param tenant 当前租户标识
         * @param actor 当前服务端身份
         * @param runId 当前操作的 {@code runId} 输入
         * @param format 当前操作的 {@code format} 输入
         * @param requestKey 当前操作的 {@code requestKey} 输入
         * @param requestHash 当前操作的 {@code requestHash} 输入
         * @param state 目标状态
         * @param attempts 当前操作的 {@code attempts} 输入
         * @param leaseOwner 当前操作的 {@code leaseOwner} 输入
         * @param leaseUntil 当前操作的 {@code leaseUntil} 输入
         * @param cancelled 当前操作的 {@code cancelled} 输入
         * @param file 当前操作的 {@code file} 输入
         * @param bytes 受控字节数据
         * @param expires 资源到期时点
         * @param error 当前操作的 {@code error} 输入
         */
        public ExportJob(
                String id,
                String tenant,
                long actor,
                String runId,
                String format,
                String requestKey,
                String requestHash,
                String state,
                int attempts,
                String leaseOwner,
                Instant leaseUntil,
                boolean cancelled,
                String file,
                Long bytes,
                Instant expires,
                String error) {
            this(
                    id,
                    tenant,
                    actor,
                    runId,
                    format,
                    requestKey,
                    requestHash,
                    state,
                    attempts,
                    leaseOwner,
                    leaseUntil,
                    cancelled,
                    file,
                    bytes,
                    expires,
                    error,
                    state,
                    0,
                    null,
                    0,
                    null);
        }
    }
}

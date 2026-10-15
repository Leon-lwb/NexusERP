package com.nexus.report.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 可信服务端报表配置，限制工作线程、容量、保留期、字体和禁用引擎。 */
@ConfigurationProperties(prefix = "nexus.report")
public class ReportProperties {
    /** 保存当前 {@code disabledEngines} 值。 */
    private java.util.Set<String> disabledEngines = java.util.Set.of();

    /**
     * 读取DisabledEngines配置。
     *
     * @return 当前处理返回的固定结果
     */
    public java.util.Set<String> getDisabledEngines() {
        return disabledEngines;
    }

    /**
     * 设置并校验DisabledEngines值。
     *
     * @param values 绑定值
     */
    public void setDisabledEngines(java.util.Set<String> values) {
        disabledEngines = java.util.Set.copyOf(values);
    }

    /** 保存当前 {@code artifactRoot} 值。 */
    private String artifactRoot = "./data/report";

    /** 保存当前 {@code fontPath} 值。 */
    private String fontPath;

    /** 保存当前 {@code workers} 值。 */
    private int workers = 4;

    /** 保存当前 {@code retentionHours} 值。 */
    private int retentionHours = 24;

    /** 保存当前 {@code taskSeconds} 值。 */
    private int taskSeconds = 240;

    /** max产物Bytes。 */
    private long maxArtifactBytes = 100L * 1024 * 1024;

    /**
     * 读取产物Root配置。
     *
     * @return 当前处理返回的固定结果
     */
    public String getArtifactRoot() {
        return artifactRoot;
    }

    /**
     * 设置并校验产物Root值。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    public void setArtifactRoot(String value) {
        artifactRoot = value;
    }

    /**
     * 读取字体Path配置。
     *
     * @return 当前处理返回的固定结果
     */
    public String getFontPath() {
        return fontPath;
    }

    /**
     * 设置并校验字体Path值。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    public void setFontPath(String value) {
        fontPath = value;
    }

    /**
     * 读取Workers配置。
     *
     * @return 当前处理返回的固定结果
     */
    public int getWorkers() {
        return workers;
    }

    /**
     * 设置并校验Workers值。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    public void setWorkers(int value) {
        if (value < 1 || value > 8) throw new IllegalArgumentException("Report worker range 1-8");
        workers = value;
    }

    /**
     * 读取RetentionHours配置。
     *
     * @return 当前处理返回的固定结果
     */
    public int getRetentionHours() {
        return retentionHours;
    }

    /**
     * 设置并校验RetentionHours值。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    public void setRetentionHours(int value) {
        if (value < 1 || value > 168)
            throw new IllegalArgumentException("Report retention range 1-168 hours");
        retentionHours = value;
    }

    /**
     * 读取TaskSeconds配置。
     *
     * @return 当前处理返回的固定结果
     */
    public int getTaskSeconds() {
        return taskSeconds;
    }

    /**
     * 设置并校验TaskSeconds值。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    public void setTaskSeconds(int value) {
        if (value < 30 || value > 600)
            throw new IllegalArgumentException("Report task timeout range 30-600 seconds");
        taskSeconds = value;
    }

    /**
     * 读取Max产物Bytes配置。
     *
     * @return 当前处理返回的固定结果
     */
    public long getMaxArtifactBytes() {
        return maxArtifactBytes;
    }

    /**
     * 设置并校验Max产物Bytes值。
     *
     * @param value 当前操作的 {@code value} 输入
     */
    public void setMaxArtifactBytes(long value) {
        if (value < 1024 * 1024 || value > 200L * 1024 * 1024)
            throw new IllegalArgumentException("Report artifact size range 1-200 MB");
        maxArtifactBytes = value;
    }
}

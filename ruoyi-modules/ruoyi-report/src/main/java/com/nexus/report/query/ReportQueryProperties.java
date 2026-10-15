package com.nexus.report.query;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 查询目录使用独立服务器只读数据源，默认停用且不复用ERP写账户。 */
@ConfigurationProperties(prefix = "nexus.report.query")
public class ReportQueryProperties {
    /** 保存当前enabled状态或配置。 */
    private boolean enabled;

    /** 保存当前jdbcUrl状态或配置。 */
    private String jdbcUrl;

    /** 保存当前username状态或配置。 */
    private String username;

    /** 保存当前password状态或配置。 */
    private String password;

    /** 保存当前timeoutSeconds状态或配置。 */
    private int timeoutSeconds = 10;

    /**
     * 判断只读查询是否明确启用。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * 显式启停只读查询。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setEnabled(boolean value) {
        enabled = value;
    }

    /**
     * 读取可信服务器数据源地址。
     *
     * @return 当前受控处理的固定结果
     */
    public String getJdbcUrl() {
        return jdbcUrl;
    }

    /**
     * 设置可信服务器数据源地址，不接受模板输入。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setJdbcUrl(String value) {
        jdbcUrl = value;
    }

    /**
     * 读取独立SELECT账户。
     *
     * @return 当前受控处理的固定结果
     */
    public String getUsername() {
        return username;
    }

    /**
     * 设置独立SELECT账户。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setUsername(String value) {
        username = value;
    }

    /**
     * 读取仅服务器使用的凭证。
     *
     * @return 当前受控处理的固定结果
     */
    public String getPassword() {
        return password;
    }

    /**
     * 设置仅服务器使用的凭证，不写日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setPassword(String value) {
        password = value;
    }

    /**
     * 读取有界查询超时。
     *
     * @return 当前受控处理的固定结果
     */
    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    /**
     * 校验查询超时为1至60秒。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setTimeoutSeconds(int value) {
        if (value < 1 || value > 60) throw new IllegalArgumentException("只读查询超时范围无效");
        timeoutSeconds = value;
    }
}

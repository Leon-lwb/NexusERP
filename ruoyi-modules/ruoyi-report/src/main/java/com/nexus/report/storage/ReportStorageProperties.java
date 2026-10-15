package com.nexus.report.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 私有共享存储和硬配额配置，不复用公共OSS外链或业务写库账户。 */
@ConfigurationProperties(prefix = "nexus.report.storage")
public final class ReportStorageProperties {
    /** 本地或共享S3存储类型。 */
    private String backend = "LOCAL";

    /**
     * 获取本地或共享S3存储类型。
     *
     * @return 当前受控处理的固定结果
     */
    public String getBackend() {
        return backend;
    }

    /**
     * 设置本地或共享S3存储类型，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setBackend(String value) {
        backend = value;
    }

    /** 显式启用主库产物清单与硬配额。 */
    private boolean manifestEnabled = false;

    /**
     * 获取显式启用主库产物清单与硬配额。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean isManifestEnabled() {
        return manifestEnabled;
    }

    /**
     * 设置显式启用主库产物清单与硬配额，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setManifestEnabled(boolean value) {
        manifestEnabled = value;
    }

    /** 共享逻辑命名空间，不绑定主机。 */
    private String storageId = null;

    /**
     * 获取共享逻辑命名空间，不绑定主机。
     *
     * @return 当前受控处理的固定结果
     */
    public String getStorageId() {
        return storageId;
    }

    /**
     * 设置共享逻辑命名空间，不绑定主机，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setStorageId(String value) {
        storageId = value;
    }

    /** 独立私有S3服务地址。 */
    private String endpoint = null;

    /**
     * 获取独立私有S3服务地址。
     *
     * @return 当前受控处理的固定结果
     */
    public String getEndpoint() {
        return endpoint;
    }

    /**
     * 设置独立私有S3服务地址，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setEndpoint(String value) {
        endpoint = value;
    }

    /** 预先创建且验证私有的报表bucket。 */
    private String bucket = null;

    /**
     * 获取预先创建且验证私有的报表bucket。
     *
     * @return 当前受控处理的固定结果
     */
    public String getBucket() {
        return bucket;
    }

    /**
     * 设置预先创建且验证私有的报表bucket，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setBucket(String value) {
        bucket = value;
    }

    /** 独立报表对象前缀。 */
    private String prefix = "nexus-report/v1";

    /**
     * 获取独立报表对象前缀。
     *
     * @return 当前受控处理的固定结果
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * 设置独立报表对象前缀，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setPrefix(String value) {
        prefix = value;
    }

    /** 从环境注入的私有存储访问标识。 */
    private String accessKey = null;

    /**
     * 获取从环境注入的私有存储访问标识。
     *
     * @return 当前受控处理的固定结果
     */
    public String getAccessKey() {
        return accessKey;
    }

    /**
     * 设置从环境注入的私有存储访问标识，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setAccessKey(String value) {
        accessKey = value;
    }

    /** 从环境注入的私有存储密钥。 */
    private String secretKey = null;

    /**
     * 获取从环境注入的私有存储密钥。
     *
     * @return 当前受控处理的固定结果
     */
    public String getSecretKey() {
        return secretKey;
    }

    /**
     * 设置从环境注入的私有存储密钥，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setSecretKey(String value) {
        secretKey = value;
    }

    /** S3签名区域。 */
    private String region = "us-east-1";

    /**
     * 获取S3签名区域。
     *
     * @return 当前受控处理的固定结果
     */
    public String getRegion() {
        return region;
    }

    /**
     * 设置S3签名区域，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setRegion(String value) {
        region = value;
    }

    /** 只在明确隔离环境允许HTTP。 */
    private boolean allowInsecure = false;

    /**
     * 获取只在明确隔离环境允许HTTP。
     *
     * @return 当前受控处理的固定结果
     */
    public boolean isAllowInsecure() {
        return allowInsecure;
    }

    /**
     * 设置只在明确隔离环境允许HTTP，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setAllowInsecure(boolean value) {
        allowInsecure = value;
    }

    /** 主库统一的全局硬字节预算。 */
    private long globalBytes = 16L * 1024 * 1024 * 1024;

    /**
     * 获取主库统一的全局硬字节预算。
     *
     * @return 当前受控处理的固定结果
     */
    public long getGlobalBytes() {
        return globalBytes;
    }

    /**
     * 设置主库统一的全局硬字节预算，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setGlobalBytes(long value) {
        globalBytes = value;
    }

    /** 每租户硬字节预算。 */
    private long tenantBytes = 2L * 1024 * 1024 * 1024;

    /**
     * 获取每租户硬字节预算。
     *
     * @return 当前受控处理的固定结果
     */
    public long getTenantBytes() {
        return tenantBytes;
    }

    /**
     * 设置每租户硬字节预算，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setTenantBytes(long value) {
        tenantBytes = value;
    }

    /** 全局对象及暂存文件数量预算。 */
    private long globalObjects = 10000;

    /**
     * 获取全局对象及暂存文件数量预算。
     *
     * @return 当前受控处理的固定结果
     */
    public long getGlobalObjects() {
        return globalObjects;
    }

    /**
     * 设置全局对象及暂存文件数量预算，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setGlobalObjects(long value) {
        globalObjects = value;
    }

    /** 每租户对象及暂存文件数量预算。 */
    private long tenantObjects = 2000;

    /**
     * 获取每租户对象及暂存文件数量预算。
     *
     * @return 当前受控处理的固定结果
     */
    public long getTenantObjects() {
        return tenantObjects;
    }

    /**
     * 设置每租户对象及暂存文件数量预算，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setTenantObjects(long value) {
        tenantObjects = value;
    }

    /** 单用户等待队列容量。 */
    private int queuedPerUser = 50;

    /**
     * 获取单用户等待队列容量。
     *
     * @return 当前受控处理的固定结果
     */
    public int getQueuedPerUser() {
        return queuedPerUser;
    }

    /**
     * 设置单用户等待队列容量，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setQueuedPerUser(int value) {
        queuedPerUser = value;
    }

    /** 租户等待队列容量。 */
    private int queuedPerTenant = 100;

    /**
     * 获取租户等待队列容量。
     *
     * @return 当前受控处理的固定结果
     */
    public int getQueuedPerTenant() {
        return queuedPerTenant;
    }

    /**
     * 设置租户等待队列容量，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setQueuedPerTenant(int value) {
        queuedPerTenant = value;
    }

    /** 全局等待队列容量。 */
    private int queuedGlobal = 1000;

    /**
     * 获取全局等待队列容量。
     *
     * @return 当前受控处理的固定结果
     */
    public int getQueuedGlobal() {
        return queuedGlobal;
    }

    /**
     * 设置全局等待队列容量，凭证字段不进入日志。
     *
     * @param value 当前契约的value配置或状态
     */
    public void setQueuedGlobal(int value) {
        queuedGlobal = value;
    }
}

package com.nexus.common.trace.port;

import com.nexus.common.trace.*;

/**
 * 正式身份注册端口，由ERP实现，跨库编排不得持有MES行锁调用。
 */
public interface SerialIdentityPort {
    /**
     * 按原命令原子预留正式身份，返回初始登记状态；重复命令核验指纹后返回原号段。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param command 包含原命令及来源上下文的不可变请求。
     * @return 完整的正式身份预留集合，初始登记不会直接生成在库事实。
     */
    SerialReservation reserve(String tenantId, SerialReservationCommand command);

    /**
     * 确认预留身份已经完成来源模块本地创建，保持正式号码和原请求指纹不变。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param commandId 首次业务命令号；重试保持原值并核验请求指纹。
     * @param fingerprint 原请求内容指纹，同命令不同内容必须拒绝。
     */
    void confirm(String tenantId, String commandId, String fingerprint);

    /**
     * 在认证租户内读取稳定正式身份及其编码，不以模块本地主键替代。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityId ERP 序列号主档主键，作为跨模块稳定实物身份。
     * @return 认证租户内的正式身份；不存在时按接口约定保留缺失。
     */
    SerialIdentity find(String tenantId, Long identityId);
}

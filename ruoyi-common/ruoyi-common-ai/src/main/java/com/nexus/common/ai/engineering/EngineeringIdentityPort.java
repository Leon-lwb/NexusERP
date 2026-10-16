package com.nexus.common.ai.engineering;

/** 工程所有者实时身份端口，由系统模块读取 master 用户、租户和权限。 */
public interface EngineeringIdentityPort {
    /**
     * 精确核对租户和用户归属，不使用永久权限快照，认证摘要仅供服务端比较。
     *
     * @param tenantId 保留前导零的服务端租户标识
     * @param userId 服务端认证的用户编号
     * @return 当前启用状态、租户状态、内部认证摘要和实时权限；不存在时返回禁用状态
     * @throws RuntimeException 当前身份无法可靠读取时拒绝操作，不能回退到旧授权快照
     */
    EngineeringOwnerStatus resolve(String tenantId, Long userId);
}

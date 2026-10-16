package com.nexus.common.ai.engineering;

import java.util.Set;

/**
 * 当前所有者状态；认证摘要仅供服务端比较，禁止作为接口响应。
 *
 * @param enabled 用户当前是否启用且未删除
 * @param tenantEnabled 租户当前是否启用且未到期
 * @param credentialFingerprint 仅服务端比较的密码和认证状态摘要，绝不向客户端输出
 * @param permissions 当前有效权限集合，不作为永久快照授权
 */
public record EngineeringOwnerStatus(
        boolean enabled,
        boolean tenantEnabled,
        String credentialFingerprint,
        Set<String> permissions) {
    public EngineeringOwnerStatus {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    @Override
    public String toString() {
        return "EngineeringOwnerStatus[内部认证状态]";
    }
}

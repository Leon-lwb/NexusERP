package com.nexus.common.ai.engineering;

import java.util.Set;

/**
 * 服务端确认的操作者，身份不能取自模型或连接器参数。
 *
 * @param tenantId 保留文本前导零的租户标识
 * @param userId 服务端认证的用户编号
 * @param permissions 当前有效权限集合，不作为永久快照授权
 */
public record EngineeringActor(String tenantId, Long userId, Set<String> permissions) {
    public EngineeringActor {
        permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
    }

    /** 核对当前身份是否拥有指定权限，支持系统超级权限。 */
    public boolean permits(String permission) {
        return permissions.contains("*:*:*") || permissions.contains(permission);
    }
}

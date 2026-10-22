package com.nexus.common.trace.port;

import java.util.Map;
import java.util.Set;

/**
 * 返回本模块有权查询且已映射的实物身份，统一解析不得扩大模块访问范围。
 */
public interface SerialVisibilityPort {
    /**
     * 按当前模块数据权限过滤稳定身份候选，避免跨业务范围泄露候选。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityIds 待核验的跨模块稳定身份集合。
     * @return 当前调用方有权查看的稳定身份集合。
     */
    Set<Long> visibleIdentities(String tenantId, Set<Long> identityIds);

    /**
     * 将已授权的稳定身份映射到本模块单件主键，保留两个标识的不同含义。
     *
     * @param tenantId 经认证且由调用入口确定的租户，不能取自扫描载荷。
     * @param identityIds 待核验的跨模块稳定身份集合。
     * @return 已授权稳定身份对应的模块本地单件主键集合。
     */
    default Map<Long, Long> localSnIds(String tenantId, Set<Long> identityIds) {
        return Map.of();
    }
}

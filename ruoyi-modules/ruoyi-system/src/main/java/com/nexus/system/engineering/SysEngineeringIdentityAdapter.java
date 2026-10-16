package com.nexus.system.engineering;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.nexus.common.ai.engineering.*;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.*;
import com.nexus.system.mapper.*;
import com.nexus.system.service.ISysPermissionService;

import lombok.RequiredArgsConstructor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.util.*;

/** 从 master 读取用户、租户及当前菜单权限，不向工程客户端泄露密码状态。 */
@Component
@RequiredArgsConstructor
@DS("master")
@ConditionalOnProperty(prefix = "nexus.ai.engineering", name = "enabled", havingValue = "true")
public class SysEngineeringIdentityAdapter implements EngineeringIdentityPort {
    private final SysUserMapper users;
    private final SysTenantMapper tenants;
    private final ISysPermissionService permissions;
    private final Clock clock;

    /**
     * 在 master 与精确租户上下文读取当前用户、租户和菜单权限，不使用会话权限快照。
     *
     * @param tenantId 保留前导零的服务端租户标识
     * @param userId 已认证用户编号，不从设备请求正文读取
     * @return 当前启用状态、租户到期状态、内部认证摘要及实时权限；不输出密码散列
     * @throws org.springframework.dao.DataAccessException 身份数据无法可靠读取时中止，不回退旧授权
     */
    @Override
    public EngineeringOwnerStatus resolve(String tenantId, Long userId) {
        return TenantHelper.dynamic(
                tenantId,
                () -> {
                    var user =
                            users.selectOne(
                                    Wrappers.<SysUser>lambdaQuery()
                                            .eq(SysUser::getTenantId, tenantId)
                                            .eq(SysUser::getUserId, userId));
                    var tenant =
                            tenants.selectOne(
                                    Wrappers.<SysTenant>lambdaQuery()
                                            .eq(SysTenant::getTenantId, tenantId));
                    boolean active =
                            user != null
                                    && Objects.equals(user.getTenantId(), tenantId)
                                    && Objects.equals(user.getUserId(), userId)
                                    && "0".equals(user.getStatus())
                                    && "0".equals(user.getDelFlag());
                    boolean tenantActive =
                            tenant != null
                                    && "0".equals(tenant.getStatus())
                                    && "0".equals(tenant.getDelFlag())
                                    && (tenant.getExpireTime() == null
                                            || tenant.getExpireTime()
                                                    .toInstant()
                                                    .isAfter(clock.instant()));
                    return new EngineeringOwnerStatus(
                            active,
                            tenantActive,
                            active ? fingerprint(tenantId, user) : "",
                            active ? permissions.getMenuPermission(userId) : Set.of());
                });
    }

    private String fingerprint(String tenant, SysUser user) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(
                                            (tenant
                                                            + ":"
                                                            + user.getUserId()
                                                            + ":"
                                                            + user.getPassword()
                                                            + ":"
                                                            + user.getStatus()
                                                            + ":"
                                                            + user.getDelFlag())
                                                    .getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行时缺少SHA-256", e);
        }
    }
}

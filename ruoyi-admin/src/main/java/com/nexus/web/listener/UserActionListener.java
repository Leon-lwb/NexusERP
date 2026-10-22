package com.nexus.web.listener;

import cn.dev33.satoken.jwt.exception.SaJwtException;
import cn.dev33.satoken.listener.SaTokenListener;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.convert.Convert;
import cn.hutool.http.useragent.UserAgent;
import cn.hutool.http.useragent.UserAgentUtil;

import com.nexus.common.core.constant.CacheConstants;
import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.domain.dto.UserOnlineDTO;
import com.nexus.common.core.utils.MessageUtils;
import com.nexus.common.core.utils.ServletUtils;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.core.utils.ip.AddressUtils;
import com.nexus.common.log.event.LogininforEvent;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.web.service.SysLoginService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 用户行为 侦听器的实现
 *
 * @author Lion Li
 */
@RequiredArgsConstructor
@Component
@Slf4j
public class UserActionListener implements SaTokenListener {

    private final SysLoginService loginService;

    /**
     * 每次登录时触发
     */
    @Override
    public void doLogin(String loginType, Object loginId, String tokenValue, SaLoginParameter loginParameter) {
        UserAgent userAgent = UserAgentUtil.parse(ServletUtils.getRequest().getHeader("User-Agent"));
        String ip = ServletUtils.getClientIP();
        UserOnlineDTO dto = new UserOnlineDTO();
        dto.setIpaddr(ip);
        dto.setLoginLocation(AddressUtils.getRealAddressByIP(ip));
        dto.setBrowser(userAgent.getBrowser().getName());
        dto.setOs(userAgent.getOs().getName());
        dto.setLoginTime(System.currentTimeMillis());
        dto.setTokenId(tokenValue);
        String username = (String) loginParameter.getExtra(LoginHelper.USER_NAME_KEY);
        String tenantId = (String) loginParameter.getExtra(LoginHelper.TENANT_KEY);
        dto.setUserName(username);
        dto.setClientKey((String) loginParameter.getExtra(LoginHelper.CLIENT_KEY));
        dto.setDeviceType(loginParameter.getDeviceType());
        dto.setDeptName((String) loginParameter.getExtra(LoginHelper.DEPT_NAME_KEY));
        TenantHelper.dynamic(tenantId, () -> {
            if(loginParameter.getTimeout() == -1) {
                RedisUtils.setCacheObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue, dto);
            } else {
                RedisUtils.setCacheObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue, dto, Duration.ofSeconds(loginParameter.getTimeout()));
            }
        });
        // 记录登录日志
        LogininforEvent logininforEvent = new LogininforEvent();
        logininforEvent.setTenantId(tenantId);
        logininforEvent.setUsername(username);
        logininforEvent.setStatus(Constants.LOGIN_SUCCESS);
        logininforEvent.setMessage(MessageUtils.message("user.login.success"));
        logininforEvent.setRequest(ServletUtils.getRequest());
        SpringUtils.context().publishEvent(logininforEvent);
        // 更新登录信息
        loginService.recordLoginInfo((Long) loginParameter.getExtra(LoginHelper.USER_KEY), ip);
        log.info("user doLogin, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 解析 token 中的租户 ID（踢下线/注销场景）。
     * <p>容错：秘钥轮换后旧 JWT 验签必然失败（SaJwtException），此时按 null 租户降级处理
     * ——仅清默认租户的在线缓存，绝不能让登录流程被 500 打断（登录内部 logoutByMaxLoginCount
     * 会走到这里踢旧会话）。</p>
     */
    private String resolveTenantId(String tokenValue) {
        try {
            return Convert.toStr(StpUtil.getExtra(tokenValue, LoginHelper.TENANT_KEY));
        } catch (SaJwtException e) {
            log.warn("解析旧 token 租户信息失败（可能是秘钥轮换前的 token），降级处理: token:***{}",
                StringUtils.right(tokenValue, 8));
            return null;
        }
    }

    /**
     * 每次注销时触发
     */
    @Override
    public void doLogout(String loginType, Object loginId, String tokenValue) {
        String tenantId = resolveTenantId(tokenValue);
        TenantHelper.dynamic(tenantId, () -> {
            RedisUtils.deleteObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue);
        });
        log.info("user doLogout, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 每次被踢下线时触发
     */
    @Override
    public void doKickout(String loginType, Object loginId, String tokenValue) {
        String tenantId = resolveTenantId(tokenValue);
        TenantHelper.dynamic(tenantId, () -> {
            RedisUtils.deleteObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue);
        });
        log.info("user doKickout, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 每次被顶下线时触发
     */
    @Override
    public void doReplaced(String loginType, Object loginId, String tokenValue) {
        String tenantId = resolveTenantId(tokenValue);
        TenantHelper.dynamic(tenantId, () -> {
            RedisUtils.deleteObject(CacheConstants.ONLINE_TOKEN_KEY + tokenValue);
        });
        log.info("user doReplaced, userId:{}, token:***{}", loginId, StringUtils.right(tokenValue, 8));
    }

    /**
     * 每次被封禁时触发
     */
    @Override
    public void doDisable(String loginType, Object loginId, String service, int level, long disableTime) {
    }

    /**
     * 每次被解封时触发
     */
    @Override
    public void doUntieDisable(String loginType, Object loginId, String service) {
    }

    /**
     * 每次打开二级认证时触发
     */
    @Override
    public void doOpenSafe(String loginType, String tokenValue, String service, long safeTime) {
    }

    /**
     * 每次创建Session时触发
     */
    @Override
    public void doCloseSafe(String loginType, String tokenValue, String service) {
    }

    /**
     * 每次创建Session时触发
     */
    @Override
    public void doCreateSession(String id) {
    }

    /**
     * 每次注销Session时触发
     */
    @Override
    public void doLogoutSession(String id) {
    }

    /**
     * 每次Token续期时触发
     */
    @Override
    public void doRenewTimeout(String loginType, Object loginId, String tokenValue, long timeout) {
    }
}

package org.dromara.report.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.jeecg.modules.jmreport.api.JmReportTokenServiceI;
import org.springframework.stereotype.Component;

/**
 * 积木报表 Token 鉴权桥接 — 对接 Sa-Token 权限体系
 *
 * @author nexus-erp
 */
@Slf4j
@Component
public class JimuReportTokenService implements JmReportTokenServiceI {

    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    @Override
    public String getToken(HttpServletRequest request) {
        // JimuReport 内部 AJAX 用 X-Access-Token / token header，前端首次访问用 URL 参数
        String token = request.getHeader("X-Access-Token");
        if (StringUtils.isBlank(token)) {
            token = request.getHeader("token");
        }
        if (StringUtils.isBlank(token)) {
            token = request.getHeader("Authorization");
        }
        if (StringUtils.isBlank(token)) {
            token = request.getParameter("token");
        }
        // 去除 Bearer 前缀
        if (StringUtils.isNotBlank(token) && token.startsWith("Bearer ")) {
            token = token.substring(7);
        }
        if (StringUtils.isNotBlank(token)) {
            CURRENT_TOKEN.set(token);
        }
        return token;
    }

    @Override
    public Boolean verifyToken(String token) {
        if (StringUtils.isBlank(token)) {
            return false;
        }
        try {
            StpUtil.getLoginIdByToken(token);
            CURRENT_TOKEN.set(token);
            return true;
        } catch (Exception e) {
            CURRENT_TOKEN.remove();
            log.warn("积木报表 Token 验证失败: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public String getTenantId() {
        String token = CURRENT_TOKEN.get();
        if (StringUtils.isBlank(token)) {
            return null;
        }
        LoginUser loginUser = LoginHelper.getLoginUser(token);
        if (loginUser != null) {
            return loginUser.getTenantId();
        }
        return null;
    }

    @Override
    public String[] getRoles(String token) {
        LoginUser loginUser = LoginHelper.getLoginUser(token);
        if (loginUser != null && loginUser.getRolePermission() != null) {
            return loginUser.getRolePermission().toArray(new String[0]);
        }
        return new String[0];
    }

    @Override
    public String[] getPermissions(String token) {
        LoginUser loginUser = LoginHelper.getLoginUser(token);
        if (loginUser != null && loginUser.getMenuPermission() != null) {
            return loginUser.getMenuPermission().toArray(new String[0]);
        }
        return new String[0];
    }

    @Override
    public String getUsername(String token) {
        LoginUser loginUser = LoginHelper.getLoginUser(token);
        if (loginUser != null) {
            return loginUser.getUsername();
        }
        return null;
    }
}

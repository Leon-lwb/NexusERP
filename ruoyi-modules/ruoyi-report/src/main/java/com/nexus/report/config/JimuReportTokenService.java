package com.nexus.report.config;

import cn.dev33.satoken.stp.StpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.utils.StringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import org.jeecg.modules.jmreport.api.JmReportTokenServiceI;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    /**
     * 仪表盘（drag 模块）内部权限码
     * <p>
     * JimuReport 的 drag 控制器方法上标注了 @RequiresPermissions，
     * 但这些权限码不在系统菜单表（sys_menu）中维护，故对已认证用户自动注入。
     */
    private static final String[] DRAG_PERMISSIONS = {
        "drag:datasource:testConnection",
        "drag:datasource:saveOrUpate",
        "drag:datasource:delete",
        "drag:analysis:sql",
        "drag:dataset:delete",
        "drag:dataset:save",
        "drag:design:getTotalData",
        "onl:drag:page:delete",
        "onl:drag:clear:recovery",
    };

    @Override
    public String[] getPermissions(String token) {
        LoginUser loginUser = LoginHelper.getLoginUser(token);
        if (loginUser != null && loginUser.getMenuPermission() != null) {
            List<String> permissions = new ArrayList<>(loginUser.getMenuPermission());
            permissions.addAll(Arrays.asList(DRAG_PERMISSIONS));
            return permissions.toArray(new String[0]);
        }
        return DRAG_PERMISSIONS.clone();
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

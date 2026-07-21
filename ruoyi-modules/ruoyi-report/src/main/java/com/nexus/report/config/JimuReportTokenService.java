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
 * <p>属于报表模块（ruoyi-report，基于 JimuReport 积木报表，独立数据源）。实现 JimuReport 的
 * {@code JmReportTokenServiceI} 接口，将积木报表的令牌提取、校验、租户/角色/权限/用户名解析
 * 统一接入 Nexus-ERP 的 Sa-Token 登录体系，使报表设计器与仪表盘沿用系统登录态。</p>
 *
 * @author nexus-erp
 */
@Slf4j
@Component
public class JimuReportTokenService implements JmReportTokenServiceI {

    /** 请求线程内的当前令牌暂存区，用于在 getToken 与租户/角色/权限解析方法间传递令牌。 */
    private static final ThreadLocal<String> CURRENT_TOKEN = new ThreadLocal<>();

    /**
     * 从 HTTP 请求中提取积木报表访问令牌。
     * <p>按 {@code X-Access-Token}、{@code token}、{@code Authorization} 请求头顺序取值，
     * 再回退到 URL 的 {@code token} 参数；自动去除 {@code Bearer } 前缀，并将令牌暂存到线程变量供后续方法使用。</p>
     *
     * @param request 当前 HTTP 请求
     * @return 解析得到的访问令牌，未携带时返回 null
     */
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

    /**
     * 校验令牌有效性。
     * <p>通过 Sa-Token 根据令牌解析登录 ID；成功则暂存令牌并返回 {@code true}，
     * 失败则清理线程变量并记录告警日志。</p>
     *
     * @param token 待校验的访问令牌
     * @return 令牌有效返回 true，无效或为空返回 false
     */
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

    /**
     * 获取当前令牌对应的租户 ID。
     * <p>从线程变量取令牌并解析登录用户，返回其租户 ID；无令牌或用户为空时返回 {@code null}。</p>
     *
     * @return 租户 ID，无有效用户时返回 null
     */
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

    /**
     * 获取令牌对应用户的角色标识数组。
     * <p>解析登录用户的角色权限；用户为空或角色权限为空时返回空数组。</p>
     *
     * @param token 访问令牌
     * @return 角色标识数组，无用户时返回空数组
     */
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

    /**
     * 获取令牌对应用户的权限码数组。
     * <p>在系统菜单权限基础上追加积木报表 drag 模块内置权限码；
     * 无用户时仅返回内置权限码（保证报表设计器可用）。</p>
     *
     * @param token 访问令牌
     * @return 权限码数组
     */
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

    /**
     * 获取令牌对应用户名。
     * <p>解析登录用户并返回其用户名；无用户时返回 {@code null}。</p>
     *
     * @param token 访问令牌
     * @return 用户名，无有效用户时返回 null
     */
    @Override
    public String getUsername(String token) {
        LoginUser loginUser = LoginHelper.getLoginUser(token);
        if (loginUser != null) {
            return loginUser.getUsername();
        }
        return null;
    }
}

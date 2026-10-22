package com.nexus.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;

import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.domain.model.XcxLoginBody;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.StreamUtils;
import com.nexus.common.core.utils.ValidatorUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.bo.SysSocialBo;
import com.nexus.system.domain.bo.SysUserBo;
import com.nexus.system.domain.vo.SysClientVo;
import com.nexus.system.domain.vo.SysSocialVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.mapper.SysUserMapper;
import com.nexus.system.service.ISysSocialService;
import com.nexus.system.service.ISysUserService;
import com.nexus.web.domain.vo.LoginVo;
import com.nexus.web.service.IAuthStrategy;
import com.nexus.web.service.SysLoginService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import me.zhyd.oauth.config.AuthConfig;
import me.zhyd.oauth.model.AuthCallback;
import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthToken;
import me.zhyd.oauth.model.AuthUser;
import me.zhyd.oauth.request.AuthRequest;
import me.zhyd.oauth.request.AuthWechatMiniProgramRequest;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 小程序认证策略
 *
 * @author Michelle.Chung
 */
@Slf4j
@Service("xcx" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class XcxAuthStrategy implements IAuthStrategy {

    private final SysLoginService loginService;
    private final ISysSocialService sysSocialService;
    private final ISysUserService userService;
    private final SysUserMapper userMapper;

    /** 小程序社交登录来源标识 */
    private static final String SOURCE_WECHAT_MINI_PROGRAM = "WECHAT_MINI_PROGRAM";
    /** 微信开放平台社交登录来源标识 */
    private static final String SOURCE_WECHAT_OPEN = "wechat_open";

    @Override
    public LoginVo login(String body, SysClientVo client) {
        XcxLoginBody loginBody = JsonUtils.parseObject(body, XcxLoginBody.class);
        ValidatorUtils.validate(loginBody);
        // xcxCode 为小程序调用 wx.login 授权后获取
        String xcxCode = loginBody.getXcxCode();
        // 优先使用请求中的appid，否则使用客户端配置的appid
        String appid = StrUtil.isNotBlank(loginBody.getAppid()) ? loginBody.getAppid() : client.getAppid();
        String appSecret = client.getAppSecret();

        if (StrUtil.isBlank(appid)) {
            throw new ServiceException("小程序appid未配置，请在客户端管理中配置");
        }
        if (StrUtil.isBlank(appSecret)) {
            throw new ServiceException("小程序appSecret未配置，请在客户端管理中配置");
        }

        // 校验 appid + appSecret + xcxCode 调用登录凭证校验接口 获取 session_key 与 openid
        AuthRequest authRequest = new AuthWechatMiniProgramRequest(AuthConfig.builder()
            .clientId(appid).clientSecret(appSecret)
            .ignoreCheckRedirectUri(true).ignoreCheckState(true).build());
        AuthCallback authCallback = new AuthCallback();
        authCallback.setCode(xcxCode);
        AuthResponse<AuthUser> resp = authRequest.login(authCallback);
        String openid, unionId;
        if (resp.ok()) {
            AuthToken token = resp.getData().getToken();
            openid = token.getOpenId();
            // 微信小程序只有关联到微信开放平台下之后才能获取到 unionId
            unionId = token.getUnionId();
        } else {
            throw new ServiceException("微信小程序登录失败：" + resp.getMsg());
        }

        // 通过 openId 查找已绑定的用户
        SysUserVo user = loadUser(openid, unionId, loginBody.getTenantId(), appid);

        // 构建 LoginUser
        LoginUser loginUser = loginService.buildLoginUser(user);
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());

        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType(client.getDeviceType());
        model.setTimeout(client.getTimeout());
        model.setActiveTimeout(client.getActiveTimeout());
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        loginVo.setOpenid(openid);
        return loginVo;
    }

    /**
     * 加载用户：优先通过unionId跨端匹配，再通过openId查找，未找到则自动注册
     */
    private SysUserVo loadUser(String openId, String unionId, String tenantId, String appid) {
        // 1. 如果有unionId，优先通过unionId跨端匹配（PC微信扫码、App微信、小程序三端互通）
        if (StrUtil.isNotBlank(unionId)) {
            List<SysSocialVo> unionList = sysSocialService.selectByAuthId(SOURCE_WECHAT_OPEN + unionId);
            if (CollUtil.isNotEmpty(unionList)) {
                Optional<SysSocialVo> opt;
                if (TenantHelper.isEnable()) {
                    opt = StreamUtils.findAny(unionList, x -> x.getTenantId().equals(tenantId));
                } else {
                    opt = unionList.stream().findFirst();
                }
                if (opt.isPresent()) {
                    SysSocialVo social = opt.get();
                    SysUserVo user = userMapper.selectVoById(social.getUserId());
                    if (ObjectUtil.isNotNull(user) && !SystemConstants.DISABLE.equals(user.getStatus())) {
                        // 绑定小程序openId到已有社交记录
                        bindMiniProgramSocial(user.getUserId(), openId, unionId, tenantId, appid);
                        return user;
                    }
                }
            }
        }

        // 2. 通过小程序openId查找
        String authId = SOURCE_WECHAT_MINI_PROGRAM + openId;
        List<SysSocialVo> list = sysSocialService.selectByAuthId(authId);
        if (CollUtil.isNotEmpty(list)) {
            Optional<SysSocialVo> opt;
            if (TenantHelper.isEnable()) {
                opt = StreamUtils.findAny(list, x -> x.getTenantId().equals(tenantId));
            } else {
                opt = list.stream().findFirst();
            }
            if (opt.isPresent()) {
                SysSocialVo social = opt.get();
                SysUserVo user = TenantHelper.dynamic(social.getTenantId(), () -> userMapper.selectVoById(social.getUserId()));
                if (ObjectUtil.isNotNull(user) && !SystemConstants.DISABLE.equals(user.getStatus())) {
                    return user;
                }
                // 用户已删除或已停用，清理过期社交记录，继续走自动注册
                log.warn("社交绑定记录对应的用户已不存在或已停用，清理社交记录：authId={}", authId);
                list.forEach(s -> sysSocialService.deleteWithValidById(s.getId()));
            }
        }

        // 3. 未找到绑定用户，自动注册新用户
        return autoRegisterUser(openId, unionId, tenantId, appid);
    }

    /**
     * 自动注册新用户并绑定社交账号
     */
    private SysUserVo autoRegisterUser(String openId, String unionId, String tenantId, String appid) {
        log.info("小程序用户首次登录，自动注册：openId={}", openId);
        SysUserBo userBo = new SysUserBo();
        userBo.setUserName("wx_" + openId.substring(0, Math.min(8, openId.length())));
        userBo.setNickName("微信用户");
        userBo.setPassword(BCrypt.hashpw("Nexus@2026"));
        userBo.setDeptId(2062813928180600833L);

        boolean registered = userService.registerUser(userBo, tenantId);
        if (!registered) {
            throw new ServiceException("微信小程序自动注册用户失败");
        }

        // 查询刚注册的用户
        SysUserVo user = userService.selectUserByUserName(userBo.getUserName());
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException("微信小程序自动注册用户失败，用户查询不到");
        }

        // 绑定社交账号
        bindMiniProgramSocial(user.getUserId(), openId, unionId, tenantId, appid);

        return user;
    }

    /**
     * 绑定小程序社交账号
     */
    private void bindMiniProgramSocial(Long userId, String openId, String unionId, String tenantId, String appid) {
        String authId = SOURCE_WECHAT_MINI_PROGRAM + openId;
        List<SysSocialVo> existList = sysSocialService.selectByAuthId(authId);
        if (CollUtil.isNotEmpty(existList)) {
            // 已存在绑定，更新
            SysSocialBo updateBo = new SysSocialBo();
            updateBo.setId(existList.get(0).getId());
            updateBo.setAuthId(authId);
            updateBo.setSource(SOURCE_WECHAT_MINI_PROGRAM);
            updateBo.setOpenId(openId);
            updateBo.setUnionId(unionId);
            updateBo.setUserId(userId);
            updateBo.setAccessToken("");
            updateBo.setNickName("微信用户");
            updateBo.setUserName("wx_" + openId.substring(0, Math.min(8, openId.length())));
            sysSocialService.updateByBo(updateBo);
        } else {
            // 新增绑定
            SysSocialBo bo = new SysSocialBo();
            bo.setAuthId(authId);
            bo.setSource(SOURCE_WECHAT_MINI_PROGRAM);
            bo.setOpenId(openId);
            bo.setUnionId(unionId);
            bo.setUserId(userId);
            bo.setAccessToken("");
            bo.setNickName("微信用户");
            bo.setUserName("wx_" + openId.substring(0, Math.min(8, openId.length())));
            sysSocialService.insertByBo(bo);
        }

        // 如果有unionId，同时绑定微信开放平台的社交记录（用于跨端匹配）
        if (StrUtil.isNotBlank(unionId)) {
            String openAuthId = SOURCE_WECHAT_OPEN + unionId;
            List<SysSocialVo> openList = sysSocialService.selectByAuthId(openAuthId);
            if (CollUtil.isEmpty(openList)) {
                SysSocialBo openBo = new SysSocialBo();
                openBo.setAuthId(openAuthId);
                openBo.setSource(SOURCE_WECHAT_OPEN);
                openBo.setOpenId(openId);
                openBo.setUnionId(unionId);
                openBo.setUserId(userId);
                openBo.setAccessToken("");
                openBo.setNickName("微信用户");
                openBo.setUserName("wx_" + openId.substring(0, Math.min(8, openId.length())));
                sysSocialService.insertByBo(openBo);
            }
        }
    }

}

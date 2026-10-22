package com.nexus.web.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;

import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.domain.model.SocialLoginBody;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.exception.user.UserException;
import com.nexus.common.core.utils.StreamUtils;
import com.nexus.common.core.utils.ValidatorUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.social.config.properties.SocialProperties;
import com.nexus.common.social.utils.SocialUtils;
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

import me.zhyd.oauth.model.AuthResponse;
import me.zhyd.oauth.model.AuthUser;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 第三方授权策略
 *
 * @author thiszhc is 三三
 */
@Slf4j
@Service("social" + IAuthStrategy.BASE_NAME)
@RequiredArgsConstructor
public class SocialAuthStrategy implements IAuthStrategy {

    private final SocialProperties socialProperties;
    private final ISysSocialService sysSocialService;
    private final ISysUserService userService;
    private final SysUserMapper userMapper;
    private final SysLoginService loginService;

    /** 微信开放平台社交登录来源标识 */
    private static final String SOURCE_WECHAT_OPEN = "wechat_open";

    /**
     * 登录-第三方授权登录
     *
     * @param body     登录信息
     * @param client   客户端信息
     */
    @Override
    public LoginVo login(String body, SysClientVo client) {
        SocialLoginBody loginBody = JsonUtils.parseObject(body, SocialLoginBody.class);
        ValidatorUtils.validate(loginBody);
        AuthResponse<AuthUser> response = SocialUtils.loginAuth(
                loginBody.getSource(), loginBody.getSocialCode(),
                loginBody.getSocialState(), socialProperties);
        if (!response.ok()) {
            throw new ServiceException(response.getMsg());
        }
        AuthUser authUserData = response.getData();

        List<SysSocialVo> list = sysSocialService.selectByAuthId(authUserData.getSource() + authUserData.getUuid());
        SysSocialVo social;
        if (CollUtil.isEmpty(list)) {
            // 未绑定社交账号，自动注册新用户并绑定
            SysUserVo newUser = autoRegisterAndBind(authUserData, loginBody.getTenantId());
            social = new SysSocialVo();
            social.setUserId(newUser.getUserId());
            social.setTenantId(loginBody.getTenantId());
        } else {
            if (TenantHelper.isEnable()) {
                Optional<SysSocialVo> opt = StreamUtils.findAny(list, x -> x.getTenantId().equals(loginBody.getTenantId()));
                if (opt.isEmpty()) {
                    throw new ServiceException("对不起，你没有权限登录当前租户！");
                }
                social = opt.get();
            } else {
                social = list.get(0);
            }
            // 检查用户是否存在，如果已删除则清理社交记录并自动注册
            SysUserVo existUser = userMapper.selectVoById(social.getUserId());
            if (ObjectUtil.isNull(existUser) || SystemConstants.DISABLE.equals(existUser.getStatus())) {
                log.warn("社交绑定记录对应的用户已不存在或已停用，清理社交记录并重新注册：authId={}", social.getAuthId());
                list.forEach(s -> sysSocialService.deleteWithValidById(s.getId()));
                SysUserVo newUser = autoRegisterAndBind(authUserData, loginBody.getTenantId());
                social = new SysSocialVo();
                social.setUserId(newUser.getUserId());
                social.setTenantId(loginBody.getTenantId());
            }
        }
        SysSocialVo finalSocial = social;
        LoginUser loginUser = TenantHelper.dynamic(social.getTenantId(), () -> {
            SysUserVo user = loadUser(finalSocial.getUserId());
            // 此处可根据登录用户的数据不同 自行创建 loginUser 属性不够用继承扩展就行了
            return loginService.buildLoginUser(user);
        });
        loginUser.setClientKey(client.getClientKey());
        loginUser.setDeviceType(client.getDeviceType());
        SaLoginParameter model = new SaLoginParameter();
        model.setDeviceType(client.getDeviceType());
        // 自定义分配 不同用户体系 不同 token 授权时间 不设置默认走全局 yml 配置
        // 例如: 后台用户30分钟过期 app用户1天过期
        model.setTimeout(client.getTimeout());
        model.setActiveTimeout(client.getActiveTimeout());
        model.setExtra(LoginHelper.CLIENT_KEY, client.getClientId());
        // 生成token
        LoginHelper.login(loginUser, model);

        LoginVo loginVo = new LoginVo();
        loginVo.setAccessToken(StpUtil.getTokenValue());
        loginVo.setExpireIn(StpUtil.getTokenTimeout());
        loginVo.setClientId(client.getClientId());
        return loginVo;
    }

    private SysUserVo loadUser(Long userId) {
        SysUserVo user = userMapper.selectVoById(userId);
        if (ObjectUtil.isNull(user)) {
            log.info("登录用户：{} 不存在.", "");
            throw new UserException("user.not.exists", "");
        } else if (SystemConstants.DISABLE.equals(user.getStatus())) {
            log.info("登录用户：{} 已被停用.", "");
            throw new UserException("user.blocked", "");
        }
        return user;
    }

    /**
     * 自动注册新用户并绑定社交账号
     */
    private SysUserVo autoRegisterAndBind(AuthUser authUserData, String tenantId) {
        String uuid = authUserData.getUuid();
        log.info("第三方用户首次登录，自动注册：source={}, uuid={}", authUserData.getSource(), uuid);

        SysUserBo userBo = new SysUserBo();
        userBo.setUserName("wx_" + uuid.substring(0, Math.min(8, uuid.length())));
        userBo.setNickName(StrUtil.isNotBlank(authUserData.getNickname()) ? authUserData.getNickname() : "微信用户");
        userBo.setPassword(BCrypt.hashpw("Nexus@2026"));
        userBo.setDeptId(2062813928180600833L);

        boolean registered = userService.registerUser(userBo, tenantId);
        if (!registered) {
            throw new ServiceException("微信自动注册用户失败");
        }

        SysUserVo user = userService.selectUserByUserName(userBo.getUserName());
        if (ObjectUtil.isNull(user)) {
            throw new ServiceException("微信自动注册用户失败，用户查询不到");
        }

        // 绑定社交账号
        SysSocialBo bo = new SysSocialBo();
        bo.setAuthId(authUserData.getSource() + uuid);
        bo.setSource(authUserData.getSource());
        bo.setOpenId(authUserData.getToken() != null ? authUserData.getToken().getOpenId() : "");
        bo.setUnionId(authUserData.getToken() != null ? authUserData.getToken().getUnionId() : "");
        bo.setUserId(user.getUserId());
        bo.setAccessToken(authUserData.getToken() != null ? authUserData.getToken().getAccessToken() : "");
        bo.setNickName(userBo.getNickName());
        bo.setUserName(userBo.getUserName());
        bo.setTenantId(tenantId);
        sysSocialService.insertByBo(bo);

        return user;
    }

}

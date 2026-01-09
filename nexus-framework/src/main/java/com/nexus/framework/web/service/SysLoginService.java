package com.nexus.framework.web.service;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import com.nexus.common.constant.CacheConstants;
import com.nexus.common.constant.Constants;
import com.nexus.common.constant.UserConstants;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.redis.RedisCache;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.exception.user.BlackListException;
import com.nexus.common.exception.user.CaptchaException;
import com.nexus.common.exception.user.CaptchaExpireException;
import com.nexus.common.exception.user.UserNotExistsException;
import com.nexus.common.exception.user.UserPasswordNotMatchException;
import com.nexus.common.utils.DateUtils;
import com.nexus.common.utils.MessageUtils;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.utils.SecurityUtils;
import com.nexus.common.utils.ip.IpUtils;
import com.nexus.framework.manager.AsyncManager;
import com.nexus.framework.manager.factory.AsyncFactory;
import com.nexus.framework.security.context.AuthenticationContextHolder;
import com.nexus.framework.web.service.UserDetailsServiceImpl;
import com.nexus.system.service.ISysConfigService;
import com.nexus.system.service.ISysUserService;

/**
 * 登录校验方法
 * 
 * @author nexus
 */
@Component
public class SysLoginService
{
    @Autowired
    private TokenService tokenService;

    @Resource
    private AuthenticationManager authenticationManager;

    @Autowired
    private RedisCache redisCache;
    
    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysConfigService configService;
    
    @Autowired
    private UserDetailsServiceImpl userDetailsService;

    /**
     * 登录验证
     * 
     * @param username 用户名或手机号
     * @param password 密码
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public String login(String username, String password, String code, String uuid)
    {
        // 验证码校验
        validateCaptcha(username, code, uuid);
        // 登录前置校验
        loginPreCheck(username, password);
        
        // 首先尝试通过用户名查找用户
        SysUser user = userService.selectUserByUserName(username);
        
        // 如果用户名未找到且输入的是手机号格式，则尝试通过手机号查找用户
        if (user == null && isValidMobile(username))
        {
            user = userService.selectUserByPhonenumber(username);
        }
        
        if (user == null)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.not.exists")));
            throw new UserNotExistsException();
        }
        
        // 用户验证
        Authentication authentication = null;
        try
        {
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(user.getUserName(), password);
            AuthenticationContextHolder.setContext(authenticationToken);
            // 该方法会去调用UserDetailsServiceImpl.loadUserByUsername
            authentication = authenticationManager.authenticate(authenticationToken);
        }
        catch (Exception e)
        {
            if (e instanceof BadCredentialsException)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
                throw new UserPasswordNotMatchException();
            }
            else
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, e.getMessage()));
                throw new ServiceException(e.getMessage());
            }
        }
        finally
        {
            AuthenticationContextHolder.clearContext();
        }
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        LoginUser loginUser = (LoginUser) authentication.getPrincipal();
        recordLoginInfo(loginUser.getUserId());
        // 生成token
        return tokenService.createToken(loginUser);
    }

    /**
     * 通过短信登录
     * @param username 用户名
     * @return 结果
     */
    public String loginBySms(String username)
    {
        // 获取用户
        SysUser user = userService.selectUserByUserName(username);
        if (user == null)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.not.exists")));
            throw new UserNotExistsException();
        }
        
        // 直接加载用户信息，跳过密码验证
        LoginUser loginUser = loadUserForSmsLogin(user);
        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_SUCCESS, MessageUtils.message("user.login.success")));
        recordLoginInfo(loginUser.getUserId());
        // 生成token
        return tokenService.createToken(loginUser);
    }
    
    /**
     * 为短信登录加载用户信息，不进行密码验证
     * @param user 用户对象
     * @return LoginUser
     */
    private LoginUser loadUserForSmsLogin(SysUser user)
    {
        // 设置认证上下文为null以避免密码验证
        AuthenticationContextHolder.clearContext();
        return (LoginUser) userDetailsService.createLoginUser(user);
    }

    /**
     * 通过手机号创建或获取用户
     * @param mobile 手机号
     * @return 用户对象
     */
    public SysUser createOrGetUserByMobile(String mobile)
    {
        // 先尝试通过手机号查找用户
        SysUser user = userService.selectUserByPhonenumber(mobile);
        if (user != null)
        {
            return user;
        }

        // 如果用户不存在，创建新用户
        user = new SysUser();
        String username = generateUsernameByMobile(mobile);
        user.setUserName(username);
        user.setNickName("手机号用户_" + mobile);
        user.setPhonenumber(mobile);
        // 设置默认密码并加密
        String defaultPassword = "123456"; // 默认密码，首次登录后应要求修改
        user.setPassword(SecurityUtils.encryptPassword(defaultPassword));
        user.setStatus("0"); // 启用状态

        // 创建用户
        userService.insertUser(user);
        return user;
    }

    /**
     * 根据手机号生成用户名
     * @param mobile 手机号
     * @return 用户名
     */
    private String generateUsernameByMobile(String mobile)
    {
        String baseUsername = "mobile_" + mobile;
        SysUser user = userService.selectUserByUserName(baseUsername);
        if (user == null)
        {
            return baseUsername;
        }

        // 如果用户名已存在，添加后缀
        int suffix = 1;
        String newUsername;
        do
        {
            newUsername = baseUsername + "_" + suffix;
            user = userService.selectUserByUserName(newUsername);
            suffix++;
        } while (user != null && suffix < 100); // 防止无限循环

        return newUsername;
    }

    /**
     * 校验验证码
     * 
     * @param username 用户名
     * @param code 验证码
     * @param uuid 唯一标识
     * @return 结果
     */
    public void validateCaptcha(String username, String code, String uuid)
    {
        boolean captchaEnabled = configService.selectCaptchaEnabled();
        if (captchaEnabled)
        {
            String verifyKey = CacheConstants.CAPTCHA_CODE_KEY + StringUtils.nvl(uuid, "");
            String captcha = redisCache.getCacheObject(verifyKey);
            if (captcha == null)
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.expire")));
                throw new CaptchaExpireException();
            }
            redisCache.deleteObject(verifyKey);
            if (!code.equalsIgnoreCase(captcha))
            {
                AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.jcaptcha.error")));
                throw new CaptchaException();
            }
        }
    }

    /**
     * 登录前置校验
     * @param username 用户名
     * @param password 用户密码
     */
    public void loginPreCheck(String username, String password)
    {
        // 用户名或密码为空 错误
        if (StringUtils.isEmpty(username))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("not.null")));
            throw new UserNotExistsException();
        }
        // 密码如果不在指定范围内 错误（对于短信登录，密码可能是临时的，所以只在非临时密码时检查）
        if (!"temp_password_for_sms_login".equals(password) &&
            password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // 用户名不在指定范围内 错误
        if (username.length() < UserConstants.USERNAME_MIN_LENGTH
                || username.length() > UserConstants.USERNAME_MAX_LENGTH)
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("user.password.not.match")));
            throw new UserPasswordNotMatchException();
        }
        // IP黑名单校验
        String blackStr = configService.selectConfigByKey("sys.login.blackIPList");
        if (IpUtils.isMatchedIp(blackStr, IpUtils.getIpAddr()))
        {
            AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.LOGIN_FAIL, MessageUtils.message("login.blocked")));
            throw new BlackListException();
        }
    }

    /**
     * 记录登录信息
     *
     * @param userId 用户ID
     */
    public void recordLoginInfo(Long userId)
    {
        SysUser sysUser = new SysUser();
        sysUser.setUserId(userId);
        sysUser.setLoginIp(IpUtils.getIpAddr());
        sysUser.setLoginDate(DateUtils.getNowDate());
        userService.updateUserProfile(sysUser);
    }
    
    /**
     * 判断是否为有效的手机号格式
     * 
     * @param mobile 手机号
     * @return 结果
     */
    private boolean isValidMobile(String mobile)
    {
        if (StringUtils.isEmpty(mobile))
        {
            return false;
        }
        // 手机号正则表达式匹配
        return mobile.matches("^1[3-9]\\d{9}$");
    }
}
package com.nexus.web.controller.system;

import java.util.Date;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.nexus.common.constant.Constants;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysMenu;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.core.domain.model.LoginBody;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.domain.model.SmsCodeBody;
import com.nexus.common.core.domain.model.SmsLoginBody;
import com.nexus.common.core.text.Convert;
import com.nexus.common.utils.DateUtils;
import com.nexus.common.utils.SecurityUtils;
import com.nexus.common.utils.StringUtils;
import com.nexus.framework.web.service.SysLoginService;
import com.nexus.framework.web.service.SysPermissionService;
import com.nexus.framework.web.service.TokenService;
import com.nexus.system.service.ISysConfigService;
import com.nexus.system.service.ISysMenuService;

/**
 * 登录验证
 * 
 * @author nexus
 */
@RestController
public class SysLoginController
{
    @Autowired
    private SysLoginService loginService;

    @Autowired
    private ISysMenuService menuService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ISysConfigService configService;

    @Autowired
    private com.nexus.framework.web.service.SmsCodeService smsCodeService;

    /**
     * 登录方法 支持用户名或手机号登录
     * 
     * @param loginBody 登录信息
     * @return 结果
     */
    @PostMapping("/login")
    public AjaxResult login(@RequestBody LoginBody loginBody)
    {
        AjaxResult ajax = AjaxResult.success();
        // 生成令牌
        String token = loginService.login(loginBody.getUsername(), loginBody.getPassword(), loginBody.getCode(),
                loginBody.getUuid());
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    /**
     * 短信验证码登录
     * 
     * @param smsLoginBody 短信登录信息
     * @return 结果
     */
    @PostMapping("/auth/smsLogin")
    public AjaxResult smsLogin(@RequestBody SmsLoginBody smsLoginBody)
    {
        String mobile = smsLoginBody.getMobile();
        String code = smsLoginBody.getCode();

        // 验证短信验证码
        if (!smsCodeService.validateSmsCode(mobile, code))
        {
            return AjaxResult.error(5005, "验证码错误");
        }

        // 通过手机号获取用户，如果用户不存在则创建新用户
        SysUser user = loginService.createOrGetUserByMobile(mobile);

        // 生成令牌
        String token = loginService.loginBySms(user.getUserName());
        
        AjaxResult ajax = AjaxResult.success();
        ajax.put(Constants.TOKEN, token);
        return ajax;
    }

    /**
     * 发送短信验证码
     * 
     * @param smsCodeBody 短信验证码请求信息
     * @return 结果
     */
    @PostMapping("/auth/sms")
    public AjaxResult sendSmsCode(@RequestBody SmsCodeBody smsCodeBody)
    {
        return smsCodeService.sendSmsCode(smsCodeBody);
    }

    /**
     * 获取用户信息
     * 
     * @return 用户信息
     */
    @GetMapping("getInfo")
    public AjaxResult getInfo()
    {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        SysUser user = loginUser.getUser();
        // 角色集合
        Set<String> roles = permissionService.getRolePermission(user);
        // 权限集合
        Set<String> permissions = permissionService.getMenuPermission(user);
        if (!loginUser.getPermissions().equals(permissions))
        {
            loginUser.setPermissions(permissions);
            tokenService.refreshToken(loginUser);
        }
        AjaxResult ajax = AjaxResult.success();
        ajax.put("user", user);
        ajax.put("roles", roles);
        ajax.put("permissions", permissions);
        ajax.put("isDefaultModifyPwd", initPasswordIsModify(user.getPwdUpdateDate()));
        ajax.put("isPasswordExpired", passwordIsExpiration(user.getPwdUpdateDate()));
        return ajax;
    }

    /**
     * 获取路由信息
     * 
     * @return 路由信息
     */
    @GetMapping("getRouters")
    public AjaxResult getRouters()
    {
        Long userId = SecurityUtils.getUserId();
        List<SysMenu> menus = menuService.selectMenuTreeByUserId(userId);
        return AjaxResult.success(menuService.buildMenus(menus));
    }
    
    // 检查初始密码是否提醒修改
    public boolean initPasswordIsModify(Date pwdUpdateDate)
    {
        Integer initPasswordModify = Convert.toInt(configService.selectConfigByKey("sys.account.initPasswordModify"));
        return initPasswordModify != null && initPasswordModify == 1 && pwdUpdateDate == null;
    }

    // 检查密码是否过期
    public boolean passwordIsExpiration(Date pwdUpdateDate)
    {
        Integer passwordValidateDays = Convert.toInt(configService.selectConfigByKey("sys.account.passwordValidateDays"));
        if (passwordValidateDays != null && passwordValidateDays > 0)
        {
            if (StringUtils.isNull(pwdUpdateDate))
            {
                // 如果从未修改过初始密码，直接提醒过期
                return true;
            }
            Date nowDate = DateUtils.getNowDate();
            return DateUtils.differentDaysByMillisecond(nowDate, pwdUpdateDate) > passwordValidateDays;
        }
        return false;
    }
}
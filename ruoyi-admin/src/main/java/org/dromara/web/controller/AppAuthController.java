package org.dromara.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.dromara.common.core.constant.Constants;
import org.dromara.common.core.constant.GlobalConstants;
import org.dromara.common.core.constant.SystemConstants;
import org.dromara.common.core.domain.R;
import org.dromara.common.core.domain.model.LoginUser;
import org.dromara.common.core.domain.model.PasswordLoginBody;
import org.dromara.common.core.domain.model.SmsLoginBody;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.json.utils.JsonUtils;
import org.dromara.common.redis.utils.RedisUtils;
import org.dromara.common.satoken.utils.LoginHelper;
import org.dromara.common.tenant.helper.TenantHelper;
import org.dromara.system.domain.bo.SysTenantBo;
import org.dromara.system.domain.vo.SysClientVo;
import org.dromara.system.domain.vo.SysTenantVo;
import org.dromara.system.domain.vo.SysUserVo;
import org.dromara.system.service.ISysClientService;
import org.dromara.system.service.ISysTenantService;
import org.dromara.system.service.ISysUserService;
import org.dromara.web.domain.vo.LoginVo;
import org.dromara.web.domain.vo.TenantListVo;
import org.dromara.web.service.IAuthStrategy;
import org.dromara.web.service.SysLoginService;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * App移动端认证控制器
 * <p>
 * 为移动端提供简化的认证接口，内部封装 clientId/grantType 等 OAuth2 参数。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@RestController
@RequestMapping("/app/auth")
public class AppAuthController {

    /**
     * App 客户端ID (sys_client 表中预置)
     */
    private static final String APP_CLIENT_ID = "428a8310cd442757ae699df5d894f051";
    private static final String APP_GRANT_TYPE_PASSWORD = "password";
    private static final String APP_GRANT_TYPE_SMS = "sms";

    private final SysLoginService loginService;
    private final ISysClientService clientService;
    private final ISysUserService userService;
    private final ISysTenantService tenantService;

    /**
     * 获取短信验证码（开发环境）
     * <p>
     * 不发送真实短信，验证码直接返回给前端展示。生产环境请配置真实短信服务商后使用 /resource/sms/code。
     */
    @SaIgnore
    @GetMapping("/smsCode")
    public R<Map<String, Object>> smsCode(@RequestParam String phonenumber) {
        String key = GlobalConstants.CAPTCHA_CODE_KEY + phonenumber;
        String code = RandomUtil.randomNumbers(6);
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        log.info("App开发模式短信验证码: phone={}, code={}", phonenumber, code);

        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("phonenumber", phonenumber);
        result.put("message", "短信服务商未配置，开发环境验证码：" + code);
        return R.ok(result);
    }

    /**
     * App密码登录
     */
    @SaIgnore
    @PostMapping("/login")
    public R<Map<String, Object>> login(@RequestBody Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        String tenantId = body.getOrDefault("tenantId", "");

        PasswordLoginBody loginBody = new PasswordLoginBody();
        loginBody.setClientId(APP_CLIENT_ID);
        loginBody.setGrantType(APP_GRANT_TYPE_PASSWORD);
        loginBody.setTenantId(tenantId);
        loginBody.setUsername(username);
        loginBody.setPassword(password);
        // 验证码字段（当后端开启验证码时必传）
        loginBody.setCode(body.get("code"));
        loginBody.setUuid(body.get("uuid"));

        SysClientVo client = clientService.queryByClientId(APP_CLIENT_ID);
        if (ObjectUtil.isNull(client) || !SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail("App客户端已被停用");
        }

        loginService.checkTenant(tenantId);

        LoginVo loginVo = IAuthStrategy.login(
            JsonUtils.toJsonString(loginBody),
            client,
            APP_GRANT_TYPE_PASSWORD
        );

        Map<String, Object> result = new HashMap<>();
        result.put("token", loginVo.getAccessToken());
        result.put("expireIn", loginVo.getExpireIn());
        return R.ok(result);
    }

    /**
     * App短信验证码登录
     */
    @SaIgnore
    @PostMapping("/smsLogin")
    public R<Map<String, Object>> smsLogin(@RequestBody Map<String, String> body) {
        String phonenumber = body.get("phonenumber");
        String smsCode = body.get("smsCode");
        String tenantId = body.getOrDefault("tenantId", "");

        SmsLoginBody loginBody = new SmsLoginBody();
        loginBody.setClientId(APP_CLIENT_ID);
        loginBody.setGrantType(APP_GRANT_TYPE_SMS);
        loginBody.setTenantId(tenantId);
        loginBody.setPhonenumber(phonenumber);
        loginBody.setSmsCode(smsCode);

        SysClientVo client = clientService.queryByClientId(APP_CLIENT_ID);
        if (ObjectUtil.isNull(client) || !SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail("App客户端已被停用");
        }

        loginService.checkTenant(tenantId);

        LoginVo loginVo = IAuthStrategy.login(
            JsonUtils.toJsonString(loginBody),
            client,
            APP_GRANT_TYPE_SMS
        );

        Map<String, Object> result = new HashMap<>();
        result.put("token", loginVo.getAccessToken());
        result.put("expireIn", loginVo.getExpireIn());
        return R.ok(result);
    }

    /**
     * 获取当前用户信息
     */
    @GetMapping("/getInfo")
    public R<Map<String, Object>> getInfo() {
        Long userId = LoginHelper.getUserId();
        SysUserVo user = userService.selectUserById(userId);
        LoginUser loginUser = LoginHelper.getLoginUser();

        Map<String, Object> result = new HashMap<>();
        result.put("user", user);
        result.put("roles", loginUser.getRolePermission());
        result.put("permissions", loginUser.getMenuPermission());
        return R.ok(result);
    }

    /**
     * 退出登录
     */
    @PostMapping("/logout")
    public R<Void> logout() {
        loginService.logout();
        return R.ok("退出成功");
    }

    /**
     * 获取租户列表
     */
    @SaIgnore
    @GetMapping("/tenant/list")
    public R<Map<String, Object>> tenantList() {
        boolean enable = TenantHelper.isEnable();
        Map<String, Object> result = new HashMap<>();
        result.put("tenantEnabled", enable);
        if (enable) {
            List<SysTenantVo> tenantList = tenantService.queryList(new SysTenantBo());
            List<TenantListVo> voList = MapstructUtils.convert(tenantList, TenantListVo.class);
            result.put("voList", voList);
        } else {
            result.put("voList", List.of());
        }
        return R.ok(result);
    }
}

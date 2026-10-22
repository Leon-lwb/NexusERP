package com.nexus.web.controller;

import cn.dev33.satoken.annotation.SaIgnore;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;

import com.nexus.common.core.constant.Constants;
import com.nexus.common.core.constant.GlobalConstants;
import com.nexus.common.core.constant.SystemConstants;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.domain.model.LoginUser;
import com.nexus.common.core.domain.model.PasswordLoginBody;
import com.nexus.common.core.domain.model.SmsLoginBody;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.ratelimiter.annotation.RateLimiter;
import com.nexus.common.redis.utils.RedisUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.system.domain.bo.SysTenantBo;
import com.nexus.system.domain.bo.SysUserBo;
import com.nexus.system.domain.vo.SysClientVo;
import com.nexus.system.domain.vo.SysTenantVo;
import com.nexus.system.domain.vo.SysUserVo;
import com.nexus.system.service.ISysClientService;
import com.nexus.system.service.ISysTenantService;
import com.nexus.system.service.ISysUserService;
import com.nexus.web.domain.vo.LoginVo;
import com.nexus.web.domain.vo.TenantListVo;
import com.nexus.web.service.IAuthStrategy;
import com.nexus.web.service.SysLoginService;

import jakarta.validation.constraints.Pattern;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import me.zhyd.oauth.cache.AuthStateCache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * App移动端认证控制器
 * <p>
 * 为移动端提供简化的认证接口，内部封装 clientId/grantType 等 OAuth2 参数。
 *
 * @author nexus
 */
@Slf4j
@RequiredArgsConstructor
@Validated
@RestController
@RequestMapping("/app/auth")
public class AppAuthController {

    /**
     * App 客户端ID (sys_client 表中预置)
     */
    private static final String APP_CLIENT_ID = "428a8310cd442757ae699df5d894f051";

    /**
     * 临时验证码回显开关 (默认关闭; 对公网开放时知道手机号即可登录该账号, 接入短信后必须关闭)
     */
    @Value("${app.auth.sms-code-echo:false}")
    private boolean smsCodeEcho;
    private static final String APP_GRANT_TYPE_PASSWORD = "password";
    private static final String APP_GRANT_TYPE_SMS = "sms";

    private final SysLoginService loginService;
    private final ISysClientService clientService;
    private final ISysUserService userService;
    private final ISysTenantService tenantService;
    private final AuthStateCache authRedisStateCache;

    /**
     * 获取临时回显验证码
     * <p>
     * 暂未接入短信服务时生成真实登录验证码并回显给请求者。接入短信后关闭此开关并使用 /resource/sms/code。
     */
    @SaIgnore
    @RateLimiter(key = "#phonenumber", time = 60, count = 1)
    @GetMapping("/smsCode")
    public R<Map<String, Object>> smsCode(@RequestParam @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式错误") String phonenumber) {
        // 回显仅为未接入短信服务时的临时措施，任何请求者均可拿到该手机号的登录凭据
        if (!smsCodeEcho) {
            return R.warn("短信登录暂不可用，请联系管理员");
        }
        String key = GlobalConstants.CAPTCHA_CODE_KEY + phonenumber;
        String code = generateSmsCode();
        RedisUtils.setCacheObject(key, code, Duration.ofMinutes(Constants.CAPTCHA_EXPIRATION));
        log.info("App临时验证码已生成: phone={}", phonenumber);

        Map<String, Object> result = new HashMap<>();
        result.put("code", code);
        result.put("phonenumber", phonenumber);
        result.put("message", "短信服务商未配置，当前验证码已回显");
        return R.ok(result);
    }

    static String generateSmsCode() {
        return RandomUtil.randomNumbers(4);
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
            return R.fail(10020, "App客户端已被停用");
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
            return R.fail(10020, "App客户端已被停用");
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
     * App微信社交登录（App端拉起微信授权）
     * <p>
     * App端通过 uni.login({ provider: 'weixin' }) 获取 code 后调用此接口
     */
    @SaIgnore
    @PostMapping("/socialLogin")
    public R<Map<String, Object>> socialLogin(@RequestBody Map<String, String> body) {
        String source = body.get("source");
        String socialCode = body.get("socialCode");
        String socialState = body.get("socialState");
        String tenantId = body.getOrDefault("tenantId", "");

        if (StrUtil.isBlank(source) || StrUtil.isBlank(socialCode)) {
            return R.warn("社交登录参数不完整");
        }

        // 构造社交登录请求体
        Map<String, String> loginBodyMap = new HashMap<>();
        loginBodyMap.put("clientId", APP_CLIENT_ID);
        loginBodyMap.put("grantType", "social");
        loginBodyMap.put("tenantId", tenantId);
        loginBodyMap.put("source", source);
        loginBodyMap.put("socialCode", socialCode);
        loginBodyMap.put(
                "socialState",
                StrUtil.isNotBlank(socialState) ? socialState : UUID.randomUUID().toString());

        SysClientVo client = clientService.queryByClientId(APP_CLIENT_ID);
        if (ObjectUtil.isNull(client) || !SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail(10020, "App客户端已被停用");
        }

        loginService.checkTenant(tenantId);

        // 预存储state到缓存，JustAuth的login()可能会校验state
        String finalState = loginBodyMap.get("socialState");
        authRedisStateCache.cache(finalState, finalState);

        LoginVo loginVo = IAuthStrategy.login(
            JsonUtils.toJsonString(loginBodyMap),
            client,
            "social"
        );

        Map<String, Object> result = new HashMap<>();
        result.put("token", loginVo.getAccessToken());
        result.put("expireIn", loginVo.getExpireIn());
        result.put("client_id", client.getClientId());
        return R.ok(result);
    }

    /**
     * 微信小程序登录
     * <p>
     * 小程序端通过 wx.login() 获取 code 后调用此接口
     */
    @SaIgnore
    @PostMapping("/xcxLogin")
    public R<Map<String, Object>> xcxLogin(@RequestBody Map<String, String> body) {
        String xcxCode = body.get("xcxCode");
        String appid = body.get("appid");
        String tenantId = body.getOrDefault("tenantId", "");

        if (StrUtil.isBlank(xcxCode)) {
            return R.warn("微信授权信息不完整，请重新授权");
        }

        // 小程序专用客户端ID
        String xcxClientId = "a3b4c5d6e7f8g9h0i1j2k3l4m5n6o7p8";

        Map<String, String> loginBodyMap = new HashMap<>();
        loginBodyMap.put("clientId", xcxClientId);
        loginBodyMap.put("grantType", "xcx");
        loginBodyMap.put("tenantId", tenantId);
        loginBodyMap.put("xcxCode", xcxCode);
        if (StrUtil.isNotBlank(appid)) {
            loginBodyMap.put("appid", appid);
        }

        SysClientVo client = clientService.queryByClientId(xcxClientId);
        if (ObjectUtil.isNull(client) || !SystemConstants.NORMAL.equals(client.getStatus())) {
            return R.fail(10020, "小程序客户端已被停用");
        }

        loginService.checkTenant(tenantId);

        LoginVo loginVo = IAuthStrategy.login(
            JsonUtils.toJsonString(loginBodyMap),
            client,
            "xcx"
        );

        Map<String, Object> result = new HashMap<>();
        result.put("token", loginVo.getAccessToken());
        result.put("expireIn", loginVo.getExpireIn());
        result.put("openid", loginVo.getOpenid());
        result.put("client_id", client.getClientId());
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
     * 微信小程序获取手机号
     * <p>
     * 小程序端通过 open-type="getPhoneNumber" 获取 code 后调用此接口
     */
    @PostMapping("/bindPhone")
    public R<Void> bindPhone(@RequestBody Map<String, String> body) {
        String phoneCode = body.get("phoneCode");
        if (StrUtil.isBlank(phoneCode)) {
            return R.fail("手机号授权code不能为空");
        }

        Long userId = LoginHelper.getUserId();
        if (ObjectUtil.isNull(userId)) {
            return R.fail("用户未登录");
        }

        // 获取小程序客户端配置
        String xcxClientId = "a3b4c5d6e7f8g9h0i1j2k3l4m5n6o7p8";
        SysClientVo client = clientService.queryByClientId(xcxClientId);
        if (ObjectUtil.isNull(client) || StrUtil.isBlank(client.getAppid()) || StrUtil.isBlank(client.getAppSecret())) {
            return R.fail("小程序客户端配置不完整");
        }

        try {
            // 1. 获取 access_token
            String tokenUrl = String.format(
                "https://api.weixin.qq.com/cgi-bin/token?grant_type=client_credential&appid=%s&secret=%s",
                client.getAppid(), client.getAppSecret());
            String tokenResp = HttpUtil.get(tokenUrl);
            Map<String, Object> tokenMap = JsonUtils.parseMap(tokenResp);
            String accessToken = (String) tokenMap.get("access_token");
            if (StrUtil.isBlank(accessToken)) {
                log.error("获取微信access_token失败：{}", tokenResp);
                return R.fail("获取微信access_token失败");
            }

            // 2. 获取手机号
            String phoneUrl = "https://api.weixin.qq.com/wxa/business/getuserphonenumber?access_token=" + accessToken;
            Map<String, String> phoneBody = new HashMap<>();
            phoneBody.put("code", phoneCode);
            String phoneResp = HttpUtil.post(phoneUrl, JsonUtils.toJsonString(phoneBody), 5000);
            Map<String, Object> phoneMap = JsonUtils.parseMap(phoneResp);
            Object phoneInfo = phoneMap.get("phone_info");
            if (ObjectUtil.isNull(phoneInfo)) {
                log.error("获取手机号失败：{}", phoneResp);
                return R.fail("获取手机号失败");
            }

            // 解析手机号（微信返回字段名为 phoneNumber）
            @SuppressWarnings("unchecked")
            Map<String, Object> phoneInfoMap = (Map<String, Object>) phoneInfo;
            String phoneNumber = (String) phoneInfoMap.get("phoneNumber");
            if (StrUtil.isBlank(phoneNumber)) {
                return R.fail("未获取到手机号");
            }

            // 3. 更新用户手机号
            SysUserVo user = userService.selectUserById(userId);
            if (ObjectUtil.isNull(user)) {
                return R.fail("用户不存在");
            }
            SysUserBo userBo = new SysUserBo();
            userBo.setUserId(userId);
            userBo.setPhonenumber(phoneNumber);
            userBo.setUpdateBy(userId);
            userService.updateUserProfile(userBo);

            log.info("用户{}绑定手机号成功：{}", userId, phoneNumber);
            return R.ok();
        } catch (Exception e) {
            log.error("绑定手机号异常", e);
            return R.fail("绑定手机号失败：" + e.getMessage());
        }
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

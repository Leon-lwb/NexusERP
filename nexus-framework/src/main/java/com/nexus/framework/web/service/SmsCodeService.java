package com.nexus.framework.web.service;

import com.nexus.common.constant.CacheConstants;
import com.nexus.common.core.domain.AjaxResult;
import com.nexus.common.core.domain.entity.SysUser;
import com.nexus.common.core.domain.model.SmsCodeBody;
import com.nexus.common.exception.ServiceException;
import com.nexus.common.utils.StringUtils;
import com.nexus.common.core.redis.RedisCache;
import com.nexus.framework.config.SmsConfig;
import com.nexus.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * 短信验证码服务
 * 
 * @author nexus
 */
@Service
public class SmsCodeService
{
    @Autowired
    private ISysUserService userService;
    
    @Autowired
    private SmsConfig smsConfig;
    
    @Autowired
    private RedisCache redisCache;

    private static final String SMS_CODE_PREFIX = CacheConstants.SMS_CODE_KEY;
    private static final int SMS_CODE_LENGTH = 6; // 验证码长度
    private static final int SMS_CODE_EXPIRE = 5 * 60; // 验证码有效期，5分钟
    private static final int SMS_SEND_INTERVAL = 60; // 发送间隔，60秒
    private static final int SMS_DAILY_LIMIT = 10; // 每日发送上限
    private static final int SMS_ERROR_LIMIT = 5; // 错误次数限制

    /**
     * 生成并发送短信验证码
     * @param smsCodeBody 短信验证码请求对象
     * @return 结果
     */
    public AjaxResult sendSmsCode(SmsCodeBody smsCodeBody)
    {
        String mobile = smsCodeBody.getMobile();

        // 验证手机号格式
        if (!isValidMobile(mobile))
        {
            return AjaxResult.error(5001, "手机号码格式错误");
        }

        // 检查是否频繁发送
        if (isSendFrequently(mobile))
        {
            return AjaxResult.error(5003, "验证码发送过于频繁，请稍后再试");
        }

        // 检查当日发送次数
        if (isDailyLimitExceeded(mobile))
        {
            return AjaxResult.error(5006, "今日发送次数已达上限");
        }

        // 生成验证码
        String code = generateSmsCode();

        // 存储到Redis
        String codeKey = SMS_CODE_PREFIX + mobile;
        redisCache.setCacheObject(codeKey, code, SMS_CODE_EXPIRE, TimeUnit.SECONDS);

        // 记录发送次数
        recordSmsSend(mobile);

        // 发送短信验证码
        boolean sendResult = sendSms(mobile, code);
        if (!sendResult)
        {
            // 发送失败，删除已存储的验证码
            redisCache.deleteObject(codeKey);
            return AjaxResult.error("验证码发送失败");
        }

        return AjaxResult.success("验证码发送成功");
    }

    /**
     * 验证短信验证码
     * @param mobile 手机号
     * @param code 验证码
     * @return 验证结果
     */
    public boolean validateSmsCode(String mobile, String code)
    {
        if (!isValidMobile(mobile) || StringUtils.isEmpty(code))
        {
            return false;
        }

        String codeKey = SMS_CODE_PREFIX + mobile;
        String storedCode = redisCache.getCacheObject(codeKey);

        // 验证码不存在或已过期
        if (storedCode == null)
        {
            return false;
        }

        // 验证码错误
        if (!storedCode.equals(code))
        {
            // 记录错误次数
            recordSmsError(mobile);
            return false;
        }

        // 验证成功，删除验证码
        redisCache.deleteObject(codeKey);
        return true;
    }

    /**
     * 发送短信
     * @param mobile 手机号
     * @param code 验证码
     * @return 发送结果
     */
    private boolean sendSms(String mobile, String code)
    {
        // 根据配置的短信服务商发送短信
        String provider = smsConfig.getProvider();
        switch (provider.toLowerCase())
        {
            case "aliyun":
                return sendSmsByAliyun(mobile, code);
            case "tencent":
                return sendSmsByTencent(mobile, code);
            default:
                // 默认使用模拟发送
                System.out.println("【短信验证码】发送至 " + mobile + "，验证码：" + code);
                return true;
        }
    }

    /**
     * 通过阿里云发送短信
     * @param mobile 手机号
     * @param code 验证码
     * @return 发送结果
     */
    private boolean sendSmsByAliyun(String mobile, String code)
    {
        // TODO: 实现阿里云短信发送逻辑
        SmsConfig.Aliyun aliyunConfig = smsConfig.getAliyun();
        if (StringUtils.isEmpty(aliyunConfig.getAccessKeyId()) || StringUtils.isEmpty(aliyunConfig.getAccessKeySecret()))
        {
            // 配置未设置，使用模拟发送
            System.out.println("【阿里云短信验证码】发送至 " + mobile + "，验证码：" + code);
            return true;
        }

        // 这里应该调用阿里云短信服务SDK
        // 由于没有实际的SDK依赖，暂时使用模拟实现
        System.out.println("【阿里云短信验证码】发送至 " + mobile + "，验证码：" + code);
        return true;
    }

    /**
     * 通过腾讯云发送短信
     * @param mobile 手机号
     * @param code 验证码
     * @return 发送结果
     */
    private boolean sendSmsByTencent(String mobile, String code)
    {
        // TODO: 实现腾讯云短信发送逻辑
        SmsConfig.Tencent tencentConfig = smsConfig.getTencent();
        if (StringUtils.isEmpty(tencentConfig.getSecretId()) || StringUtils.isEmpty(tencentConfig.getSecretKey()))
        {
            // 配置未设置，使用模拟发送
            System.out.println("【腾讯云短信验证码】发送至 " + mobile + "，验证码：" + code);
            return true;
        }

        // 这里应该调用腾讯云短信服务SDK
        // 由于没有实际的SDK依赖，暂时使用模拟实现
        System.out.println("【腾讯云短信验证码】发送至 " + mobile + "，验证码：" + code);
        return true;
    }

    /**
     * 验证手机号格式
     * @param mobile 手机号
     * @return 是否有效
     */
    private boolean isValidMobile(String mobile)
    {
        if (StringUtils.isEmpty(mobile))
        {
            return false;
        }

        // 简单的手机号验证正则表达式
        return mobile.matches("^1[3-9]\\d{9}$");
    }

    /**
     * 生成随机验证码
     * @return 验证码
     */
    private String generateSmsCode()
    {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < SMS_CODE_LENGTH; i++)
        {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * 检查是否频繁发送
     * @param mobile 手机号
     * @return 是否频繁发送
     */
    private boolean isSendFrequently(String mobile)
    {
        String intervalKey = SMS_CODE_PREFIX + "interval:" + mobile;
        String lastSendTime = redisCache.getCacheObject(intervalKey);
        if (lastSendTime != null)
        {
            return true;
        }

        // 记录发送时间
        redisCache.setCacheObject(intervalKey, "sent", SMS_SEND_INTERVAL, TimeUnit.SECONDS);
        return false;
    }

    /**
     * 检查当日发送次数是否超限
     * @param mobile 手机号
     * @return 是否超限
     */
    private boolean isDailyLimitExceeded(String mobile)
    {
        String dailyKey = SMS_CODE_PREFIX + "daily:" + mobile + ":" + System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        Integer count = redisCache.getCacheObject(dailyKey);
        if (count == null)
        {
            count = 0;
        }

        return count >= SMS_DAILY_LIMIT;
    }

    /**
     * 记录短信发送
     * @param mobile 手机号
     */
    private void recordSmsSend(String mobile)
    {
        String dailyKey = SMS_CODE_PREFIX + "daily:" + mobile + ":" + System.currentTimeMillis() / (24L * 60 * 60 * 1000);
        Integer count = redisCache.getCacheObject(dailyKey);
        if (count == null)
        {
            count = 0;
        }
        redisCache.setCacheObject(dailyKey, count + 1, 24 * 60 * 60, TimeUnit.SECONDS); // 保存24小时
    }

    /**
     * 记录短信验证错误
     * @param mobile 手机号
     */
    private void recordSmsError(String mobile)
    {
        String errorKey = SMS_CODE_PREFIX + "error:" + mobile;
        Integer count = redisCache.getCacheObject(errorKey);
        if (count == null)
        {
            count = 0;
        }

        redisCache.setCacheObject(errorKey, count + 1, 60, TimeUnit.SECONDS); // 1分钟内统计错误次数

        if (count + 1 >= SMS_ERROR_LIMIT)
        {
            throw new ServiceException("验证码错误次数过多，请稍后再试");
        }
    }
}
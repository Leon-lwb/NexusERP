package com.nexus.common.constant;

/**
 * 全局的key常量 (业务无关的key)
 *
 * @author nbacheng
 */
public interface GlobalConstants {

     /**
     * 业务处理器beanName传递参数
     */
    public static final String HANDLER_NAME = "handlerName";
	
	/**
     * redis消息通道名称
     */
	 public static final String REDIS_TOPIC_NAME="nbcio_redis_topic";
	
    /**
     * 全局 redis key (业务无关的key)
     */
    String GLOBAL_REDIS_KEY = "global:";

    /**
     * 验证码 redis key
     */
    String CAPTCHA_CODE_KEY = GLOBAL_REDIS_KEY + "captcha_codes:";

    /**
     * 防重提交 redis key
     */
    String REPEAT_SUBMIT_KEY = GLOBAL_REDIS_KEY + "repeat_submit:";

    /**
     * 限流 redis key
     */
    String RATE_LIMIT_KEY = GLOBAL_REDIS_KEY + "rate_limit:";

    /**
     * 登录账户密码错误次数 redis key
     */
    String PWD_ERR_CNT_KEY = GLOBAL_REDIS_KEY + "pwd_err_cnt:";

    /**
     * 三方认证 redis key
     */
    String SOCIAL_AUTH_CODE_KEY = GLOBAL_REDIS_KEY + "social_auth_codes:";
}

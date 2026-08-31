package com.nexus.pay.service.alipay;

import com.alipay.easysdk.factory.Factory;
import com.alipay.easysdk.kernel.Config;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * 支付宝支付配置
 *
 * @author zlh
 */
@Configuration
@ConditionalOnProperty(prefix = "pay.alipay", name = "enabled", havingValue = "true")
public class AliPayConfig {

    /** 支付宝应用 ID。 */
    @Value("${pay.alipay.appId}")
    private String appId;

    /** 支付宝异步回调地址。 */
    @Value("${pay.alipay.notifyUrl}")
    private String notifyUrl;

    /** 应用私钥，支持 classpath: 前缀引用证书文件，缺省为空。 */
    @Value("${pay.alipay.appPrivateKey:}")
    private String appPrivateKey;

    /** 支付宝公钥，支持 classpath: 前缀引用证书文件，缺省为空。 */
    @Value("${pay.alipay.alipayPublicKey:}")
    private String alipayPublicKey;

    /** 签名类型，缺省 RSA2。 */
    @Value("${pay.alipay.signType:RSA2}")
    private String signType;

    /** 商户证书路径，用于证书模式，缺省为空。 */
    @Value("${pay.alipay.merchantCertPath:}")
    private String merchantCertPath;

    /** 支付宝证书路径，缺省为空。 */
    @Value("${pay.alipay.alipayCertPath:}")
    private String alipayCertPath;

    /** 支付宝根证书路径，缺省为空。 */
    @Value("${pay.alipay.alipayRootCertPath:}")
    private String alipayRootCertPath;

    /** 支付宝网关地址，缺省 openapi.alipay.com。 */
    @Value("${pay.alipay.gatewayHost:openapi.alipay.com}")
    private String gatewayHost;

    /** 通信协议，缺省 https。 */
    @Value("${pay.alipay.protocol:https}")
    private String protocol;

    /** Spring 应用上下文，用于读取 classpath 证书资源。 */
    @Autowired
    private ApplicationContext applicationContext;

    private String getAppPrivateKey() throws Exception {
        if (appPrivateKey.startsWith("classpath")) {
            Resource resource = applicationContext.getResource(appPrivateKey);
            try (InputStream inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        }
        return appPrivateKey;
    }

    private String getAlipayPublicKey() throws Exception {
        if (alipayPublicKey.startsWith("classpath")) {
            Resource resource = applicationContext.getResource(alipayPublicKey);
            try (InputStream inputStream = resource.getInputStream()) {
                return StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
        }
        return alipayPublicKey;
    }

    /**
     * 构建并注册支付宝 SDK 全局配置（Factory 单例），供各支付能力复用。
     *
     * @return 支付宝 SDK 配置对象
     * @throws Exception 读取证书文件或初始化失败时抛出
     */
    @Bean
    public Config alipayBaseConfig() throws Exception {
        Config config = new Config();
        config.protocol = protocol;
        config.gatewayHost = gatewayHost;
        config.signType = signType;
        config.appId = appId;
        config.merchantPrivateKey = getAppPrivateKey();
        config.merchantCertPath = merchantCertPath;
        config.alipayCertPath = alipayCertPath;
        config.alipayRootCertPath = alipayRootCertPath;
        config.alipayPublicKey = getAlipayPublicKey();
        config.notifyUrl = this.notifyUrl;
        Factory.setOptions(config);
        return config;
    }

}

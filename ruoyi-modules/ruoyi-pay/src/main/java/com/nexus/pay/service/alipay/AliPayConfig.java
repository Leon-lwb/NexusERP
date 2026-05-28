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

    @Value("${pay.alipay.appId}")
    private String appId;

    @Value("${pay.alipay.notifyUrl}")
    private String notifyUrl;

    @Value("${pay.alipay.appPrivateKey:}")
    private String appPrivateKey;

    @Value("${pay.alipay.alipayPublicKey:}")
    private String alipayPublicKey;

    @Value("${pay.alipay.signType:RSA2}")
    private String signType;

    @Value("${pay.alipay.merchantCertPath:}")
    private String merchantCertPath;

    @Value("${pay.alipay.alipayCertPath:}")
    private String alipayCertPath;

    @Value("${pay.alipay.alipayRootCertPath:}")
    private String alipayRootCertPath;

    @Value("${pay.alipay.gatewayHost:openapi.alipay.com}")
    private String gatewayHost;

    @Value("${pay.alipay.protocol:https}")
    private String protocol;

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

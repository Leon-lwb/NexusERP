package com.nexus.pay.service.wechat;

import com.wechat.pay.java.core.RSAAutoCertificateConfig;
import com.wechat.pay.java.core.notification.NotificationParser;
import com.wechat.pay.java.service.payments.nativepay.NativePayService;
import com.wechat.pay.java.service.refund.RefundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 微信支付配置
 *
 * @author ZlH
 */
@Configuration
@ConditionalOnProperty(prefix = "pay.wechat", name = "enabled", havingValue = "true")
public class WxPayConfig {

    /** 微信支付商户号。 */
    @Value("${pay.wechat.merchantId}")
    private String merchantId;

    /** 商户证书序列号。 */
    @Value("${pay.wechat.merchantSerialNumber}")
    private String merchantSerialNumber;

    /** 微信支付 APIv3 密钥。 */
    @Value("${pay.wechat.apiV3Key}")
    private String apiV3Key;

    /** 商户 API 私钥文件路径，支持 classpath: 前缀（会缓存到临时文件）。 */
    @Value("${pay.wechat.privateKeyPath}")
    private String privateKeyPath;

    /** 微信应用 ID（AppID）。 */
    @Value("${pay.wechat.appId}")
    private String appId;

    /** 微信支付异步回调地址。 */
    @Value("${pay.wechat.notifyUrl}")
    private String notifyUrl;

    /** Spring 应用上下文，用于读取 classpath 证书资源。 */
    @Autowired
    private ApplicationContext applicationContext;

    /** @return 微信支付商户号 */
    public String getMerchantId() {
        return merchantId;
    }

    /** @return 微信应用 ID（AppID） */
    public String getAppId() {
        return appId;
    }

    /** @return 微信支付异步回调地址 */
    public String getNotifyUrl() {
        return notifyUrl;
    }

    /** @return 商户证书私钥文件路径（支持 classpath 资源解析并缓存到临时文件）。 */
    public String getPrivateKeyPath() throws Exception {
        if (privateKeyPath.startsWith("classpath:")) {
            Resource resource = applicationContext.getResource(privateKeyPath);
            String tempFilePath = System.getProperty("java.io.tmpdir") + "/temp_wxcert.pem";
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, Paths.get(tempFilePath), StandardCopyOption.REPLACE_EXISTING);
                privateKeyPath = tempFilePath;
            } catch (Exception e) {
                Files.deleteIfExists(Paths.get(tempFilePath));
                throw new RuntimeException("微信支付证书文件读取失败", e);
            }
        }
        return privateKeyPath;
    }

    /**
     * 构建并注册微信支付自动更新证书的全局配置。
     *
     * @return 微信支付 RSA 自动证书配置
     * @throws Exception 读取私钥或初始化失败时抛出
     */
    @Bean
    public RSAAutoCertificateConfig wxpayBaseConfig() throws Exception {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKeyFromPath(getPrivateKeyPath())
                .merchantSerialNumber(merchantSerialNumber)
                .apiV3Key(apiV3Key)
                .build();
    }

    /**
     * 注册 Native 支付服务（用于生成支付二维码）。
     *
     * @return Native 支付服务实例
     * @throws Exception 初始化失败时抛出
     */
    @Bean
    public NativePayService nativePayService() throws Exception {
        return new NativePayService.Builder().config(wxpayBaseConfig()).build();
    }

    /**
     * 注册退款服务。
     *
     * @return 退款服务实例
     * @throws Exception 初始化失败时抛出
     */
    @Bean
    public RefundService refundService() throws Exception {
        return new RefundService.Builder().config(wxpayBaseConfig()).build();
    }

    /**
     * 注册回调通知解析器，用于验签与解析微信回调报文。
     *
     * @return 通知解析器实例
     * @throws Exception 初始化失败时抛出
     */
    @Bean
    public NotificationParser notificationParser() throws Exception {
        return new NotificationParser(wxpayBaseConfig());
    }

}

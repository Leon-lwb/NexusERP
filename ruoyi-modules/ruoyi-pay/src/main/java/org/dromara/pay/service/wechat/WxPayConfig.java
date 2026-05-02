package org.dromara.pay.service.wechat;

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

    @Value("${pay.wechat.merchantId}")
    private String merchantId;

    @Value("${pay.wechat.merchantSerialNumber}")
    private String merchantSerialNumber;

    @Value("${pay.wechat.apiV3Key}")
    private String apiV3Key;

    @Value("${pay.wechat.privateKeyPath}")
    private String privateKeyPath;

    @Value("${pay.wechat.appId}")
    private String appId;

    @Value("${pay.wechat.notifyUrl}")
    private String notifyUrl;

    @Autowired
    private ApplicationContext applicationContext;

    public String getMerchantId() {
        return merchantId;
    }

    public String getAppId() {
        return appId;
    }

    public String getNotifyUrl() {
        return notifyUrl;
    }

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

    @Bean
    public RSAAutoCertificateConfig wxpayBaseConfig() throws Exception {
        return new RSAAutoCertificateConfig.Builder()
                .merchantId(merchantId)
                .privateKeyFromPath(getPrivateKeyPath())
                .merchantSerialNumber(merchantSerialNumber)
                .apiV3Key(apiV3Key)
                .build();
    }

    @Bean
    public NativePayService nativePayService() throws Exception {
        return new NativePayService.Builder().config(wxpayBaseConfig()).build();
    }

    @Bean
    public RefundService refundService() throws Exception {
        return new RefundService.Builder().config(wxpayBaseConfig()).build();
    }

    @Bean
    public NotificationParser notificationParser() throws Exception {
        return new NotificationParser(wxpayBaseConfig());
    }

}

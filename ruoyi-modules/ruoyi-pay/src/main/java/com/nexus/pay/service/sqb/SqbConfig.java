package com.nexus.pay.service.sqb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

/**
 * 收钱吧支付配置
 *
 * @author zlh
 */
@Component
@ConditionalOnProperty(prefix = "pay.sqb", name = "enabled", havingValue = "true")
public class SqbConfig {

    /** 收钱吧开放平台 API 域名。 */
    @Value("${pay.sqb.apiDomain}")
    private String apiDomain;

    /** 收钱吧终端序列号。 */
    @Value("${pay.sqb.terminalSn}")
    private String terminalSn;

    /** 收钱吧终端密钥。 */
    @Value("${pay.sqb.terminalKey}")
    private String terminalKey;

    /** 收钱吧应用 ID。 */
    @Value("${pay.sqb.appId}")
    private String appId;

    /** 收钱吧服务商序列号。 */
    @Value("${pay.sqb.vendorSn}")
    private String vendorSn;

    /** 收钱吧服务商密钥。 */
    @Value("${pay.sqb.vendorKey}")
    private String vendorKey;

    /** 收钱吧异步回调地址。 */
    @Value("${pay.sqb.notifyUrl}")
    private String notifyUrl;

    /** 收钱吧平台 RSA 公钥，支持 classpath: 前缀引用证书文件。 */
    @Value("${pay.sqb.publicKey}")
    private String publicKey;

    /** Spring 应用上下文，用于读取 classpath 证书资源。 */
    @Autowired
    private ApplicationContext applicationContext;

    /**
     * 获取收钱吧平台 RSA 公钥（支持从 classpath 证书文件读取）。
     *
     * @return 收钱吧平台公钥字符串
     * @throws Exception 读取证书文件失败时抛出
     */
    public String getPublicKey() throws Exception {
        if (publicKey.startsWith("classpath")) {
            Resource resource = applicationContext.getResource(publicKey);
            try (InputStream inputStream = resource.getInputStream();
                 BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
                publicKey = bufferedReader.lines().collect(Collectors.joining(System.lineSeparator()));
            }
        }
        return publicKey;
    }

    /** @return 异步回调地址 */
    public String getNotifyUrl() {
        return notifyUrl;
    }

    /** @return 开放平台 API 域名 */
    public String getApiDomain() {
        return apiDomain;
    }

    /** @return 终端序列号 */
    public String getTerminalSn() {
        return terminalSn;
    }

    /** @return 终端密钥 */
    public String getTerminalKey() {
        return terminalKey;
    }

    /** @return 应用 ID */
    public String getAppId() {
        return appId;
    }

    /** @return 服务商序列号 */
    public String getVendorSn() {
        return vendorSn;
    }

    /** @return 服务商密钥 */
    public String getVendorKey() {
        return vendorKey;
    }

}

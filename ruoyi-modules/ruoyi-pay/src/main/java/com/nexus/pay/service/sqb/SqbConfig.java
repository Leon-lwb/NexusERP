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

    @Value("${pay.sqb.apiDomain}")
    private String apiDomain;

    @Value("${pay.sqb.terminalSn}")
    private String terminalSn;

    @Value("${pay.sqb.terminalKey}")
    private String terminalKey;

    @Value("${pay.sqb.appId}")
    private String appId;

    @Value("${pay.sqb.vendorSn}")
    private String vendorSn;

    @Value("${pay.sqb.vendorKey}")
    private String vendorKey;

    @Value("${pay.sqb.notifyUrl}")
    private String notifyUrl;

    @Value("${pay.sqb.publicKey}")
    private String publicKey;

    @Autowired
    private ApplicationContext applicationContext;

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

    public String getNotifyUrl() {
        return notifyUrl;
    }

    public String getApiDomain() {
        return apiDomain;
    }

    public String getTerminalSn() {
        return terminalSn;
    }

    public String getTerminalKey() {
        return terminalKey;
    }

    public String getAppId() {
        return appId;
    }

    public String getVendorSn() {
        return vendorSn;
    }

    public String getVendorKey() {
        return vendorKey;
    }

}

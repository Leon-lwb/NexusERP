package com.nexus.im.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * IM配置属性
 *
 * @author nexus
 */
@Data
@Component
@ConfigurationProperties(prefix = "im")
@EnableConfigurationProperties
public class ImProperties {

    private boolean enabled = true;

    private String serverUrl = "http://localhost:8080";

    private String apiKey;

    private String appId;

    private String appSecret;

    private boolean ssoEnabled = true;

    private UserSync userSync = new UserSync();

    @Data
    public static class UserSync {
        private boolean autoSync = true;
        private int syncInterval = 60;
        private boolean syncAvatar = true;
    }
}

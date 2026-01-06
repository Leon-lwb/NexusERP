package com.nexus.framework.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 短信配置类
 * 
 * @author nexus
 */
@Component
@ConfigurationProperties(prefix = "sms")
public class SmsConfig
{
    /**
     * 短信服务提供商
     */
    private String provider = "aliyun";
    
    /**
     * 阿里云短信配置
     */
    private Aliyun aliyun = new Aliyun();
    
    /**
     * 腾讯云短信配置
     */
    private Tencent tencent = new Tencent();
    
    public String getProvider()
    {
        return provider;
    }
    
    public void setProvider(String provider)
    {
        this.provider = provider;
    }
    
    public Aliyun getAliyun()
    {
        return aliyun;
    }
    
    public void setAliyun(Aliyun aliyun)
    {
        this.aliyun = aliyun;
    }
    
    public Tencent getTencent()
    {
        return tencent;
    }
    
    public void setTencent(Tencent tencent)
    {
        this.tencent = tencent;
    }
    
    public static class Aliyun
    {
        private String accessKeyId;
        private String accessKeySecret;
        private String signName;
        private String templateCode;
        
        public String getAccessKeyId()
        {
            return accessKeyId;
        }
        
        public void setAccessKeyId(String accessKeyId)
        {
            this.accessKeyId = accessKeyId;
        }
        
        public String getAccessKeySecret()
        {
            return accessKeySecret;
        }
        
        public void setAccessKeySecret(String accessKeySecret)
        {
            this.accessKeySecret = accessKeySecret;
        }
        
        public String getSignName()
        {
            return signName;
        }
        
        public void setSignName(String signName)
        {
            this.signName = signName;
        }
        
        public String getTemplateCode()
        {
            return templateCode;
        }
        
        public void setTemplateCode(String templateCode)
        {
            this.templateCode = templateCode;
        }
    }
    
    public static class Tencent
    {
        private String secretId;
        private String secretKey;
        private String sdkAppId;
        private String templateId;
        private String signName;
        
        public String getSecretId()
        {
            return secretId;
        }
        
        public void setSecretId(String secretId)
        {
            this.secretId = secretId;
        }
        
        public String getSecretKey()
        {
            return secretKey;
        }
        
        public void setSecretKey(String secretKey)
        {
            this.secretKey = secretKey;
        }
        
        public String getSdkAppId()
        {
            return sdkAppId;
        }
        
        public void setSdkAppId(String sdkAppId)
        {
            this.sdkAppId = sdkAppId;
        }
        
        public String getTemplateId()
        {
            return templateId;
        }
        
        public void setTemplateId(String templateId)
        {
            this.templateId = templateId;
        }
        
        public String getSignName()
        {
            return signName;
        }
        
        public void setSignName(String signName)
        {
            this.signName = signName;
        }
    }
}
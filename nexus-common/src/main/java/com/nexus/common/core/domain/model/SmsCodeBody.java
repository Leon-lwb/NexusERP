package com.nexus.common.core.domain.model;

/**
 * 短信验证码请求对象
 * 
 * @author nexus
 */
public class SmsCodeBody
{
    /**
     * 手机号
     */
    private String mobile;

    public String getMobile()
    {
        return mobile;
    }

    public void setMobile(String mobile)
    {
        this.mobile = mobile;
    }
}
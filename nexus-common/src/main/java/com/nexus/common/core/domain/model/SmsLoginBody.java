package com.nexus.common.core.domain.model;

/**
 * 短信登录对象
 * 
 * @author nexus
 */
public class SmsLoginBody
{
    /**
     * 手机号
     */
    private String mobile;

    /**
     * 验证码
     */
    private String code;

    public String getMobile()
    {
        return mobile;
    }

    public void setMobile(String mobile)
    {
        this.mobile = mobile;
    }

    public String getCode()
    {
        return code;
    }

    public void setCode(String code)
    {
        this.code = code;
    }
}
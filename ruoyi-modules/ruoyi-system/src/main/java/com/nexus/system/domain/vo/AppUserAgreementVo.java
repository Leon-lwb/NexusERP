package com.nexus.system.domain.vo;

import com.nexus.system.domain.AppUserAgreement;
import io.github.linpeilie.annotations.AutoMapper;
import lombok.Data;

import java.util.Date;

/**
 * App用户协议同意视图对象
 *
 * @author nexus
 * @date 2026-08-05
 */
@Data
@AutoMapper(target = AppUserAgreement.class)
public class AppUserAgreementVo {

    /**
     * 协议标识(privacy_policy/user_service_agreement)
     */
    private String agreementKey;

    /**
     * 协议版本
     */
    private String agreementVersion;

    /**
     * 同意时间
     */
    private Date agreeTime;

}

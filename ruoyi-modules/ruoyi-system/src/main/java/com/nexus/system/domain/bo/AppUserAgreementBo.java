package com.nexus.system.domain.bo;

import lombok.Data;

/**
 * App用户协议同意业务对象
 *
 * @author nexus
 * @date 2026-08-05
 */
@Data
public class AppUserAgreementBo {

    /**
     * 协议标识(privacy_policy/user_service_agreement)
     */
    private String agreementKey;

    /**
     * 协议版本
     */
    private String agreementVersion;

}

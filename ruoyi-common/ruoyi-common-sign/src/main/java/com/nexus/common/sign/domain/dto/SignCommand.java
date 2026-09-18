package com.nexus.common.sign.domain.dto;

/**
 * 签名入参。
 *
 * @param bizType          业务类型（必填，见 SignBizType）
 * @param bizId            业务单据ID（可空，如纯审批动作）
 * @param bizNo            业务单据编号（可空）
 * @param signatureMeaning 签署含义（必填，如"审批通过"）
 * @param sealId           所用印章ID（可空=仅逻辑签名不盖章）
 * @param authMethod       认证方式（必填，见 SignAuthMethod）
 * @param credential       二级认证凭证（LOGIN_ONLY 时可为空）
 * @author nexus
 */
public record SignCommand(
    String bizType,
    Long bizId,
    String bizNo,
    String signatureMeaning,
    Long sealId,
    String authMethod,
    String credential
) {
}

package com.nexus.common.sign.domain.dto;

/**
 * 签名入参。
 *
 * <p><b>正文绑定（MES-SOP-05 起）</b>：{@code docOssId}/{@code docHash} 用于「对既有正文的
 * 逻辑签名」（如 MES {@code SOP_VIEW} 已阅确认）。此前逻辑签名一律走
 * {@code DocLink.NONE}，{@code es_signature.doc_hash} 恒为 null，链哈希里也没有正文摘要 ——
 * 签完字后把 OSS 里的正文换成另一份，验签依然通过，签名无法证明「签署时看到的是哪一份内容」。
 * 传入 {@code docHash} 后，正文摘要会编入链哈希（{@code SignatureChainCalculator.computeV2}），
 * 替换正文即可被检出。</p>
 *
 * <p>向后兼容：保留了 7 参构造器，既有调用点（审批签批/NCR 处置等）无需改动，
 * 其 {@code docOssId}/{@code docHash} 为 null，行为与修复前一致。</p>
 *
 * @param bizType          业务类型（必填，见 SignBizType）
 * @param bizId            业务单据ID（可空，如纯审批动作）
 * @param bizNo            业务单据编号（可空）
 * @param signatureMeaning 签署含义（必填，如"审批通过"）
 * @param sealId           所用印章ID（可空=仅逻辑签名不盖章）
 * @param authMethod       认证方式（必填，见 SignAuthMethod）
 * @param credential       二级认证凭证（LOGIN_ONLY 时可为空）
 * @param docOssId         被签署正文的 OSS ID（可空；逻辑签名不产出新文档，故落
 *                         {@code es_signature.src_doc_oss_id} 而非 {@code doc_oss_id}）
 * @param docHash          被签署正文的 SM3（可空；非空时编入链哈希并落
 *                         {@code es_signature.doc_hash}）
 * @author nexus
 */
public record SignCommand(
    String bizType,
    Long bizId,
    String bizNo,
    String signatureMeaning,
    Long sealId,
    String authMethod,
    String credential,
    Long docOssId,
    String docHash
) {

    /**
     * 向后兼容构造器：不含正文绑定的逻辑签名（{@code docOssId}/{@code docHash} 为 null）。
     *
     * @param bizType          业务类型
     * @param bizId            业务单据ID
     * @param bizNo            业务单据编号
     * @param signatureMeaning 签署含义
     * @param sealId           印章ID
     * @param authMethod       认证方式
     * @param credential       二级认证凭证
     */
    public SignCommand(String bizType, Long bizId, String bizNo, String signatureMeaning,
                       Long sealId, String authMethod, String credential) {
        this(bizType, bizId, bizNo, signatureMeaning, sealId, authMethod, credential, null, null);
    }
}

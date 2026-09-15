package com.nexus.common.sign.support;

import com.nexus.common.sign.util.Sm3Utils;

/**
 * 签名哈希链计算（纯函数，无副作用，便于单测）。
 *
 * <pre>
 * chain_hash = SM3( prev_hash ‖ signature_id ‖ biz_type ‖ biz_id ‖
 *                   signer_user_id ‖ seal_id ‖ signature_meaning ‖
 *                   doc_hash ‖ signed_at_epoch_ms )
 * </pre>
 *
 * @author nexus
 */
public final class SignatureChainCalculator {

    private SignatureChainCalculator() {
    }

    /**
     * 计算链哈希。
     *
     * @param prevHash        前一条链哈希（链首传 null）
     * @param signatureId     本条签名ID
     * @param bizType         业务类型
     * @param bizId           业务单据ID（可空）
     * @param signerUserId    签署人用户ID
     * @param sealId          印章ID（可空）
     * @param meaning         签署含义
     * @param docHash         文档 SM3（可空）
     * @param signedAtEpochMs 签署时间戳（毫秒）
     * @return 64 位小写十六进制链哈希
     */
    public static String compute(String prevHash,
                                 Long signatureId,
                                 String bizType,
                                 Long bizId,
                                 Long signerUserId,
                                 Long sealId,
                                 String meaning,
                                 String docHash,
                                 long signedAtEpochMs) {
        return Sm3Utils.hashHex(
            prevHash,
            str(signatureId),
            bizType,
            str(bizId),
            str(signerUserId),
            str(sealId),
            meaning,
            docHash,
            str(signedAtEpochMs)
        );
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

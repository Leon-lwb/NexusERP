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

    /**
     * 计算链哈希（v2 公式，2026-09-18 起）。
     *
     * <p>在 v1 输入的基础上追加了 {@code auth_method} 与 {@code biz_no}：
     * auth_method 决定证据效力（该签名是否经过二级认证），此前不入哈希意味着持有库写权限者
     * 可在 PASSWORD ↔ LOGIN_ONLY 之间改写而不被验签发现。v1 公式原样保留
     * （{@link #compute}），专供验签时兼容公式升级前落库的存量记录 —— 新签名一律走 v2。</p>
     *
     * <pre>
     * chain_hash = SM3( prev_hash ‖ signature_id ‖ biz_type ‖ biz_id ‖
     *                   signer_user_id ‖ seal_id ‖ signature_meaning ‖
     *                   doc_hash ‖ signed_at_epoch_ms ‖ auth_method ‖ biz_no )
     * </pre>
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
     * @param authMethod      认证方式（PASSWORD/SMS/FACE/LOGIN_ONLY）
     * @param bizNo           业务单据编号（可空）
     * @return 64 位小写十六进制链哈希
     */
    public static String computeV2(String prevHash,
                                   Long signatureId,
                                   String bizType,
                                   Long bizId,
                                   Long signerUserId,
                                   Long sealId,
                                   String meaning,
                                   String docHash,
                                   long signedAtEpochMs,
                                   String authMethod,
                                   String bizNo) {
        return Sm3Utils.hashHex(
            prevHash,
            str(signatureId),
            bizType,
            str(bizId),
            str(signerUserId),
            str(sealId),
            meaning,
            docHash,
            str(signedAtEpochMs),
            authMethod,
            bizNo
        );
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}

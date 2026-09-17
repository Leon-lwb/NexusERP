package com.nexus.common.sign.support.pdf;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.security.Security;

/**
 * 密码学 Provider 引导。
 *
 * <p><b>为什么用「追加」而不是「置顶」：</b>spike 实测（
 * {@code doc/system/specs/2026-09-16-pdfbox-spike-report.md} Q1）三种模式下产出的
 * 签名<b>字节完全一致</b>；{@code insertProviderAt(BC, 1)} 置顶只会把默认摘要/签名实现
 * 从 {@code SUN}/{@code SunRsaSign} 换成 {@code BC}，凭空扩大影响面（同一 JVM 里其它
 * 依赖 BC 的组件也会被改变行为）。因此这里只在缺失时<b>追加</b>，绝不置顶、
 * 也绝不 {@code removeProvider}。</p>
 *
 * <p>idempotent：{@code Security.getProvider("BC") != null} 时直接返回，
 * 单测里反复调用不会互相干扰。</p>
 *
 * @author nexus
 */
public final class PdfCryptoProviders {

    private PdfCryptoProviders() {
    }

    /**
     * 确保 BouncyCastle Provider 已注册（追加式，幂等）。
     */
    public static void ensureBouncyCastle() {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            synchronized (PdfCryptoProviders.class) {
                if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                    Security.addProvider(new BouncyCastleProvider());
                }
            }
        }
    }
}

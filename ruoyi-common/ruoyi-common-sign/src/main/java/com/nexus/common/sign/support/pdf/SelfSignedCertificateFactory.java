package com.nexus.common.sign.support.pdf;

import com.nexus.common.core.exception.ServiceException;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 自签证书工厂（PDF 数字签名用）。
 *
 * <p><b>证书决策（P2a-2 的明确取舍，勿在未决前改成其它方案）：</b></p>
 * <ul>
 *   <li><b>每次签署现场生成一对 RSA-2048 密钥 + 一张自签 X.509</b>，
 *       并把证书<b>内嵌进 CMS</b>（{@code Certs} 字段）。这样签名是
 *       <b>自包含</b>的：证书随 PDF 一起流转，验签方不需要访问任何证书库 ——
 *       <b>不需要改数据库结构、不引入证书表</b>。</li>
 *   <li><b>信任链校验明确不在本阶段范围</b>。自签证书在 Acrobat 里会显示
 *       「签名有效性未知 / 无法验证信任链」，但<b>完整性校验（文档是否被改动）
 *       完全正常</b>。本项目当前要解决的正是完整性，而不是 CA 信任。</li>
 *   <li>代价：私钥不落库，签名后即随请求消失，<b>无法事后重签同一身份</b>；
 *       将来若要「企业级可追溯签名者身份」，需要引入 CA 签发/密钥托管，
 *       届时只替换本类即可，调用方不变。</li>
 * </ul>
 *
 * @author nexus
 */
public final class SelfSignedCertificateFactory {

    /** 密钥长度 */
    private static final int RSA_KEY_SIZE = 2048;
    /** 默认有效期（天） */
    private static final int DEFAULT_VALID_DAYS = 365;
    /** 生效时间前移（天），容忍客户端/服务端轻微时钟漂移 */
    private static final int BACKDATE_DAYS = 1;

    private static final SecureRandom RANDOM = new SecureRandom();

    private SelfSignedCertificateFactory() {
    }

    /**
     * 签名身份：私钥 + 自签证书（证书主题 CN 为签署人姓名快照）。
     *
     * @param privateKey  私钥
     * @param certificate 自签证书
     */
    public record SigningIdentity(PrivateKey privateKey, X509Certificate certificate) {
    }

    /**
     * 生成签名身份。
     *
     * @param commonName 证书主题 CN（签署人姓名快照；含中文亦可，按 UTF8String 编码）
     * @param orgUnit    证书主题 OU（如 {@code Nexus-ERP 电子签章}）
     * @param validDays  有效期天数（&le;0 时取默认 365 天）
     * @return 私钥 + 自签证书
     */
    public static SigningIdentity generate(String commonName, String orgUnit, int validDays) {
        PdfCryptoProviders.ensureBouncyCastle();
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(RSA_KEY_SIZE, RANDOM);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();

            Instant now = Instant.now();
            Date notBefore = Date.from(now.minus(BACKDATE_DAYS, ChronoUnit.DAYS));
            Date notAfter = Date.from(now.plus(validDays > 0 ? validDays : DEFAULT_VALID_DAYS, ChronoUnit.DAYS));
            BigInteger serial = new BigInteger(64, RANDOM).abs().add(BigInteger.ONE);

            // X500Name 的字符串形式以 ',' 分隔 RDN，主题值里出现特殊字符会解析错乱，先净化
            String subjectDn = "CN=" + sanitize(commonName)
                + ", OU=" + sanitize(orgUnit)
                + ", O=Nexus-ERP, C=CN";
            X500Name subject = new X500Name(subjectDn);

            JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
                subject, serial, notBefore, notAfter, subject, keyPair.getPublic());
            builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
            builder.addExtension(Extension.keyUsage, true,
                new KeyUsage(KeyUsage.digitalSignature | KeyUsage.nonRepudiation));

            ContentSigner signer = new JcaContentSignerBuilder(PdfCmsSigner.SIGNATURE_ALGORITHM)
                .build(keyPair.getPrivate());
            X509Certificate certificate = new JcaX509CertificateConverter()
                .getCertificate(builder.build(signer));
            // 自签名自校验：拿到一张连自己都验不过的证书，是密钥/算法配错的最早信号
            certificate.verify(keyPair.getPublic());
            return new SigningIdentity(keyPair.getPrivate(), certificate);
        } catch (Exception e) {
            throw new ServiceException("生成自签证书失败：{}", e.getMessage());
        }
    }

    /**
     * 净化 X.500 主题值中的分隔符/转义字符。
     *
     * @param value 原始值
     * @return 可用于 DN 字符串的值
     */
    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[,+=;<>#\"\\\\]", "_").trim();
    }
}

package com.nexus.common.sign.support.pdf;

import com.nexus.common.core.exception.ServiceException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.SignatureInterface;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignedDataGenerator;
import org.bouncycastle.cms.CMSTypedData;
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder;
import org.bouncycastle.cert.jcajce.JcaCertStore;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Calendar;
import java.util.List;

/**
 * PDF 数字签名（CMS / PKCS#7 detached，RSA-2048 + SHA-256）。
 *
 * <p><b>增量保存的硬前置（spike 结论，勿绕过）：</b>{@code saveIncremental}
 * 要求文档是「从文件或流加载」的，全新内存文档会抛
 * {@code IllegalStateException: document was not loaded from a file or a stream}
 * 并产出 0 字节。所以本类的入口是「已经是字节的 PDF」：
 * 先 {@code Loader.loadPDF(bytes)}（此时前置已满足）→ {@code addSignature}
 * → {@code saveIncremental(out)}。上游 {@link PdfContentStamper} 负责先
 * {@code save} 一次把盖章结果变成字节。全程内存，无临时文件。</p>
 *
 * <p>签名结构：{@code /Filter = Adobe.PPKLite}、{@code /SubFilter = adbe.pkcs7.detached}，
 * 由 {@code addSignature} 自动创建<b>不可见</b>签名域（0×0 控件），
 * 印章是另画的图片 —— 二者互不干扰。</p>
 *
 * @author nexus
 */
public final class PdfCmsSigner {

    /** 摘要+签名算法（RSA-2048 / SHA-256） */
    public static final String SIGNATURE_ALGORITHM = "SHA256withRSA";

    private PdfCmsSigner() {
    }

    /**
     * 对 PDF 施加 detached CMS/PKCS#7 数字签名。
     *
     * @param pdf         待签 PDF 字节（必须已是可加载的完整 PDF，见类注释）
     * @param privateKey  签名私钥（RSA）
     * @param certificate 签名证书（自签，随 CMS 一并内嵌）
     * @param signerName  签署人姓名快照（写入签名域 /Name）；空则不写
     * @param reason      签署含义（写入签名域 /Reason，Acrobat 签名面板可见）；空则不写
     * @return 签名后的 PDF 字节
     * @throws ServiceException PDF 加载失败或 CMS 生成失败
     */
    public static byte[] sign(byte[] pdf, PrivateKey privateKey, X509Certificate certificate,
                              String signerName, String reason) {
        PdfCryptoProviders.ensureBouncyCastle();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDSignature signature = new PDSignature();
            signature.setFilter(PDSignature.FILTER_ADOBE_PPKLITE);
            signature.setSubFilter(PDSignature.SUBFILTER_ADBE_PKCS7_DETACHED);
            signature.setSignDate(Calendar.getInstance());
            if (signerName != null && !signerName.isBlank()) {
                signature.setName(signerName);
            }
            if (reason != null && !reason.isBlank()) {
                signature.setReason(reason);
            }
            SignatureInterface signatureInterface =
                content -> buildDetachedCms(content, privateKey, certificate);
            doc.addSignature(signature, signatureInterface);
            ByteArrayOutputStream signed = new ByteArrayOutputStream();
            doc.saveIncremental(signed);
            return signed.toByteArray();
        } catch (IOException e) {
            throw new ServiceException("PDF 数字签名失败：{}", e.getMessage());
        }
    }

    /**
     * 生成 detached CMS/PKCS#7（内容不封装进 CMS，PDF 场景的标准做法）。
     *
     * @param content     PDFBox 回调给的「被签名字节」流（ByteRange 两段拼接）
     * @param privateKey  签名私钥
     * @param certificate 签名证书
     * @return CMS DER 字节
     * @throws IOException 供 PDFBox 的 {@link SignatureInterface} 契约使用
     */
    private static byte[] buildDetachedCms(InputStream content, PrivateKey privateKey,
                                           X509Certificate certificate) throws IOException {
        try {
            ContentSigner signer = new JcaContentSignerBuilder(SIGNATURE_ALGORITHM).build(privateKey);
            CMSSignedDataGenerator generator = new CMSSignedDataGenerator();
            generator.addSignerInfoGenerator(new JcaSignerInfoGeneratorBuilder(
                new JcaDigestCalculatorProviderBuilder().build()).build(signer, certificate));
            // 证书随签名内嵌（detached CMS 的 Certs 字段）：验签方无需任何外部证书源
            generator.addCertificates(new JcaCertStore(List.of(certificate)));
            // 必须声明为 CMSTypedData：generate(CMSTypedData, boolean) 不接受更宽的 CMSProcessable
            CMSTypedData signedContent = new PdfCmsTypedData(content);
            CMSSignedData cms = generator.generate(signedContent, false);
            return cms.getEncoded();
        } catch (Exception e) {
            throw new IOException("生成 CMS/PKCS#7 签名失败：" + e.getMessage(), e);
        }
    }
}

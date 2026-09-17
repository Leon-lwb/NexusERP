package com.nexus.common.sign.support.pdf;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.DocVerifyVerdict;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.List;

/**
 * 文档级验签器 —— <b>PDFBox 核心包没有任何验签 API，这是新增的真实代码</b>。
 *
 * <p><b>为什么必须两条检查一起做（spike 实测，缺一不可）：</b></p>
 * <ol>
 *   <li><b>只做 CMS 验签会漏报</b>：签名完成后，① 往文件尾部追加几个裸字节、
 *       ② 用 {@code saveIncremental} 追加一个真实修订（改页面），
 *       两种情况 {@code SignerInformation#verify()} <b>都仍然返回 true</b> ——
 *       因为被签名覆盖的那段字节确实没被动过。要发现它们，只能检查
 *       {@code /ByteRange} 是否覆盖到文件末尾。</li>
 *   <li><b>只做覆盖检查会误报</b>：纯尾部裸字节不会形成新的 xref/修订，阅读器会直接忽略，
 *       把它和「覆盖区间内被改写」判成同一等级是错的。因此覆盖检查必须再叠加
 *       「未覆盖区里有没有新的 {@code startxref}/{@code %%EOF}」来区分<b>追加修订</b>和<b>裸字节</b>。</li>
 * </ol>
 *
 * <p>反过来，覆盖检查对「覆盖区间内等长改写」完全无感（长度没变），只有 CMS 摘要比对
 * （{@code CMSSignerDigestMismatchException}）能抓到。两条检查互补，最终结论见
 * {@link DocVerifyVerdict}。</p>
 *
 * <p><b>本验签器比 Acrobat 弱在哪（如实说明）：</b>不做信任链/吊销/时间戳/签名策略校验，
 * 只输出密码学完整性结论 —— 自签证书会显示「信任未知」，但完整性结论是可靠的。</p>
 *
 * @author nexus
 */
public final class PdfDocumentVerifier {

    private static final byte[] EOF_MARKER = "%%EOF".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] STARTXREF_MARKER = "startxref".getBytes(StandardCharsets.US_ASCII);

    /** RSA 加密算法 OID（RFC 8017） */
    private static final String OID_RSA_ENCRYPTION = "1.2.840.113549.1.1.1";

    private PdfDocumentVerifier() {
    }

    /**
     * 校验一份（可能已被篡改的）PDF 的签名完整性。
     *
     * @param pdfBytes PDF 字节
     * @return 验签结果（含两条检查的原始观测值）
     * @throws ServiceException 内容为空 / 非 PDF / PDF 无法解析
     */
    public static PdfVerifyOutcome verify(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new ServiceException("签名文档内容为空");
        }
        if (!PdfContentStamper.isPdf(pdfBytes)) {
            throw new ServiceException("仅支持 PDF 文件");
        }
        PdfCryptoProviders.ensureBouncyCastle();

        PdfVerifyOutcome out = new PdfVerifyOutcome();
        out.setFileSize(pdfBytes.length);

        PDSignature signature;
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            List<PDSignature> dictionaries = distinctSignatures(doc.getSignatureDictionaries());
            out.setSignatureCount(dictionaries.size());
            if (dictionaries.isEmpty()) {
                out.getMessages().add("文档中未找到任何数字签名");
                return finish(out, DocVerifyVerdict.NO_SIGNATURE);
            }
            // 多签场景取「最外层」（ByteRange 起点最大）的那一个，即最后一次签署
            signature = dictionaries.get(dictionaries.size() - 1);
        } catch (IOException e) {
            throw new ServiceException("PDF 解析失败，无法验签：{}", e.getMessage());
        }

        Calendar signDate = signature.getSignDate();
        if (signDate != null) {
            out.setSignedAt(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss z").format(signDate.getTime()));
        }

        int[] byteRange = signature.getByteRange();
        out.setByteRange(byteRange);
        if (byteRange == null || byteRange.length != 4) {
            out.getMessages().add("签名域缺少有效的 /ByteRange，无法确定被签名的字节范围");
            return finish(out, DocVerifyVerdict.INVALID_SIGNATURE);
        }

        int coveredEnd = byteRange[2] + byteRange[3];
        out.setCoveredEnd(coveredEnd);
        int trailingBytes = Math.max(pdfBytes.length - coveredEnd, 0);
        out.setTrailingBytes(trailingBytes);

        int tailStart = Math.max(coveredEnd, 0);
        out.setEofCount(count(pdfBytes, EOF_MARKER, 0, pdfBytes.length));
        out.setEofCountInTail(count(pdfBytes, EOF_MARKER, tailStart, pdfBytes.length));
        out.setStartxrefCount(count(pdfBytes, STARTXREF_MARKER, 0, pdfBytes.length));
        out.setStartxrefCountInTail(count(pdfBytes, STARTXREF_MARKER, tailStart, pdfBytes.length));

        boolean revisionAppended = trailingBytes > 0
            && (out.getEofCountInTail() > 0 || out.getStartxrefCountInTail() > 0);
        out.setRevisionAppended(revisionAppended);
        out.setCoveragePassed(coveredEnd <= pdfBytes.length && trailingBytes == 0);

        out.getMessages().add("观测值：ByteRange=[" + byteRange[0] + "," + byteRange[1] + ","
            + byteRange[2] + "," + byteRange[3] + "]，覆盖至第 " + coveredEnd + " 字节，文件共 "
            + pdfBytes.length + " 字节，未覆盖 " + trailingBytes + " 字节；%%EOF 共 "
            + out.getEofCount() + " 次（未覆盖区 " + out.getEofCountInTail() + " 次），startxref 共 "
            + out.getStartxrefCount() + " 次（未覆盖区 " + out.getStartxrefCountInTail() + " 次）");

        // 文件比签名覆盖范围还短：整体重写/截断，被签名的原始字节已不存在，
        // 此时 getSignedContent 会抛 IllegalArgumentException("Input buffer too short")，先判掉
        if (coveredEnd > pdfBytes.length) {
            out.getMessages().add("文档已被重写或截断：签名覆盖到第 " + coveredEnd + " 字节，"
                + "实际文件仅 " + pdfBytes.length + " 字节，签名覆盖的内容已不存在");
            return finish(out, DocVerifyVerdict.TRUNCATED);
        }

        verifyCms(pdfBytes, signature, out);

        if (!out.isCmsPassed()) {
            return finish(out, out.isInPlaceModified()
                ? DocVerifyVerdict.MODIFIED_IN_PLACE
                : DocVerifyVerdict.INVALID_SIGNATURE);
        }
        if (trailingBytes == 0) {
            out.getMessages().add("签名完整：CMS/PKCS#7 验签通过，且签名覆盖文件的全部 "
                + pdfBytes.length + " 字节");
            return finish(out, DocVerifyVerdict.OK);
        }
        if (revisionAppended) {
            out.getMessages().add("文档已被修改：签名之后又追加了 " + trailingBytes
                + " 字节并形成新的 PDF 修订（未覆盖区出现 %%EOF " + out.getEofCountInTail()
                + " 次、startxref " + out.getStartxrefCountInTail() + " 次），该部分未被签名覆盖。"
                + "注意：仅做 CMS 验签会漏掉这种情况");
            return finish(out, DocVerifyVerdict.REVISION_APPENDED);
        }
        out.getMessages().add("签名之后追加了 " + trailingBytes
            + " 字节未被覆盖的裸数据，但未构成新的 PDF 修订（未覆盖区没有 startxref/%%EOF），"
            + "阅读器通常会忽略，Acrobat 大概率仍显示「未修改」；"
            + "CMS 验签通过，严格意义上文件已超出签名覆盖范围，建议人工确认来源");
        return finish(out, DocVerifyVerdict.UNSIGNED_TRAILING_BYTES);
    }

    /**
     * 检查①：CMS/PKCS#7 验签（用签名内嵌的证书公钥验签）。
     *
     * @param pdfBytes  文件字节
     * @param signature 签名域
     * @param out       结果（就地写入 cmsPassed/inPlaceModified/证书与算法信息）
     */
    private static void verifyCms(byte[] pdfBytes, PDSignature signature, PdfVerifyOutcome out) {
        try {
            byte[] contents = signature.getContents(pdfBytes);
            if (contents == null || contents.length == 0) {
                out.getMessages().add("签名域 /Contents 为空，没有可验证的 CMS 数据");
                return;
            }
            byte[] signedContent = signature.getSignedContent(pdfBytes);
            CMSSignedData cms = new CMSSignedData(new CMSProcessableByteArray(signedContent), contents);
            Collection<SignerInformation> signers = cms.getSignerInfos().getSigners();
            if (signers.isEmpty()) {
                out.getMessages().add("CMS 中没有签名者信息（SignerInfos 为空）");
                return;
            }
            SignerInformation signerInfo = signers.iterator().next();
            out.setDigestAlgorithm(digestName(signerInfo.getDigestAlgOID()));
            out.setSignatureAlgorithm(signatureAlgorithmName(signerInfo));

            Collection<X509CertificateHolder> matches = cms.getCertificates().getMatches(signerInfo.getSID());
            if (matches.isEmpty()) {
                out.getMessages().add("CMS 中未包含与该签名者匹配的证书，无法验证签名");
                return;
            }
            X509CertificateHolder holder = matches.iterator().next();
            out.setSignerSubject(holder.getSubject().toString());
            out.setSignerIssuer(holder.getIssuer().toString());

            boolean ok = signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().build(holder));
            out.setCmsPassed(ok);
            if (!ok) {
                out.getMessages().add("CMS 验签返回 false（签名值与证书公钥不匹配）");
            }
        } catch (CMSSignerDigestMismatchException e) {
            // 「文档已被修改」的精确触发点：覆盖区间内的字节被改过，摘要对不上
            out.setInPlaceModified(true);
            out.getMessages().add("文档已被修改：签名覆盖区间内的内容与签名时不一致，"
                + "CMS 消息摘要校验失败（" + e.getClass().getSimpleName() + "：" + e.getMessage() + "）");
        } catch (Exception e) {
            out.getMessages().add("数字签名无法验证：" + e.getClass().getSimpleName()
                + (e.getMessage() == null ? "" : " - " + e.getMessage()));
        }
    }

    /**
     * 取出「互不相同」的签名字典。
     *
     * <p>PDFBox 的 {@code getSignatureDictionaries()} 会同时从 AcroForm 域和
     * {@code /Root /Perms} 收集，同一次签名的字典可能被返回两次；按 ByteRange 起始偏移去重，
     * 并按起始偏移升序排列，末位即最近一次签署。</p>
     *
     * @param dictionaries PDFBox 返回的签名字典
     * @return 去重并按签署顺序升序排列的列表
     */
    private static List<PDSignature> distinctSignatures(List<PDSignature> dictionaries) {
        List<PDSignature> result = new ArrayList<>();
        if (dictionaries == null) {
            return result;
        }
        List<Long> seen = new ArrayList<>();
        for (PDSignature dictionary : dictionaries) {
            int[] range = dictionary.getByteRange();
            long key = (range == null || range.length != 4) ? -1L : ((long) range[2] << 32) | range[3];
            if (!seen.contains(key)) {
                seen.add(key);
                result.add(dictionary);
            }
        }
        result.sort((a, b) -> {
            int[] ra = a.getByteRange();
            int[] rb = b.getByteRange();
            int sa = (ra == null || ra.length != 4) ? -1 : ra[2];
            int sb = (rb == null || rb.length != 4) ? -1 : rb[2];
            return Integer.compare(sa, sb);
        });
        return result;
    }

    /**
     * 统计 ASCII 标记出现次数。
     *
     * @param data   字节数组
     * @param needle 标记
     * @param from   起始偏移（含）
     * @param to     结束偏移（不含）
     * @return 出现次数
     */
    private static int count(byte[] data, byte[] needle, int from, int to) {
        int hits = 0;
        int limit = Math.min(to, data.length);
        for (int i = Math.max(from, 0); i + needle.length <= limit; i++) {
            boolean hit = true;
            for (int j = 0; j < needle.length; j++) {
                if (data[i + j] != needle[j]) {
                    hit = false;
                    break;
                }
            }
            if (hit) {
                hits++;
                i += needle.length - 1;
            }
        }
        return hits;
    }

    /**
     * 摘要算法 OID → 可读名称。
     *
     * @param oid 摘要算法 OID
     * @return 可读名称（未知 OID 原样返回）
     */
    private static String digestName(String oid) {
        return switch (oid) {
            case "1.3.14.3.2.26" -> "SHA-1";
            case "2.16.840.1.101.3.4.2.1" -> "SHA-256";
            case "2.16.840.1.101.3.4.2.2" -> "SHA-384";
            case "2.16.840.1.101.3.4.2.3" -> "SHA-512";
            case "1.2.156.10197.1.401" -> "SM3";
            default -> oid;
        };
    }

    /**
     * 组合出签名算法名（如 {@code SHA256withRSA}）。
     *
     * <p><b>为什么要先查「带摘要的 RSA OID」：</b>BouncyCastle 在
     * {@code SignerInformation#getEncryptionAlgOID()} 里回的是
     * {@code sha256WithRSAEncryption(1.2.840.113549.1.1.11)} 这类<b>已含摘要</b>的 OID，
     * 而不是裸的 {@code rsaEncryption(1.2.840.113549.1.1.1)}。只判后者会让本方法对我们自己
     * 产出的签名永远落到兜底分支，把「签名算法」这个面向用户的字段填成原始 OID
     * （{@code SHA-256/1.2.840.113549.1.1.11}）。两种形态都要认。</p>
     *
     * @param signerInfo 签名者信息
     * @return 算法名（未知 OID 退化为「摘要/OID」）
     */
    private static String signatureAlgorithmName(SignerInformation signerInfo) {
        String encryptionOid = signerInfo.getEncryptionAlgOID();
        String byOid = switch (encryptionOid) {
            case "1.2.840.113549.1.1.5" -> "SHA1withRSA";
            case "1.2.840.113549.1.1.11" -> "SHA256withRSA";
            case "1.2.840.113549.1.1.12" -> "SHA384withRSA";
            case "1.2.840.113549.1.1.13" -> "SHA512withRSA";
            case "1.2.840.113549.1.1.10" -> "RSASSA-PSS";
            case "1.2.840.10045.4.3.2" -> "SHA256withECDSA";
            case "1.2.840.10045.4.3.3" -> "SHA384withECDSA";
            case "1.2.840.10045.4.3.4" -> "SHA512withECDSA";
            case "1.2.156.10197.1.501" -> "SM3withSM2";
            default -> null;
        };
        if (byOid != null) {
            return byOid;
        }
        String digest = digestName(signerInfo.getDigestAlgOID());
        if (OID_RSA_ENCRYPTION.equals(encryptionOid)) {
            // 通用 rsaEncryption：算法名只能由摘要算法拼出来
            return digest.replace("-", "") + "withRSA";
        }
        return digest + "/" + encryptionOid;
    }

    private static PdfVerifyOutcome finish(PdfVerifyOutcome out, DocVerifyVerdict verdict) {
        out.setVerdict(verdict);
        return out;
    }
}

package com.nexus.common.sign.support.pdf;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.DocVerifyVerdict;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.CMSProcessableByteArray;
import org.bouncycastle.cms.CMSSignedData;
import org.bouncycastle.cms.CMSSignerDigestMismatchException;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.List;

import static com.nexus.common.sign.support.pdf.PdfTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 文档级验签器测试 —— <b>P2 的核心风险点</b>。
 *
 * <p><b>本类要证明的核心命题：</b>「只做 CMS/PKCS#7 验签」会漏掉「签名后追加内容」这一类篡改，
 * 而「只做 ByteRange 覆盖检查」会漏掉「覆盖区间内等长改写」。因此验签器<b>两条检查缺一不可</b>。
 * 类中每一处 {@code assertCmsAloneWouldMiss...} 的断言都是在钉死这个盲区。</p>
 *
 * <p>签名与篡改全部在测试内现场生成（PDFBox 造 PDF、自签证书现场签发），无任何二进制夹具。</p>
 *
 * @author nexus
 */
class PdfDocumentVerifierTest {

    /** RSA-2048 现场生成签名身份（整个测试类共用一次，避免重复耗时的密钥生成）。 */
    private static SelfSignedCertificateFactory.SigningIdentity identity;
    /** 盖章 + 签名后的干净文件（后续全部篡改用例都从它派生）。 */
    private static byte[] cleanSigned;

    @BeforeAll
    static void signOnce() {
        identity = SelfSignedCertificateFactory.generate("张伟", "Nexus-ERP 电子签章", 0);
        cleanSigned = PdfCmsSigner.sign(
            PdfContentStamper.stamp(newPdf(PDRectangle.A4, 2),
                List.of(new PdfContentStamper.StampSpec(0, 0.1f, 0.6f, 0.25f, 0.15f, 0,
                    quadrantSealPng(200)))),
            identity.privateKey(), identity.certificate(), "张伟", "合同签署");
    }

    // ---------------------------------------------------------------- 干净文档

    @Test
    @DisplayName("验收标准：刚签完的文件必须验为 OK（CMS 通过 + 全覆盖）")
    void freshlySignedDocumentVerifiesClean() {
        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(cleanSigned);

        assertEquals(DocVerifyVerdict.OK, outcome.getVerdict(), "刚签署的文件必须判为 OK");
        assertTrue(outcome.isCmsPassed(), "CMS/PKCS#7 验签必须通过");
        assertTrue(outcome.isCoveragePassed(), "签名必须覆盖整个文件");
        assertFalse(outcome.isInPlaceModified(), "不应误报原地改写");
        assertFalse(outcome.isRevisionAppended(), "不应误报追加修订");
        assertEquals(0, outcome.getTrailingBytes(), "未覆盖字节必须为 0");
        assertEquals(cleanSigned.length, outcome.getCoveredEnd(), "覆盖末端应正好等于文件长度");
        assertEquals(1, outcome.getSignatureCount(), "应恰好检出 1 个签名域");
    }

    @Test
    @DisplayName("签名信息自描述：算法为 SHA-256 / SHA256withRSA，自签证书内嵌在 CMS 里")
    void signatureIsSelfContainedAndSelfDescribing() {
        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(cleanSigned);

        assertEquals("SHA-256", outcome.getDigestAlgorithm());
        assertEquals("SHA256withRSA", outcome.getSignatureAlgorithm());
        assertNotNull(outcome.getSignerSubject(), "应能读出签名者证书主题");
        assertTrue(outcome.getSignerSubject().contains("CN=张伟"),
            "证书主题应含签署人 CN（实际 " + outcome.getSignerSubject() + "）");
        assertEquals(outcome.getSignerSubject(), outcome.getSignerIssuer(), "自签证书的颁发者应等于主体");
        assertNotNull(outcome.getSignedAt(), "应能读出签名域的签署时间");
    }

    @Test
    @DisplayName("ByteRange 形如 [0, len1, off2, len2] 且第二段只跳过 /Contents 占位")
    void byteRangeShapeIsTheExcludedContentsHole() {
        int[] range = PdfDocumentVerifier.verify(cleanSigned).getByteRange();

        assertEquals(4, range.length);
        assertEquals(0, range[0], "首段必须从文件头开始");
        assertTrue(range[1] > 0);
        assertEquals(cleanSigned.length, range[2] + range[3], "两段拼接必须覆盖到文件末尾");
        assertTrue(range[2] > range[1], "第二段必须从 /Contents 之后开始");
        assertTrue(range[2] - range[1] > 1000, "被跳过的正是签名占位符（spike 实测约 9KB hex）");
    }

    // ---------------------------------------------------------------- 篡改一：原地等长改写

    @Test
    @DisplayName("篡改A（CMS 抓）：覆盖区间内等长改 1 字节 → MODIFIED_IN_PLACE，且覆盖检查对此无感")
    void inPlaceEqualLengthEditIsDetected() throws Exception {
        byte[] tampered = editCoveredByteInPlace(cleanSigned);

        assertEquals(cleanSigned.length, tampered.length, "等长改写不应改变文件长度");
        assertTrue(PdfContentStamper.isPdf(tampered), "改写后文件仍应能被识别为 PDF（否则测的不是本用例）");

        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(tampered);

        assertEquals(DocVerifyVerdict.MODIFIED_IN_PLACE, outcome.getVerdict(),
            "覆盖区间内被改写必须判为「文档已被修改」，说明 " + outcome.getMessages());
        assertTrue(outcome.isInPlaceModified(), "应置位「原地改写」标志");
        assertFalse(outcome.isCmsPassed(), "CMS 摘要必须对不上");
        // 关键反证：覆盖检查完全看不出这一条（长度没变、也没有未覆盖字节）
        assertTrue(outcome.isCoveragePassed(), "长度未变 → 覆盖检查单独看是「通过」的，故必须叠加 CMS 检查");
        assertEquals(0, outcome.getTrailingBytes());
        assertTrue(outcome.getMessages().stream().anyMatch(m -> m.contains("文档已被修改")),
            "明细里应明确给出「文档已被修改」，实际：" + outcome.getMessages());
    }

    @Test
    @DisplayName("篡改A 反证：独立的 BC 原生 CMS 验签确实抛/报失败（不是我们自己判错了）")
    void inPlaceEditAlsoFailsRawBouncyCastleVerification() throws Exception {
        byte[] tampered = editCoveredByteInPlace(cleanSigned);

        assertFalse(rawBouncyCastleCmsVerifies(tampered),
            "原生 BouncyCastle 验签也必须失败，否则说明是我们的判定逻辑有问题");
        assertTrue(rawBouncyCastleCmsVerifies(cleanSigned), "同一份未篡改文件的原生验签必须通过（对照组）");
    }

    // ---------------------------------------------------------------- 篡改二：签名后追加修订

    @Test
    @DisplayName("篡改B（只有覆盖检查能抓）：签名后追加真实增量修订 → REVISION_APPENDED，"
        + "且【CMS 验签仍然通过】")
    void postSignatureIncrementalRevisionIsDetected() throws Exception {
        byte[] revised = appendIncrementalRevision(cleanSigned);
        assertTrue(revised.length > cleanSigned.length, "追加修订后文件必然变长");

        // ① 先证明「只做 CMS 验签会漏」——这是本用例存在的全部理由
        assertTrue(rawBouncyCastleCmsVerifies(revised),
            "签名覆盖区间未被改动，原生 CMS 验签必然仍然通过 —— 这正是 CMS-only 的盲区");

        // ② 我们的验签器必须抓到它
        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(revised);

        assertEquals(DocVerifyVerdict.REVISION_APPENDED, outcome.getVerdict(),
            "签名后追加的修订必须被判为「文档已被修改」，说明 " + outcome.getMessages());
        assertTrue(outcome.isRevisionAppended(), "应置位「追加修订」标志");
        assertFalse(outcome.isCoveragePassed(), "覆盖检查必须报「未覆盖」");
        assertTrue(outcome.isCmsPassed(), "CMS 验签本身仍然通过 —— 故必须叠加覆盖检查才抓得到");
        assertEquals(cleanSigned.length, outcome.getCoveredEnd(), "覆盖末端应停在签名时的文件长度");
        assertEquals(revised.length - cleanSigned.length, outcome.getTrailingBytes());
        assertTrue(outcome.getEofCountInTail() > 0, "未覆盖区应出现新的 %%EOF（即真实修订）");
        assertTrue(outcome.getStartxrefCountInTail() > 0, "未覆盖区应出现新的 startxref（即真实修订）");
        assertTrue(outcome.getEofCount() > 1, "全文件 %%EOF 计数应从 1 增长");
    }

    @Test
    @DisplayName("篡改B 是真篡改：追加的修订确实改了页面（渲染结果与原文不同）")
    void appendedRevisionActuallyChangesTheRenderedPage() throws Exception {
        byte[] revised = appendIncrementalRevision(cleanSigned);

        // 夹具在页面上叠了一块 200×200 的红色实心矩形
        Color after = new Color(render(revised, 0, 72).getRGB(120,
            Math.round(PDRectangle.A4.getHeight()) - 120));
        assertTrue(after.getRed() > 150 && after.getGreen() < 100 && after.getBlue() < 100,
            "追加的修订应真的在页面上留下红色矩形（实际 " + after + "），否则本用例只是追加了无意义字节");
    }

    // ---------------------------------------------------------------- 篡改三：裸尾字节（低危）

    @Test
    @DisplayName("尾部裸字节：不构成修订 → UNSIGNED_TRAILING_BYTES，且与「追加修订」不同级别")
    void bareTrailingBytesAreReportedAsLowSeverity() {
        byte[] withJunk = concat(cleanSigned, "      ".getBytes(StandardCharsets.US_ASCII));

        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(withJunk);

        assertEquals(DocVerifyVerdict.UNSIGNED_TRAILING_BYTES, outcome.getVerdict(),
            "裸尾字节未形成修订，不应与原地改写同级，说明 " + outcome.getMessages());
        assertTrue(outcome.isCmsPassed(), "CMS 验签仍应通过");
        assertFalse(outcome.isRevisionAppended(), "不得误判为追加修订");
        assertEquals(6, outcome.getTrailingBytes());
        assertEquals(0, outcome.getEofCountInTail(), "裸字节里不应含 %%EOF");
        assertEquals(0, outcome.getStartxrefCountInTail(), "裸字节里不应含 startxref");
        // 与「追加修订」区分开：两者结论不同，且提示信息说明「阅读器通常会忽略」
        assertNotEquals(DocVerifyVerdict.REVISION_APPENDED, outcome.getVerdict());
        assertNotEquals(DocVerifyVerdict.MODIFIED_IN_PLACE, outcome.getVerdict());
        assertTrue(outcome.getMessages().stream().anyMatch(m -> m.contains("裸数据")),
            "应明确说明是未构成修订的裸数据，实际：" + outcome.getMessages());
    }

    // ---------------------------------------------------------------- 其余失效形态

    @Test
    @DisplayName("整体重写导致文件短于签名覆盖范围 → TRUNCATED（签名覆盖的内容已不存在）")
    void rewrittenShorterDocumentIsTruncated() throws Exception {
        byte[] rewritten = fullRewrite(cleanSigned);
        int coveredEnd = PdfDocumentVerifier.verify(cleanSigned).getCoveredEnd();
        assertTrue(rewritten.length < coveredEnd,
            "全量重写应产出更小的文件（" + rewritten.length + " vs 覆盖末端 " + coveredEnd + "）");

        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(rewritten);

        assertEquals(DocVerifyVerdict.TRUNCATED, outcome.getVerdict(),
            "文件短于覆盖范围说明签名内容已不存在，说明 " + outcome.getMessages());
    }

    @Test
    @DisplayName("没有签名的 PDF → NO_SIGNATURE（而不是抛异常）")
    void unsignedDocumentIsReportedAsNoSignature() {
        PdfVerifyOutcome outcome = PdfDocumentVerifier.verify(newPdf(PDRectangle.A4, 1));

        assertEquals(DocVerifyVerdict.NO_SIGNATURE, outcome.getVerdict());
        assertEquals(0, outcome.getSignatureCount());
        assertFalse(outcome.isCmsPassed());
    }

    @Test
    @DisplayName("输入不合法：null / 空 / 非 PDF → ServiceException")
    void invalidInputRejected() {
        assertThrows(ServiceException.class, () -> PdfDocumentVerifier.verify(null));
        assertThrows(ServiceException.class, () -> PdfDocumentVerifier.verify(new byte[0]));
        assertThrows(ServiceException.class,
            () -> PdfDocumentVerifier.verify("这不是 PDF".getBytes(StandardCharsets.UTF_8)));
    }

    // ---------------------------------------------------------------- 夹具

    /**
     * 在签名之后追加一个真实的 PDF 增量修订：在首页叠一块红色实心矩形。
     *
     * @param signed 干净签名文件
     * @return 追加修订后的文件
     */
    private static byte[] appendIncrementalRevision(byte[] signed) {
        try (PDDocument doc = Loader.loadPDF(signed)) {
            PDPage page = doc.getPage(0);
            try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.setNonStrokingColor(Color.RED);
                cs.addRect(20, 100, 200, 200);
                cs.fill();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.saveIncremental(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 全量重写（{@code save()} 而非 {@code saveIncremental}）：产出的文件通常显著变小，
     * 签名覆盖的原始字节已不复存在。
     *
     * @param signed 干净签名文件
     * @return 全量重写后的文件
     */
    private static byte[] fullRewrite(byte[] signed) {
        try (PDDocument doc = Loader.loadPDF(signed)) {
            PDPage page = doc.getPage(0);
            try (PDPageContentStream cs = new PDPageContentStream(
                doc, page, PDPageContentStream.AppendMode.APPEND, true)) {
                cs.setNonStrokingColor(Color.BLUE);
                cs.addRect(30, 30, 100, 100);
                cs.fill();
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * <b>独立于生产代码</b>的原生 BouncyCastle CMS 验签（spike 的最小 recipe 原样复刻）。
     *
     * <p>存在的意义：证明「生产验签器判 OK / 不 OK」是<b>密码学事实</b>，
     * 而不是我们自己代码的 bug。特别是篡改B 要证明「CMS 单独看会通过」。</p>
     *
     * @param pdf PDF 字节
     * @return CMS 验签是否通过
     */
    private static boolean rawBouncyCastleCmsVerifies(byte[] pdf) throws Exception {
        PdfCryptoProviders.ensureBouncyCastle();
        try (PDDocument doc = Loader.loadPDF(pdf)) {
            PDSignature signature = doc.getSignatureDictionaries().get(0);
            CMSSignedData cms = new CMSSignedData(
                new CMSProcessableByteArray(signature.getSignedContent(pdf)),
                signature.getContents(pdf));
            SignerInformation signerInfo = cms.getSignerInfos().getSigners().iterator().next();
            Collection<X509CertificateHolder> matches = cms.getCertificates().getMatches(signerInfo.getSID());
            assertFalse(matches.isEmpty(), "签名内嵌的证书应能被匹配到");
            return signerInfo.verify(new JcaSimpleSignerInfoVerifierBuilder().build(matches.iterator().next()));
        } catch (CMSSignerDigestMismatchException e) {
            // 覆盖区间内被改写时 BC 抛的就是它 —— 即「文档已被修改」的精确信号（spike 结论）
            return false;
        }
    }

    private static byte[] concat(byte[] base, byte[] tail) {
        byte[] out = new byte[base.length + tail.length];
        System.arraycopy(base, 0, out, 0, base.length);
        System.arraycopy(tail, 0, out, base.length, tail.length);
        return out;
    }
}

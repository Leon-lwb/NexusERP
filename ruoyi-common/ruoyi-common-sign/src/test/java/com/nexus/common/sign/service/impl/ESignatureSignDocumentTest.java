package com.nexus.common.sign.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.DocVerifyVerdict;
import com.nexus.common.sign.constants.SealStatus;
import com.nexus.common.sign.constants.SealType;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSeal;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.domain.EsSignature;
import com.nexus.common.sign.domain.dto.DocSignCommand;
import com.nexus.common.sign.domain.dto.SealPlacement;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.domain.vo.DocumentVerifyVo;
import com.nexus.common.sign.mapper.EsSealGrantMapper;
import com.nexus.common.sign.mapper.EsSealMapper;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.mapper.EsSignatureMapper;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.SignAuthVerifier;
import com.nexus.common.sign.support.SignatureChainCalculator;
import com.nexus.common.sign.support.pdf.PdfCmsSigner;
import com.nexus.common.sign.support.pdf.PdfContentStamper;
import com.nexus.common.sign.support.pdf.PdfDocumentVerifier;
import com.nexus.common.sign.support.pdf.PdfTestSupport;
import com.nexus.common.sign.support.pdf.SelfSignedCertificateFactory;
import com.nexus.common.sign.util.Sm3Utils;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.annotation.Transactional;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code signDocument} / {@code verifyDocument} 服务层测试（P2a-2）。
 *
 * <p><b>不碰真实存储</b>：{@code OssContentAccessor} 全部用 mock，签署后的字节由
 * ArgumentCaptor 从 {@code store(...)} 调用里抓回来做断言 —— 抓住的正是「写进对象存储的那一份」，
 * 因此「签完立即验签通过」这条验收标准是在真实产出物上验证的，不是内部中间态。</p>
 *
 * <p><b>为什么失败路径要断言「什么都没写」：</b>签章是一次不可逆的对外动作
 * （产出新文件、写台账、改文档状态）。任何一处校验失败都必须<b>在第一次写入之前</b>终止，
 * 否则会出现「拒绝了请求，却留下了半套数据」。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@Tag("local")
@Tag("dev")
class ESignatureSignDocumentTest {

    private static final Long ME = 7L;
    private static final String TENANT = "000000";
    private static final Long MY_DEPT = 100L;
    private static final String BIZ = SignBizType.APPROVAL;

    private static final Long DOC_ID = 88001L;
    private static final Long FILE_OSS_ID = 5001L;
    private static final Long SEAL_OSS_ID = 5002L;
    private static final Long SEAL_ID = 9L;
    private static final Long SEAL_ID_2 = 10L;
    private static final Long NEW_OSS_ID = 6001L;

    /** 现场用 PDFBox 生成的源 PDF 与印章图（无二进制夹具）。 */
    private static byte[] srcPdf;
    private static byte[] sealPng;
    /** 直接由 PDF 层产出的干净签名文件，供 verifyDocument 用例当「库里已存的那一份」。 */
    private static byte[] signedFixture;
    private static String signedFixtureHash;

    @Mock
    private EsSignatureMapper signatureMapper;
    @Mock
    private EsSealMapper sealMapper;
    @Mock
    private EsSealGrantMapper grantMapper;
    @Mock
    private SignAuthVerifier authVerifier;
    @Mock
    private LockTemplate lockTemplate;
    @Mock
    private EsSignDocumentMapper documentMapper;
    @Mock
    private ObjectProvider<OssContentAccessor> ossAccessorProvider;
    @Mock
    private OssContentAccessor ossContentAccessor;

    @Captor
    private ArgumentCaptor<EsSignature> sigCaptor;
    @Captor
    private ArgumentCaptor<EsSignDocument> docCaptor;
    @Captor
    private ArgumentCaptor<byte[]> storedBytesCaptor;
    @Captor
    private ArgumentCaptor<String> fileNameCaptor;
    @Captor
    private ArgumentCaptor<String> contentTypeCaptor;

    private ESignatureServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;
    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeAll
    static void buildFixtures() {
        srcPdf = PdfTestSupport.newPdf(PDRectangle.A4, 2);
        sealPng = PdfTestSupport.quadrantSealPng(200);
        SelfSignedCertificateFactory.SigningIdentity identity =
            SelfSignedCertificateFactory.generate("张伟", "Nexus-ERP 电子签章", 0);
        signedFixture = PdfCmsSigner.sign(
            PdfContentStamper.stamp(srcPdf, List.of(
                new PdfContentStamper.StampSpec(0, 0.1f, 0.6f, 0.25f, 0.15f, 0, sealPng))),
            identity.privateKey(), identity.certificate(), "张伟", "合同签署");
        signedFixtureHash = Sm3Utils.hashHexBytes(signedFixture);
    }

    @BeforeEach
    void setUp() {
        service = new ESignatureServiceImpl(signatureMapper, sealMapper, grantMapper, authVerifier,
            lockTemplate, documentMapper, ossAccessorProvider);

        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String c : new String[]{"SIGNATUREID", "TENANTID", "BIZTYPE", "BIZID", "BIZNO",
            "SIGNERUSERID", "SIGNERNAME", "SIGNERDEPTID", "SEALID", "SEALTYPE",
            "SIGNATUREMEANING", "AUTHMETHOD", "SRCDOCOSSID", "SRCDOCHASH", "DOCOSSID",
            "DOCHASH", "DOCID", "SIGNEDAT", "SIGNIP", "SIGNUA", "PREVHASH", "CHAINHASH", "REMARK"}) {
            cols.put(c, new ColumnCache(c.toLowerCase(), c.toLowerCase()));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSignature.class)).thenReturn(cols);

        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(ME);
        loginHelperMock.when(LoginHelper::getTenantId).thenReturn(TENANT);
        loginHelperMock.when(LoginHelper::getDeptId).thenReturn(MY_DEPT);

        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(newLock());
        when(signatureMapper.selectOne(any())).thenReturn(null);
        when(signatureMapper.insert(any(EsSignature.class))).thenReturn(1);
        when(documentMapper.updateById(any(EsSignDocument.class))).thenReturn(1);
        // 默认给一个「本人启用中的个人章」；越权/停用/不存在等用例在各自方法内覆盖此桩
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));

        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossContentAccessor);
        when(ossContentAccessor.read(FILE_OSS_ID)).thenReturn(srcPdf);
        when(ossContentAccessor.read(SEAL_OSS_ID)).thenReturn(sealPng);
        when(ossContentAccessor.store(any(byte[].class), anyString(), anyString())).thenReturn(NEW_OSS_ID);

        when(documentMapper.selectById(DOC_ID)).thenReturn(document());
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
        if (loginHelperMock != null) loginHelperMock.close();
    }

    // ---------------------------------------------------------------- 正常路径

    @Test
    @DisplayName("签章成功：写出新文件 + 台账登记四条文档字段 + 文档表回填 signed_oss_id/sign_status")
    void signDocument_writesFileAndLedger() {
        Long signatureId = service.signDocument(cmd(placement(0, SEAL_ID)));

        assertNotNull(signatureId, "应返回签名ID");
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();

        assertEquals(FILE_OSS_ID, saved.getSrcDocOssId(), "应登记签署前文件OSS ID");
        assertEquals(Sm3Utils.hashHexBytes(srcPdf), saved.getSrcDocHash(), "应登记签署前文件SM3");
        assertEquals(NEW_OSS_ID, saved.getDocOssId(), "应登记签署后文件OSS ID");
        assertEquals(DOC_ID, saved.getDocId(), "应关联 es_sign_document.doc_id");
        assertEquals(BIZ, saved.getBizType());
        assertEquals(100L, saved.getBizId());
        assertEquals(SEAL_ID, saved.getSealId());
        assertEquals("合同签署", saved.getSignatureMeaning());
        assertEquals(SignAuthMethod.LOGIN_ONLY, saved.getAuthMethod());

        // 写进对象存储的必须是「盖章 + 数字签名」之后的 PDF，且内容类型/文件名正确
        verify(ossContentAccessor).store(storedBytesCaptor.capture(), fileNameCaptor.capture(),
            contentTypeCaptor.capture());
        assertEquals("application/pdf", contentTypeCaptor.getValue());
        assertEquals("采购合同-signed.pdf", fileNameCaptor.getValue());
        byte[] stored = storedBytesCaptor.getValue();
        assertTrue(PdfContentStamper.isPdf(stored));
        assertNotEquals(Sm3Utils.hashHexBytes(srcPdf), Sm3Utils.hashHexBytes(stored), "签署后文件必然变化");
        assertEquals(Sm3Utils.hashHexBytes(stored), saved.getDocHash(), "台账 docHash 必须是签署后文件的 SM3");

        verify(documentMapper).updateById(docCaptor.capture());
        assertEquals(DOC_ID, docCaptor.getValue().getDocId());
        assertEquals(NEW_OSS_ID, docCaptor.getValue().getSignedOssId());
        assertEquals("2", docCaptor.getValue().getSignStatus(), "签署状态应置为已签(2)");
    }

    @Test
    @DisplayName("验收标准：写进对象存储的那一份，签完立即验签必须为 OK 且无未覆盖字节")
    void signDocument_outputVerifiesCleanImmediately() {
        service.signDocument(cmd(placement(0, SEAL_ID)));

        verify(ossContentAccessor).store(storedBytesCaptor.capture(), anyString(), anyString());
        byte[] stored = storedBytesCaptor.getValue();

        var outcome = PdfDocumentVerifier.verify(stored);

        assertEquals(DocVerifyVerdict.OK, outcome.getVerdict(),
            "刚签完的文件必须验为 OK，说明 " + outcome.getMessages());
        assertTrue(outcome.isCmsPassed(), "CMS/PKCS#7 验签必须通过");
        assertTrue(outcome.isCoveragePassed(), "签名必须覆盖整个文件");
        assertEquals(0, outcome.getTrailingBytes());
        assertEquals("SHA-256", outcome.getDigestAlgorithm());
        assertEquals("SHA256withRSA", outcome.getSignatureAlgorithm());
    }

    @Test
    @DisplayName("哈希链：模拟 datetime(0) 落库往返后仍可复现，且 docHash 确实编进了链")
    void signDocument_chainHashReproducibleAfterDbRoundTrip() {
        service.signDocument(cmd(placement(0, SEAL_ID)));
        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature written = sigCaptor.getValue();

        // P1 的线上缺陷：signed_at 是 datetime(0)，毫秒会被截断，链哈希若用毫秒就永远验不过。
        // 这里先钉死写入时就已按秒对齐，再模拟落库往返重算。
        assertEquals(0L, written.getSignedAt().getTime() % 1000L,
            "signed_at 必须在写入前就截断到秒，否则落库后无法复现链哈希（P1 回归点）");

        EsSignature readBack = readBackFromDbAsMysqlDatetime(written);
        String recomputed = SignatureChainCalculator.computeV2(
            readBack.getPrevHash(), readBack.getSignatureId(), readBack.getBizType(),
            readBack.getBizId(), readBack.getSignerUserId(), readBack.getSealId(),
            readBack.getSignatureMeaning(), readBack.getDocHash(), readBack.getSignedAt().getTime(),
            readBack.getAuthMethod(), readBack.getBizNo());
        assertEquals(readBack.getChainHash(), recomputed,
            "落库往返后重算的链哈希必须与库中值一致");

        // 反向证明「签署后文件摘要被编进了链」：换掉 docHash，链哈希必须随之改变
        assertNotNull(written.getDocHash(), "文档签章必须把签署后文件 SM3 写进 doc_hash");
        String tampered = SignatureChainCalculator.computeV2(
            written.getPrevHash(), written.getSignatureId(), written.getBizType(),
            written.getBizId(), written.getSignerUserId(), written.getSealId(),
            written.getSignatureMeaning(), "0".repeat(64), written.getSignedAt().getTime(),
            written.getAuthMethod(), written.getBizNo());
        assertNotEquals(written.getChainHash(), tampered,
            "doc_hash 若不参与链式计算，文件被换掉验链就发现不了");
    }

    @Test
    @DisplayName("链式追加：非链首时 prevHash 取自上一条的 chainHash")
    void signDocument_linksToPreviousRecord() {
        EsSignature prev = new EsSignature();
        prev.setSignatureId(1L);
        prev.setChainHash("b".repeat(64));
        when(signatureMapper.selectOne(any())).thenReturn(prev);

        service.signDocument(cmd(placement(0, SEAL_ID)));

        verify(signatureMapper).insert(sigCaptor.capture());
        assertEquals("b".repeat(64), sigCaptor.getValue().getPrevHash());
    }

    @Test
    @DisplayName("多章：每个不同印章都要单独过授权，且登记第一个落位所用印章")
    void signDocument_authorizesEachDistinctSeal() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        when(sealMapper.selectById(SEAL_ID_2)).thenReturn(myPersonalSeal(SEAL_ID_2));
        when(ossContentAccessor.read(SEAL_OSS_ID)).thenReturn(sealPng);

        service.signDocument(cmd(placement(0, SEAL_ID), placement(1, SEAL_ID_2)));

        verify(sealMapper).selectById(SEAL_ID);
        verify(sealMapper).selectById(SEAL_ID_2);
        verify(signatureMapper).insert(sigCaptor.capture());
        assertEquals(SEAL_ID, sigCaptor.getValue().getSealId(),
            "台账只有一列 seal_id，应登记第一个落位所用印章（落位顺序即业务主次）");
    }

    @Test
    @DisplayName("同页多处盖同一个章：授权只判定一次，但两处都要落图")
    void signDocument_sameSealTwiceIsAuthorizedOnce() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));

        service.signDocument(cmd(placement(0, SEAL_ID), placement(0, SEAL_ID)));

        // 按印章去重：同一个章不应重复查库判定授权
        verify(sealMapper, times(1)).selectById(SEAL_ID);
        verify(ossContentAccessor).store(storedBytesCaptor.capture(), anyString(), anyString());
        assertTrue(PdfContentStamper.isPdf(storedBytesCaptor.getValue()));
    }

    // ---------------------------------------------------------------- 拒绝：授权 / 认证

    @Test
    @DisplayName("用章授权：他人的个人章 → 拒绝，且对象存储/台账/文档表一个字都没写")
    void signDocument_unentitledSealRejected() {
        EsSeal others = myPersonalSeal(SEAL_ID);
        others.setOwnerUserId(999L);
        when(sealMapper.selectById(SEAL_ID)).thenReturn(others);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertTrue(e.getMessage().contains("无权使用印章"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("用章授权：与 sign 走同一段判定（同一印章的拒绝文案完全一致，规则不会漂移）")
    void signDocument_sharesSealAuthorizationWithSign() {
        EsSeal others = myPersonalSeal(SEAL_ID);
        others.setOwnerUserId(999L);
        when(sealMapper.selectById(SEAL_ID)).thenReturn(others);

        ServiceException fromSign = assertThrows(ServiceException.class,
            () -> service.sign(new SignCommand(BIZ, 100L, "AP-001", "审批通过", SEAL_ID,
                SignAuthMethod.LOGIN_ONLY, null)));
        ServiceException fromSignDocument = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertEquals(fromSign.getMessage(), fromSignDocument.getMessage(),
            "两个入口的用章授权必须来自同一段代码，否则规则会各自漂移");
    }

    @Test
    @DisplayName("用章授权：停用印章 → 拒绝且不写")
    void signDocument_disabledSealRejected() {
        EsSeal disabled = myPersonalSeal(SEAL_ID);
        disabled.setStatus(SealStatus.DISABLED);
        when(sealMapper.selectById(SEAL_ID)).thenReturn(disabled);

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("用章授权：印章不存在 → 拒绝且不写")
    void signDocument_sealNotFoundRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("二级认证失败 → 拒绝且不写（不允许降级为无凭证签章）")
    void signDocument_authFailureRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        doThrow(new ServiceException("签署密码错误"))
            .when(authVerifier).verify(anyLong(), anyString(), any());

        ServiceException e = assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, DOC_ID, List.of(placement(0, SEAL_ID)), "合同签署",
                SignAuthMethod.PASSWORD, "bad")));

        assertEquals("签署密码错误", e.getMessage());
        assertNothingWritten();
    }

    // ---------------------------------------------------------------- 拒绝：源文件

    @Test
    @DisplayName("非 PDF 必须由服务端拒绝（魔数不符），不能只靠前端/后缀名")
    void signDocument_nonPdfRejectedServerSide() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        byte[] notPdf = "这其实是一个 txt 文件".getBytes(StandardCharsets.UTF_8);
        when(ossContentAccessor.read(FILE_OSS_ID)).thenReturn(notPdf);
        // 摘要故意配置为「正确」——若拒绝来自摘要不符，本用例就失去了意义，
        // 因此这里必须证明拦住它的是魔数检查本身。
        EsSignDocument doc = document();
        doc.setFileHash(Sm3Utils.hashHexBytes(notPdf));
        when(documentMapper.selectById(DOC_ID)).thenReturn(doc);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertTrue(e.getMessage().contains("仅支持 PDF"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("源文件被换过（SM3 与上传时不符）→ 拒绝签署，绝不签一个不是它该签的东西")
    void signDocument_sourceHashMismatchRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        EsSignDocument doc = document();
        doc.setFileHash("0".repeat(64));
        when(documentMapper.selectById(DOC_ID)).thenReturn(doc);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertTrue(e.getMessage().contains("摘要不一致"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("源正文取不到（OSS 内容缺失）→ 拒绝且不写")
    void signDocument_sourceContentMissingRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        when(ossContentAccessor.read(FILE_OSS_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("印章原图取不到 → 拒绝且不写（不做「跳过该章」的降级）")
    void signDocument_sealImageMissingRejected() {
        EsSeal seal = myPersonalSeal(SEAL_ID);
        seal.setSealOssId(null);
        when(sealMapper.selectById(SEAL_ID)).thenReturn(seal);

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("印章原图不是图片 → 拒绝且不写（坏图不能产出坏 PDF）")
    void signDocument_brokenSealImageRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        when(ossContentAccessor.read(SEAL_OSS_ID)).thenReturn("不是图片".getBytes(StandardCharsets.UTF_8));

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("盖章页码越界 → 拒绝且不写")
    void signDocument_pageIndexOutOfRangeRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));

        assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(5, SEAL_ID))));
        assertNothingWritten();
    }

    // ---------------------------------------------------------------- 拒绝：入参 / 单据

    @Test
    @DisplayName("入参非法：空落位 / 缺印章ID / 比例越界 / 含义为空 / 认证方式为空 一律拒绝")
    void signDocument_invalidCommandsRejected() {
        assertThrows(ServiceException.class, () -> service.signDocument(null));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, DOC_ID, List.of(), "合同签署", SignAuthMethod.LOGIN_ONLY, null)));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, DOC_ID, null, "合同签署", SignAuthMethod.LOGIN_ONLY, null)));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(" ", 100L, DOC_ID, List.of(placement(0, SEAL_ID)), "合同签署",
                SignAuthMethod.LOGIN_ONLY, null)));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, DOC_ID, List.of(placement(0, SEAL_ID)), "  ",
                SignAuthMethod.LOGIN_ONLY, null)));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, DOC_ID, List.of(placement(0, SEAL_ID)), "合同签署", " ", null)));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, null, List.of(placement(0, SEAL_ID)), "合同签署",
                SignAuthMethod.LOGIN_ONLY, null)));
        // 比例越界 / 缺印章ID
        assertThrows(ServiceException.class, () -> service.signDocument(cmd(
            new SealPlacement(0, 0.9f, 0.1f, 0.5f, 0.1f, 0, SEAL_ID))));
        assertThrows(ServiceException.class, () -> service.signDocument(cmd(
            new SealPlacement(0, 0.1f, 0.1f, 0.1f, 0.1f, 0, null))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("签署含义超长（>128）→ 拒绝")
    void signDocument_meaningTooLongRejected() {
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 100L, DOC_ID, List.of(placement(0, SEAL_ID)), "长".repeat(129),
                SignAuthMethod.LOGIN_ONLY, null)));
    }

    @Test
    @DisplayName("待签文档不存在 → 拒绝")
    void signDocument_documentNotFoundRejected() {
        when(documentMapper.selectById(DOC_ID)).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));
        assertNothingWritten();
    }

    @Test
    @DisplayName("命令里的 bizType / bizId 与文档记录不一致 → 拒绝（不能把章盖到别的单据上）")
    void signDocument_bizMismatchRejected() {
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand("OTHER_BIZ", 100L, DOC_ID, List.of(placement(0, SEAL_ID)), "合同签署",
                SignAuthMethod.LOGIN_ONLY, null)));
        assertThrows(ServiceException.class, () -> service.signDocument(
            new DocSignCommand(BIZ, 999L, DOC_ID, List.of(placement(0, SEAL_ID)), "合同签署",
                SignAuthMethod.LOGIN_ONLY, null)));
        assertNothingWritten();
    }

    @Test
    @DisplayName("拿不到链锁 → 不写台账/不回填文档（对象存储里的孤儿对象是可接受代价）")
    void signDocument_lockUnavailableWritesNoLedger() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        verify(signatureMapper, never()).insert(any(EsSignature.class));
        verify(documentMapper, never()).updateById(any(EsSignDocument.class));
    }

    @Test
    @DisplayName("对象存储 SPI 缺失 → 明确业务异常，而不是 NPE")
    void signDocument_ossAccessorMissingRejected() {
        when(sealMapper.selectById(SEAL_ID)).thenReturn(myPersonalSeal(SEAL_ID));
        when(ossAccessorProvider.getIfAvailable()).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertTrue(e.getMessage().contains("对象存储内容访问实现缺失"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("事务：signDocument 必须声明 @Transactional，保证「签出的文件」与「台账行」同生共死")
    void signDocumentIsTransactional() throws Exception {
        Method method = ESignatureServiceImpl.class.getMethod("signDocument", DocSignCommand.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation, "signDocument 必须声明 @Transactional");
        assertTrue(annotation.rollbackFor().length > 0, "必须对所有异常回滚");
        assertTrue(List.of(annotation.rollbackFor()).contains(Exception.class),
            "回滚范围必须覆盖 Exception，否则会出现「有签名文件、没有台账」");
        assertTrue(Modifier.isPublic(method.getModifiers()));
    }

    // ---------------------------------------------------------------- verifyDocument

    @Test
    @DisplayName("verifyDocument：干净文件 → passed=true / OK，并回填台账摘要与实际摘要")
    void verifyDocument_cleanFilePasses() {
        when(signatureMapper.selectById(1L)).thenReturn(recordWith(9001L, signedFixtureHash));
        when(ossContentAccessor.read(9001L)).thenReturn(signedFixture);

        DocumentVerifyVo vo = service.verifyDocument(1L);

        assertEquals(Boolean.TRUE, vo.getPassed());
        assertEquals(DocVerifyVerdict.OK, vo.getVerdict());
        assertTrue(vo.isCmsPassed());
        assertTrue(vo.isCoveragePassed());
        assertEquals(Boolean.TRUE, vo.getHashPassed());
        assertEquals(signedFixtureHash, vo.getDocHash());
        assertEquals(signedFixtureHash, vo.getActualDocHash());
        assertEquals(DOC_ID, vo.getDocId());
        assertEquals(1L, vo.getSignatureId());
        assertEquals(0, vo.getTrailingBytes());
    }

    @Test
    @DisplayName("verifyDocument：覆盖区间内等长改写 → passed=false / MODIFIED_IN_PLACE + 明确文案")
    void verifyDocument_inPlaceTamperFails() {
        byte[] tampered = PdfTestSupport.editCoveredByteInPlace(signedFixture);
        assertEquals(signedFixture.length, tampered.length, "本用例要求等长改写");
        // 台账摘要按篡改后的文件登记，排除「台账摘要不符」这条旁证，单独检验验签器
        when(signatureMapper.selectById(2L)).thenReturn(recordWith(9002L, Sm3Utils.hashHexBytes(tampered)));
        when(ossContentAccessor.read(9002L)).thenReturn(tampered);

        DocumentVerifyVo vo = service.verifyDocument(2L);

        assertEquals(Boolean.FALSE, vo.getPassed());
        assertEquals(DocVerifyVerdict.MODIFIED_IN_PLACE, vo.getVerdict());
        assertTrue(vo.isInPlaceModified());
        assertFalse(vo.isCmsPassed());
        assertEquals(Boolean.TRUE, vo.getHashPassed(), "摘要与本条记录一致 → 说明抓它的是 CMS 检查，不是台账旁证");
        assertTrue(vo.getMessages().stream().anyMatch(m -> m.contains("文档已被修改")),
            "应给出可读的「文档已被修改」，实际：" + vo.getMessages());
    }

    @Test
    @DisplayName("verifyDocument：签名后追加增量修订 → passed=false / REVISION_APPENDED（CMS 单独看是通的）")
    void verifyDocument_postSignatureRevisionFails() {
        byte[] revised = appendRevision(signedFixture);
        when(signatureMapper.selectById(3L)).thenReturn(recordWith(9003L, Sm3Utils.hashHexBytes(revised)));
        when(ossContentAccessor.read(9003L)).thenReturn(revised);

        DocumentVerifyVo vo = service.verifyDocument(3L);

        assertEquals(Boolean.FALSE, vo.getPassed());
        assertEquals(DocVerifyVerdict.REVISION_APPENDED, vo.getVerdict());
        assertTrue(vo.isRevisionAppended());
        assertTrue(vo.isCmsPassed(), "CMS 验签仍然通过 —— 只做 CMS 验签根本发现不了这一类篡改");
        assertFalse(vo.isCoveragePassed());
        assertTrue(vo.getTrailingBytes() > 0);
        assertTrue(vo.getEofCountInTail() > 0);
    }

    @Test
    @DisplayName("verifyDocument：文件被换成另一份（台账 SM3 不符）→ passed=false，即使签名本身自洽")
    void verifyDocument_ledgerHashMismatchFails() {
        // 库里存着 A，实际读出来的是另一份同样「签名完整」的文件 B
        when(signatureMapper.selectById(4L)).thenReturn(recordWith(9004L, "f".repeat(64)));
        when(ossContentAccessor.read(9004L)).thenReturn(signedFixture);

        DocumentVerifyVo vo = service.verifyDocument(4L);

        assertEquals(Boolean.FALSE, vo.getPassed());
        assertEquals(Boolean.FALSE, vo.getHashPassed());
        assertEquals(DocVerifyVerdict.MODIFIED_IN_PLACE, vo.getVerdict(),
            "台账摘要对不上必须升格为「已被替换」，不能因为 CMS 自洽就放行");
        assertTrue(vo.getMessages().stream().anyMatch(m -> m.contains("台账")),
            "应点明台账登记值与实际不符，实际：" + vo.getMessages());
    }

    @Test
    @DisplayName("verifyDocument：记录不存在 / 不含签署后文档 / 内容缺失 一律明确报错")
    void verifyDocument_invalidTargetsRejected() {
        when(signatureMapper.selectById(5L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.verifyDocument(5L));

        // P1 的逻辑签名没有 doc_oss_id，不能拿它做文档级验签
        EsSignature noDoc = recordWith(null, null);
        when(signatureMapper.selectById(6L)).thenReturn(noDoc);
        assertThrows(ServiceException.class, () -> service.verifyDocument(6L));

        when(signatureMapper.selectById(7L)).thenReturn(recordWith(9007L, signedFixtureHash));
        when(ossContentAccessor.read(9007L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.verifyDocument(7L));
    }

    // ---------------------------------------------------------------- 夹具

    private DocSignCommand cmd(SealPlacement... placements) {
        return new DocSignCommand(BIZ, 100L, DOC_ID, List.of(placements), "合同签署",
            SignAuthMethod.LOGIN_ONLY, null);
    }

    private static SealPlacement placement(int pageIndex, Long sealId) {
        return new SealPlacement(pageIndex, 0.1f, 0.6f, 0.25f, 0.15f, 0, sealId);
    }

    private static EsSeal myPersonalSeal(Long sealId) {
        EsSeal seal = new EsSeal();
        seal.setSealId(sealId);
        seal.setSealType(SealType.PERSONAL);
        seal.setSealName("张伟的个人章");
        seal.setOwnerUserId(ME);
        seal.setSealOssId(SEAL_OSS_ID);
        seal.setStatus(SealStatus.ENABLED);
        return seal;
    }

    private static EsSignDocument document() {
        EsSignDocument doc = new EsSignDocument();
        doc.setDocId(DOC_ID);
        doc.setBizType(BIZ);
        doc.setBizId(100L);
        doc.setFileName("采购合同.pdf");
        doc.setFileOssId(FILE_OSS_ID);
        doc.setFileSize((long) srcPdf.length);
        doc.setFileHash(Sm3Utils.hashHexBytes(srcPdf));
        doc.setPageCount(2);
        doc.setSignStatus("0");
        doc.setIsCurrent("1");
        return doc;
    }

    private static EsSignature recordWith(Long docOssId, String docHash) {
        EsSignature record = new EsSignature();
        record.setSignatureId(1L);
        record.setDocId(DOC_ID);
        record.setDocOssId(docOssId);
        record.setDocHash(docHash);
        record.setSrcDocHash(Sm3Utils.hashHexBytes(srcPdf));
        record.setBizType(BIZ);
        record.setBizId(100L);
        return record;
    }

    private LockInfo newLock() {
        return new LockInfo("es:sign:chain:" + TENANT, "v", 30000L, 10000L, 1, null, null);
    }

    /**
     * 模拟 MySQL {@code datetime}(0) 的落库往返：小数秒被静默截断（P1 的真实缺陷）。
     *
     * @param d 写入前的 Java 时间
     * @return 数据库实际存下、并被读回的时间
     */
    private static Date readBackFromDbAsMysqlDatetime(Date d) {
        return new Date((d.getTime() / 1000L) * 1000L);
    }

    /**
     * 复刻「从库里读回的那一行」：字段逐一拷贝，{@code signed_at} 按 {@code datetime}(0) 截断。
     *
     * @param saved 写入时传给 insert 的实体
     * @return 数据库读回的等价实体
     */
    private static EsSignature readBackFromDbAsMysqlDatetime(EsSignature saved) {
        EsSignature db = new EsSignature();
        db.setSignatureId(saved.getSignatureId());
        db.setBizType(saved.getBizType());
        db.setBizId(saved.getBizId());
        db.setBizNo(saved.getBizNo());
        db.setAuthMethod(saved.getAuthMethod());
        db.setSignerUserId(saved.getSignerUserId());
        db.setSealId(saved.getSealId());
        db.setSignatureMeaning(saved.getSignatureMeaning());
        db.setSrcDocOssId(saved.getSrcDocOssId());
        db.setSrcDocHash(saved.getSrcDocHash());
        db.setDocOssId(saved.getDocOssId());
        db.setDocHash(saved.getDocHash());
        db.setDocId(saved.getDocId());
        db.setPrevHash(saved.getPrevHash());
        db.setChainHash(saved.getChainHash());
        db.setSignedAt(readBackFromDbAsMysqlDatetime(saved.getSignedAt()));
        return db;
    }

    /** 在签名之后追加一个真实增量修订（改页面）。 */
    private static byte[] appendRevision(byte[] signed) {
        try (PDDocument doc = Loader.loadPDF(signed)) {
            PDPage page = doc.getPage(0);
            try (PDPageContentStream cs =
                    new PDPageContentStream(
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

    /** 拒绝路径的统一断言：绝不能留下任何半套数据。 */
    private void assertNothingWritten() {
        verify(signatureMapper, never()).insert(any(EsSignature.class));
        verify(documentMapper, never()).updateById(any(EsSignDocument.class));
        verify(ossContentAccessor, never()).store(any(byte[].class), anyString(), anyString());
    }

    // ------------------------------------------- 多方签署基准 / 版本守卫（2026-09-18 安全审查修复）

    /** 既有「签署后文件」的 OSS ID（第二签署人的基准文件） */
    private static final Long SIGNED_OSS_ID = 6100L;

    @Test
    @DisplayName("追加签署：基准必须是当前签署后文件（保留先签者的章与CMS），而不是原始上传件")
    void signDocument_reSigningBasedOnSignedArtifact() {
        EsSignDocument doc = document();
        doc.setSignedOssId(SIGNED_OSS_ID);
        when(documentMapper.selectById(DOC_ID)).thenReturn(doc);
        when(ossContentAccessor.read(SIGNED_OSS_ID)).thenReturn(signedFixture);
        // 台账里「产生当前签署后文件的那条记录」——同时充当链尾（lenient mock 下同一个桩）
        EsSignature producer = recordWith(SIGNED_OSS_ID, signedFixtureHash);
        when(signatureMapper.selectOne(any())).thenReturn(producer);

        service.signDocument(cmd(placement(0, SEAL_ID)));

        verify(signatureMapper).insert(sigCaptor.capture());
        EsSignature saved = sigCaptor.getValue();
        assertEquals(SIGNED_OSS_ID, saved.getSrcDocOssId(), "追加签署的「签署前文件」应是上一签署产物");
        assertEquals(signedFixtureHash, saved.getSrcDocHash(), "签署前摘要应与台账登记的签署后文件一致");
        verify(ossContentAccessor, never()).read(FILE_OSS_ID);
    }

    @Test
    @DisplayName("追加签署：签署后文件被替换（与台账 doc_hash 不一致）-> 拒绝且什么都没写")
    void signDocument_reSigningArtifactReplacedRejected() {
        EsSignDocument doc = document();
        doc.setSignedOssId(SIGNED_OSS_ID);
        when(documentMapper.selectById(DOC_ID)).thenReturn(doc);
        when(ossContentAccessor.read(SIGNED_OSS_ID)).thenReturn(signedFixture);
        EsSignature producer = recordWith(SIGNED_OSS_ID, "0".repeat(64)); // 台账登记的是另一份文件
        when(signatureMapper.selectOne(any())).thenReturn(producer);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertTrue(e.getMessage().contains("摘要不一致"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("版本守卫：签署已被新版本取代的正文（is_current=0）-> 拒绝且什么都没写")
    void signDocument_nonCurrentVersionRejected() {
        EsSignDocument doc = document();
        doc.setIsCurrent("0");
        when(documentMapper.selectById(DOC_ID)).thenReturn(doc);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.signDocument(cmd(placement(0, SEAL_ID))));

        assertTrue(e.getMessage().contains("新版本取代"), "实际：" + e.getMessage());
        assertNothingWritten();
    }
}

package com.nexus.common.sign.service.impl;

import com.baomidou.lock.LockInfo;
import com.baomidou.lock.LockTemplate;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.EsSignDocument;
import com.nexus.common.sign.domain.vo.EsSignDocumentVo;
import com.nexus.common.sign.mapper.EsSignDocumentMapper;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.common.sign.support.pdf.PdfContentStamper;
import com.nexus.common.sign.support.pdf.PdfTestSupport;
import com.nexus.common.sign.util.Sm3Utils;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * {@code IESSignDocumentService} 实现的服务层测试（P2a-3）。
 *
 * <p><b>这些用例守的是什么：</b>P2a-2 的 {@code signDocument} 早已就绪，但 {@code es_sign_document}
 * 从没有过数据 —— 没有登记入口，签章能力就是不可达的。本类逐条钉住「登记」这一步的关键性质：
 * 服务端自己认字节（不信后缀/Content-Type）、大小与可解析性把关、摘要与页数算对、
 * 字节原样写进对象存储、换版时旧行 {@code is_current} 必须翻成 '0'。</p>
 *
 * <p><b>不碰真实存储与数据库</b>：{@code OssContentAccessor} 与 Mapper 全部 mock，
 * 写进对象存储的字节由 ArgumentCaptor 从 {@code store(...)} 调用里抓回来做断言 ——
 * 抓住的就是「真正落盘的那一份」。</p>
 *
 * <p><b>为什么拒绝路径要断言「什么都没写」：</b>上传是一次带副作用的动作
 * （写对象存储 + 写库）。任何校验失败都必须在第一次写入之前终止，
 * 否则会出现「请求被拒了，却留下半个对象/半行数据」。</p>
 *
 * @author nexus
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EsSignDocumentServiceImplTest {

    private static final String BIZ = SignBizType.SO_CONTRACT;
    private static final Long BIZ_ID = 100L;
    private static final String TENANT = "000000";
    private static final Long NEW_OSS_ID = 7001L;
    private static final Long PREV_DOC_ID = 88001L;
    /** 与实现内的上限一致：10MB */
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    /** 现场用 PDFBox 生成的正文（无二进制夹具）：2 页 / 3 页 */
    private static byte[] twoPagePdf;
    private static byte[] threePagePdf;

    @Mock private EsSignDocumentMapper baseMapper;
    @Mock private ObjectProvider<OssContentAccessor> ossAccessorProvider;
    @Mock private OssContentAccessor ossContentAccessor;
    @Mock private LockTemplate lockTemplate;

    @Captor private ArgumentCaptor<EsSignDocument> docCaptor;
    @Captor private ArgumentCaptor<byte[]> storedBytesCaptor;
    @Captor private ArgumentCaptor<String> fileNameCaptor;
    @Captor private ArgumentCaptor<String> contentTypeCaptor;
    @Captor private ArgumentCaptor<LambdaUpdateWrapper<EsSignDocument>> updateWrapperCaptor;

    private EsSignDocumentServiceImpl service;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;
    private MockedStatic<com.nexus.common.satoken.utils.LoginHelper> loginHelperMock;

    @BeforeAll
    static void buildFixtures() {
        twoPagePdf = PdfTestSupport.newPdf(PDRectangle.A4, 2);
        threePagePdf = PdfTestSupport.newPdf(PDRectangle.A4, 3);
    }

    @BeforeEach
    void setUp() {
        service = new EsSignDocumentServiceImpl(baseMapper, ossAccessorProvider, lockTemplate);

        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        // lambda wrapper 需要 MP 的列缓存（无 Spring/无 TableInfo 时自行预热，与 EsSealGrantServiceImplTest 同法）。
        // 列名必须用真实的库列名（snake_case）：本用例会读回 wrapper 生成的 SQL 片段做断言，
        // 用 c.toLowerCase() 那种占位写法会让断言连同 SQL 一起失真（现实测）。
        Map<String, ColumnCache> cols = new HashMap<>();
        for (String col : new String[]{"doc_id", "tenant_id", "biz_type", "biz_id", "file_oss_id",
            "file_name", "file_size", "file_hash", "page_count", "doc_version", "is_current",
            "signed_oss_id", "sign_status", "del_flag", "create_dept", "create_by", "create_time",
            "update_by", "update_time"}) {
            cols.put(col.replace("_", "").toUpperCase(Locale.ROOT), new ColumnCache(col, col));
        }
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(EsSignDocument.class)).thenReturn(cols);

        loginHelperMock = mockStatic(com.nexus.common.satoken.utils.LoginHelper.class);
        loginHelperMock.when(com.nexus.common.satoken.utils.LoginHelper::getTenantId).thenReturn(TENANT);

        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(newLock());
        when(ossAccessorProvider.getIfAvailable()).thenReturn(ossContentAccessor);
        when(ossContentAccessor.store(any(byte[].class), anyString(), anyString())).thenReturn(NEW_OSS_ID);
        when(baseMapper.insert(any(EsSignDocument.class))).thenReturn(1);
        when(baseMapper.selectOne(any())).thenReturn(null);
    }

    @AfterEach
    void tearDown() {
        if (lambdaUtilsMock != null) lambdaUtilsMock.close();
        if (loginHelperMock != null) loginHelperMock.close();
    }

    // ---------------------------------------------------------------- 正常路径

    @Test
    @DisplayName("首次上传：字节原样写给对象存储，摘要/大小/页数/版本/状态全部落对")
    void uploadDocument_firstVersion_writesBytesAndComputesMetadata() {
        Long docId = service.uploadDocument(BIZ, BIZ_ID, pdf("采购合同.pdf", twoPagePdf));

        assertNotNull(docId, "必须返回新文档ID（写入前已用雪花ID定好，落库后即为该行主键）");
        verify(ossContentAccessor).store(storedBytesCaptor.capture(), fileNameCaptor.capture(),
            contentTypeCaptor.capture());
        assertEquals("采购合同.pdf", fileNameCaptor.getValue(), "文件名应去掉目录前缀后原样登记");
        assertEquals("application/pdf", contentTypeCaptor.getValue(), "写对象存储必须声明 PDF 类型");
        assertArrayEquals(twoPagePdf, storedBytesCaptor.getValue(),
            "写进对象存储的必须是上传的原始字节，不能是任何加工后的内容");
        assertTrue(PdfContentStamper.isPdf(storedBytesCaptor.getValue()),
            "落盘的字节必须仍是一份 PDF（签章阶段会再验一次魔数）");

        verify(baseMapper).insert(docCaptor.capture());
        EsSignDocument saved = docCaptor.getValue();
        assertEquals(docId, saved.getDocId(), "返回值必须就是落库那一行的主键");
        assertEquals(BIZ, saved.getBizType());
        assertEquals(BIZ_ID, saved.getBizId());
        assertEquals(NEW_OSS_ID, saved.getFileOssId(), "file_oss_id 必须是对象存储返回的真实 ossId");
        assertEquals("采购合同.pdf", saved.getFileName());
        assertEquals((long) twoPagePdf.length, saved.getFileSize(), "file_size 必须是实际字节数");
        assertEquals(Sm3Utils.hashHexBytes(twoPagePdf), saved.getFileHash(),
            "file_hash 必须是该 PDF 的 SM3（签章时用它比对源正文有没有被换掉）");
        assertEquals(2, saved.getPageCount(), "page_count 必须由 PDFBox 解析得出");
        assertEquals(1, saved.getDocVersion(), "首次上传版本号为 1");
        assertEquals("1", saved.getIsCurrent(), "首次上传即当前生效版本");
        assertEquals("0", saved.getSignStatus(), "刚上传是未签状态");

        verify(baseMapper, never()).update(isNull(), any());
    }

    @Test
    @DisplayName("页数：3 页 PDF 登记 3，且与签章时的页码上限一致")
    void uploadDocument_pageCountMatchesDocument() {
        service.uploadDocument(BIZ, BIZ_ID, pdf("三页.pdf", threePagePdf));

        verify(baseMapper).insert(docCaptor.capture());
        assertEquals(3, docCaptor.getValue().getPageCount());
    }

    @Test
    @DisplayName("换版：新增行 doc_version=2 且 is_current='1'，旧版本行被条件更新为 is_current='0'")
    void uploadDocument_newVersion_flipsPreviousIsCurrent() {
        when(baseMapper.selectOne(any())).thenReturn(existingVersion(1));

        Long docId = service.uploadDocument(BIZ, BIZ_ID, pdf("采购合同.pdf", twoPagePdf));

        // 旧版本必须被置为「非当前」：否则同一业务键会有两行 is_current='1'，
        // 「当前生效版本」就再也无从判断（签章会签到一个已被替换的版本上）
        verify(baseMapper).update(isNull(), updateWrapperCaptor.capture());
        LambdaUpdateWrapper<EsSignDocument> luw = updateWrapperCaptor.getValue();
        String setSql = luw.getSqlSet() == null ? "" : luw.getSqlSet();
        assertTrue(setSql.contains("is_current"), "应显式 SET is_current，实际：" + setSql);
        assertTrue(luw.getSqlSegment().contains("biz_type") && luw.getSqlSegment().contains("biz_id"),
            "换版必须限定在同一业务键内，实际：" + luw.getSqlSegment());
        assertTrue(luw.getParamNameValuePairs().containsValue("0"),
            "旧版本 is_current 必须被置为 '0'，实际参数：" + luw.getParamNameValuePairs());
        assertTrue(luw.getParamNameValuePairs().containsValue(BIZ)
                && luw.getParamNameValuePairs().containsValue(BIZ_ID)
                && luw.getParamNameValuePairs().containsValue("1"),
            "条件应是 biz_type + biz_id + is_current='1'，实际参数：" + luw.getParamNameValuePairs());

        verify(baseMapper).insert(docCaptor.capture());
        EsSignDocument saved = docCaptor.getValue();
        assertEquals(2, saved.getDocVersion(), "换版应递增版本号");
        assertEquals("1", saved.getIsCurrent(), "新上传的那一版才是当前生效版本");
        assertNotEquals(PREV_DOC_ID, saved.getDocId(), "换版是新增行，不覆盖旧行");
        assertEquals(docId, saved.getDocId());
    }

    @Test
    @DisplayName("换版串行化：锁的 key 带租户与业务键，且不同单据/lock 被占用时不会写任何东西")
    void uploadDocument_serializesVersionBumpByBizKey() {
        when(baseMapper.selectOne(any())).thenReturn(existingVersion(1));

        service.uploadDocument(BIZ, BIZ_ID, pdf("采购合同.pdf", twoPagePdf));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(lockTemplate).lock(keyCaptor.capture(), anyLong(), anyLong());
        String key = keyCaptor.getValue();
        assertTrue(key.contains(TENANT), "锁 key 必须带租户，否则不同租户的同名业务键会互相阻塞：" + key);
        assertTrue(key.contains(BIZ) && key.contains(String.valueOf(BIZ_ID)),
            "锁 key 必须精确到业务键，否则同一业务的并发上传仍会撞版本：" + key);
    }

    @Test
    @DisplayName("换版锁必须活到事务提交之后：提交前就放锁，第二个上传者会读到同一个版本号并写出第二行 is_current='1'")
    void uploadDocument_lockReleasedOnlyAfterCommit() {
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.uploadDocument(BIZ, BIZ_ID, pdf("采购合同.pdf", twoPagePdf));

            verify(lockTemplate, never()).releaseLock(any());

            // 模拟事务提交：此刻临界区（读版本 → 写版本 → 提交）才真正结束
            TransactionSynchronizationManager.getSynchronizations()
                .forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            verify(lockTemplate).releaseLock(any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("无事务上下文（直连调用/单测）时锁在 finally 立即释放，不会泄漏")
    void uploadDocument_lockReleasedImmediatelyWithoutTransaction() {
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive(),
            "前置条件：本用例不在事务同步中");

        service.uploadDocument(BIZ, BIZ_ID, pdf("采购合同.pdf", twoPagePdf));

        verify(lockTemplate).releaseLock(any());
    }

    @Test
    @DisplayName("拿不到换版锁 → 拒绝，且对象存储/数据库一个字都没写")
    void uploadDocument_lockUnavailable_writesNothing() {
        when(lockTemplate.lock(anyString(), anyLong(), anyLong())).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, pdf("采购合同.pdf", twoPagePdf)));

        assertTrue(e.getMessage().contains("繁忙"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    // ---------------------------------------------------------------- 拒绝：字节级准入

    @Test
    @DisplayName("非 PDF 必须由服务端按魔数拒绝（后缀名与 Content-Type 都伪装成 PDF 也没用）")
    void uploadDocument_nonPdfRejectedByMagicBytes() {
        byte[] notPdf = "这其实是一个 txt 文件".getBytes(StandardCharsets.UTF_8);
        // 文件名 .pdf、Content-Type application/pdf 全部伪装到位 —— 拦住它的只能是魔数检查本身
        MultipartFile file = new MockMultipartFile("file", "合同.pdf", "application/pdf", notPdf);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, file));

        assertTrue(e.getMessage().contains("仅支持 PDF"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("超过大小上限 → 拒绝（在解析/落库之前），提示带实际上限")
    void uploadDocument_oversizeRejected() {
        byte[] tooBig = new byte[(int) MAX_FILE_SIZE + 1];

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, pdf("超大合同.pdf", tooBig)));

        assertTrue(e.getMessage().contains("不能超过"), "实际：" + e.getMessage());
        assertTrue(e.getMessage().contains("10"), "提示应给出实际上限(MB)，实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("文件名不是 .pdf / 声明了别的具体类型 / 空文件 → 一律拒绝")
    void uploadDocument_fileNameAndContentTypeChecks() {
        MultipartFile wrongExt = new MockMultipartFile(
            "file", "合同.docx", "application/pdf", twoPagePdf);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, wrongExt)).getMessage().contains("仅支持 PDF"));
        assertNothingWritten();

        MultipartFile wrongType = new MockMultipartFile(
            "file", "合同.pdf", "image/png", twoPagePdf);
        ServiceException typeEx = assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, wrongType));
        assertTrue(typeEx.getMessage().contains("必须是 PDF"), "实际：" + typeEx.getMessage());
        assertNothingWritten();

        MultipartFile empty = new MockMultipartFile("file", "空.pdf", "application/pdf", new byte[0]);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, empty)).getMessage().contains("不能为空"));
        assertNothingWritten();

        // 浏览器常见：只给通用二进制类型（不识别扩展名时），此时应以字节头为准放行
        MultipartFile octet = new MockMultipartFile(
            "file", "合同.pdf", "application/octet-stream", twoPagePdf);
        assertNotNull(service.uploadDocument(BIZ, BIZ_ID, octet),
            "octet-stream 是浏览器常见上报值，不应因此拒绝一份真的是 PDF 的文件");
    }

    @Test
    @DisplayName("魔数对但内容损坏/加密的 PDF → 拒绝，绝不登记 page_count 为空的正文")
    void uploadDocument_unparsablePdfRejected() {
        byte[] broken = "%PDF-1.7\n这不是一份能解析的 PDF".getBytes(StandardCharsets.UTF_8);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, pdf("坏文件.pdf", broken)));

        assertTrue(e.getMessage().contains("解析失败"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("业务键缺失（bizType 空 / bizId 空）→ 拒绝，不落任何数据")
    void uploadDocument_bizKeyRequired() {
        assertThrows(ServiceException.class,
            () -> service.uploadDocument("  ", BIZ_ID, pdf("合同.pdf", twoPagePdf)));
        assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, null, pdf("合同.pdf", twoPagePdf)));
        assertNothingWritten();
    }

    @Test
    @DisplayName("对象存储 SPI 缺失 → 明确业务异常而不是 NPE")
    void uploadDocument_ossAccessorMissingRejected() {
        when(ossAccessorProvider.getIfAvailable()).thenReturn(null);

        ServiceException e = assertThrows(ServiceException.class,
            () -> service.uploadDocument(BIZ, BIZ_ID, pdf("合同.pdf", twoPagePdf)));

        assertTrue(e.getMessage().contains("对象存储内容访问实现缺失"), "实际：" + e.getMessage());
        assertNothingWritten();
    }

    @Test
    @DisplayName("事务：uploadDocument 必须声明 @Transactional，保证「写进对象存储的正文」与「库里的行」同生共死")
    void uploadDocumentIsTransactional() throws Exception {
        Method method = EsSignDocumentServiceImpl.class
            .getMethod("uploadDocument", String.class, Long.class, MultipartFile.class);
        Transactional annotation = method.getAnnotation(Transactional.class);

        assertNotNull(annotation, "uploadDocument 必须声明 @Transactional");
        assertTrue(List.of(annotation.rollbackFor()).contains(Exception.class),
            "回滚范围必须覆盖 Exception，否则写库失败会留下「有正文文件、没有登记行」");
    }

    // ---------------------------------------------------------------- 查询

    @Test
    @DisplayName("列表：按业务键查询并按版本倒序，无数据时返回空列表")
    void listByBiz_queriesByBizKey() {
        EsSignDocumentVo vo = vo(1);
        when(baseMapper.selectVoList(any())).thenReturn(List.of(vo));

        LambdaQueryWrapper<EsSignDocument> lqw = captureQueryWrapper(() -> service.listByBiz(BIZ, null));
        assertTrue(lqw.getSqlSegment().contains("biz_type"),
            "必须按业务类型过滤，实际：" + lqw.getSqlSegment());
        assertTrue(lqw.getParamNameValuePairs().containsValue(BIZ));

        assertEquals(List.of(vo), service.listByBiz(BIZ, BIZ_ID));

        ServiceException e = assertThrows(ServiceException.class, () -> service.listByBiz(" ", BIZ_ID));
        assertTrue(e.getMessage().contains("业务类型不能为空"), "实际：" + e.getMessage());
    }

    @Test
    @DisplayName("当前版本：条件里必须带 is_current='1'，且取版本号最大的一行")
    void getCurrent_filtersByIsCurrent() {
        EsSignDocumentVo vo = vo(2);
        when(baseMapper.selectVoOne(any())).thenReturn(vo);

        assertEquals(vo, service.getCurrent(BIZ, BIZ_ID));

        ArgumentCaptor<Wrapper<EsSignDocument>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoOne(captor.capture());
        LambdaQueryWrapper<EsSignDocument> lqw = asLambdaQuery(captor.getValue());
        String segment = lqw.getSqlSegment();
        assertTrue(segment.contains("is_current"),
            "「当前生效版本」必须由 is_current='1' 判定，实际：" + segment);
        assertTrue(lqw.getParamNameValuePairs().containsValue("1"));
        assertTrue(lqw.getParamNameValuePairs().containsValue(BIZ_ID));

        assertThrows(ServiceException.class, () -> service.getCurrent(BIZ, null));
    }

    @Test
    @DisplayName("按ID查询：不存在（含已逻辑删除）→ 明确业务异常，不返回 null 让上层 NPE")
    void getByDocId_missingRejected() {
        EsSignDocumentVo vo = vo(1);
        when(baseMapper.selectVoById(PREV_DOC_ID)).thenReturn(vo);
        assertEquals(vo, service.getByDocId(PREV_DOC_ID));

        when(baseMapper.selectVoById(999L)).thenReturn(null);
        ServiceException e = assertThrows(ServiceException.class, () -> service.getByDocId(999L));
        assertTrue(e.getMessage().contains("待签文档不存在"), "实际：" + e.getMessage());
        assertThrows(ServiceException.class, () -> service.getByDocId(null));
    }

    // ---------------------------------------------------------------- 夹具

    private static MultipartFile pdf(String fileName, byte[] content) {
        return new MockMultipartFile("file", fileName, "application/pdf", content);
    }

    private static EsSignDocument existingVersion(int version) {
        EsSignDocument doc = new EsSignDocument();
        doc.setDocId(PREV_DOC_ID);
        doc.setBizType(BIZ);
        doc.setBizId(BIZ_ID);
        doc.setDocVersion(version);
        doc.setIsCurrent("1");
        return doc;
    }

    private static EsSignDocumentVo vo(int version) {
        EsSignDocumentVo vo = new EsSignDocumentVo();
        vo.setDocId(PREV_DOC_ID);
        vo.setBizType(BIZ);
        vo.setBizId(BIZ_ID);
        vo.setDocVersion(version);
        vo.setIsCurrent("1");
        return vo;
    }

    private LockInfo newLock() {
        return new LockInfo("es:sign:doc:" + TENANT, "v", 30000L, 10000L, 1, null, null);
    }

    /**
     * 执行一次查询并抓回它用到的条件。
     *
     * <p>用「先跑一遍、再抓 wrapper」而不是直接断言参数：wrapper 是在服务内部构造的，
     * 只有真正执行才存在（也正是要说清「服务到底按什么条件查的」）。</p>
     *
     * @param call 触发查询的调用
     * @return 该次查询的 wrapper
     */
    @SuppressWarnings("unchecked")
    private LambdaQueryWrapper<EsSignDocument> captureQueryWrapper(Runnable call) {
        call.run();
        ArgumentCaptor<Wrapper<EsSignDocument>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).selectVoList(captor.capture());
        return asLambdaQuery(captor.getValue());
    }

    /** 服务构造的一定是 lambda wrapper，取回具体类型才能读参数表（{@code Wrapper} 接口上没有）。 */
    @SuppressWarnings("unchecked")
    private static LambdaQueryWrapper<EsSignDocument> asLambdaQuery(Wrapper<EsSignDocument> wrapper) {
        return (LambdaQueryWrapper<EsSignDocument>) wrapper;
    }

    /** 拒绝路径的统一断言：绝不能留下任何半套数据。 */
    private void assertNothingWritten() {
        verify(baseMapper, never()).insert(any(EsSignDocument.class));
        // entity 恒为 null（换版只靠 wrapper 的 SET 子句），故匹配器也用 isNull()，
        // 不能用 any(Class) —— 那在 Mockito 2+ 下不匹配 null，断言会静默失效
        verify(baseMapper, never()).update(isNull(), any());
        verify(ossContentAccessor, never()).store(any(byte[].class), anyString(), anyString());
    }
}

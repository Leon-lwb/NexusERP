package com.nexus.mes.sop.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.common.sign.support.OssContentAccessor;
import com.nexus.mes.sop.domain.SopViewLog;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.mes.sop.domain.SopVersion;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopVersionMapper;
import com.nexus.mes.sop.mapper.SopViewLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SOP查阅记录 Service 单元测试 — P0-6 电子签名回填 (5 tests)
 * <p>查阅是「记录」而非授权: 签署失败必须降级(signature_id 留空), 查阅登记照常落库。</p>
 *
 * <p><b>降级的前提是签名走独立事务(REQUIRES_NEW)：</b>必须调用
 * {@code eSignatureService.signInNewTransaction(...)} 而非 {@code sign(...)}。
 * {@code sign} 的传播行为是 REQUIRED, 会加入 recordView 的事务; 它一旦抛 RuntimeException,
 * Spring 先把<b>共享</b>事务标记为 rollback-only, 本测试里的 try/catch 只吞得掉异常、
 * 吞不掉该标记 —— 线上表现为 insert 看似成功、提交时抛 UnexpectedRollbackException,
 * 查阅记录被一起回滚。</p>
 *
 * <p><b>本测试的边界：</b>纯 Mock 单元测试没有事务管理器, 上面的 rollback-only 污染在此
 * <b>无法复现</b>(所以它原先才会漏掉这个 bug)；这里只能断言"调用的是独立事务入口"。
 * 真实事务语义必须由集成测试覆盖: Task 17 的 E2E 需确认「签署失败时查阅记录仍然落库」。</p>
 */
@ExtendWith(MockitoExtension.class)
class SopViewLogServiceImplTest {

    @Mock private SopViewLogMapper baseMapper;
    @Mock private ESignatureService eSignatureService;
    @Mock private SopDocumentMapper sopDocumentMapper;
    @Mock private SopVersionMapper sopVersionMapper;
    @Mock private ObjectProvider<OssContentAccessor> ossAccessorProvider;
    @InjectMocks private SopViewLogServiceImpl service;

    @Captor private ArgumentCaptor<SopViewLog> logCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;

    /**
     * 真实列缓存: LambdaQueryWrapper/LambdaUpdateWrapper 需要实体 TableInfo,
     * 纯 Mock 单测下缺失会抛 "can not find lambda cache for this entity"。
     */
    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, SopViewLog.class);
        TableInfoHelper.initTableInfo(assistant, SopVersion.class);
        TableInfoHelper.initTableInfo(assistant, SopDocument.class);
    }

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(66L);
        // 服务端准入：sopId 必须指向真实存在的 SOP（否则可直接调接口对不存在的 SOP 无限刷签名台账）
        // lenient: 删除/已阅判定类用例不走 recordView, 该桩用不到
        lenient().when(sopDocumentMapper.selectById(any())).thenReturn(new SopDocument());
    }

    @AfterEach
    void tearDown() {
        if (loginHelperMock != null) loginHelperMock.close();
    }

    @Test
    void recordView_signSucceeds_setsSignatureIdOnLogRow() {
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(7788L);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        assertTrue(service.recordView(2L, "V2", 10L, "WO-1", 20L, "工序A", 30L));

        verify(baseMapper).insert(logCaptor.capture());
        SopViewLog row = logCaptor.getValue();
        assertEquals(7788L, row.getSignatureId(), "应回填签名ID");
        assertEquals(66L, row.getViewerId());
        assertEquals(2L, row.getSopId());

        // 查阅用 LOGIN_ONLY(不触发二级认证) 且不盖章
        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        SignCommand cmd = cmdCaptor.getValue();
        assertEquals(SignBizType.SOP_VIEW, cmd.bizType());
        assertEquals(2L, cmd.bizId());
        assertEquals(SignAuthMethod.LOGIN_ONLY, cmd.authMethod());
        assertNull(cmd.sealId(), "SOP 查阅不盖章");
        assertNull(cmd.credential());
        assertTrue(cmd.signatureMeaning().startsWith("SOP 已阅确认"));
    }

    /**
     * 回归守卫: 查阅签署必须走独立事务入口。
     * <p>若有人把调用点改回 {@code sign(...)}, 后者的 REQUIRED 会加入 recordView 事务,
     * 签署失败时把共享事务标记为 rollback-only —— 本测试的 mock 无法复现该污染,
     * 但能钉死"调用的是独立事务入口"这一前提。</p>
     */
    @Test
    void recordView_usesIndependentTransactionSignature_neverJoinsCallerTransaction() {
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(1L);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        service.recordView(5L, "V5", null, null, null, null, null);

        verify(eSignatureService).signInNewTransaction(any(SignCommand.class));
        verify(eSignatureService, never()).sign(any(SignCommand.class));
    }

    @Test
    void recordView_signFails_logStillWritten_signatureIdNull() {
        when(eSignatureService.signInNewTransaction(any(SignCommand.class)))
            .thenThrow(new ServiceException("签名通道繁忙，请稍后重试"));
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        // best-effort: 签署失败不得外抛
        assertDoesNotThrow(() -> service.recordView(1L, "V1", null, null, null, null, null));

        verify(baseMapper).insert(logCaptor.capture());
        assertNull(logCaptor.getValue().getSignatureId(), "签署失败留空, 不阻断查阅登记");
        assertEquals(1L, logCaptor.getValue().getSopId());
    }

    @Test
    void recordView_signReturnsNull_signatureIdNull() {
        // 签名服务未返回ID(非异常路径)时同样留空
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(null);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        service.recordView(3L, "V3", null, null, null, null, null);

        verify(baseMapper).insert(logCaptor.capture());
        assertNull(logCaptor.getValue().getSignatureId());
    }

    @Test
    void recordView_signMeaningSkipsBlankVersionNo() {
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(9L);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        service.recordView(4L, "  ", null, null, null, null, null);

        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        assertEquals("SOP 已阅确认", cmdCaptor.getValue().signatureMeaning(), "空版本号不拼进签署含义");
    }

    // ==================== MES-SOP-05: 签名绑定正文哈希 ====================

    @Test
    void recordView_bindsDocHashFromVersionFileHash() {
        SopVersion version = new SopVersion();
        version.setVersionId(11L);
        version.setSopId(2L);
        version.setVersionNo("V2");
        version.setFileOssId("12345");
        version.setFileHash("SM3-ABCDEF");
        when(sopVersionMapper.selectOne(any())).thenReturn(version);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(999L);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        service.recordView(2L, "V2", null, null, null, null, null);

        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        SignCommand cmd = cmdCaptor.getValue();
        assertEquals("SM3-ABCDEF", cmd.docHash(),
            "SOP_VIEW 逻辑签名必须绑定正文 SM3（修复前 doc_hash 恒为 null）");
        assertEquals(12345L, cmd.docOssId());
    }

    @Test
    void recordView_computesDocHashFromOssWhenVersionHashBlank() {
        SopVersion version = new SopVersion();
        version.setVersionId(12L);
        version.setSopId(2L);
        version.setVersionNo("V2");
        version.setFileOssId("12345");
        version.setFileHash(null);
        when(sopVersionMapper.selectOne(any())).thenReturn(version);
        OssContentAccessor accessor = mock(OssContentAccessor.class);
        when(accessor.read(12345L)).thenReturn("SOP-BODY".getBytes());
        when(ossAccessorProvider.getIfAvailable()).thenReturn(accessor);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(1000L);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        service.recordView(2L, "V2", null, null, null, null, null);

        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        String expected = com.nexus.common.sign.util.Sm3Utils.hashHexBytes("SOP-BODY".getBytes());
        assertEquals(expected, cmdCaptor.getValue().docHash(), "哈希为空时须按正文现算 SM3");
        // 现算结果回写 sop_version.file_hash 作为缓存
        verify(sopVersionMapper).update(any(), any());
    }

    @Test
    void recordView_degradesWithoutDocHashButStillWritesLog() {
        // 版本行查不到 -> 不绑定正文哈希, 但查阅登记照常落库(可观测, 不静默失败)
        when(sopVersionMapper.selectOne(any())).thenReturn(null);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(1001L);
        when(baseMapper.insert(any(SopViewLog.class))).thenReturn(1);

        assertTrue(service.recordView(2L, "V9", null, null, null, null, null));

        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        assertNull(cmdCaptor.getValue().docHash());
    }

    // ==================== MES-SOP-03: 已签名记录不可删 ====================

    @Test
    void deleteByIds_rejectsSignedRecord() {
        SopViewLog signed = new SopViewLog();
        signed.setLogId(1L);
        signed.setSignatureId(7788L);
        when(baseMapper.selectList(any())).thenReturn(java.util.List.of(signed));

        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{1L}));
        verify(baseMapper, never()).deleteByIds(any());
    }

    @Test
    void deleteByIds_allowsUnsignedRecord() {
        SopViewLog unsigned = new SopViewLog();
        unsigned.setLogId(2L);
        when(baseMapper.selectList(any())).thenReturn(java.util.List.of(unsigned));
        when(baseMapper.deleteByIds(any())).thenReturn(1);

        assertTrue(service.deleteByIds(new Long[]{2L}));
        verify(baseMapper).deleteByIds(any());
    }

    @Test
    void deleteByIds_returnsFalseWhenNothingFound() {
        when(baseMapper.selectList(any())).thenReturn(java.util.List.of());

        assertFalse(service.deleteByIds(new Long[]{3L}));
        verify(baseMapper, never()).deleteByIds(any());
    }

    // ==================== MES-SOP-02: 已阅确认判定 ====================

    @Test
    void hasAcknowledged_requiresSignatureId() {
        when(baseMapper.selectCount(any())).thenReturn(1L);

        assertTrue(service.hasAcknowledged(2L, "1.0", 5L));
    }

    @Test
    void hasAcknowledged_falseWhenNoSignedRow() {
        when(baseMapper.selectCount(any())).thenReturn(0L);

        assertFalse(service.hasAcknowledged(2L, "1.0", 5L));
        assertFalse(service.hasAcknowledged(null, "1.0", 5L));
        assertFalse(service.hasAcknowledged(2L, "1.0", null));
    }
}

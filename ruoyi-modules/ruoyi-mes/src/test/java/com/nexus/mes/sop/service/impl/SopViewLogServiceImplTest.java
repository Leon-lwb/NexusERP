package com.nexus.mes.sop.service.impl;

import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.mes.sop.domain.SopViewLog;
import com.nexus.mes.sop.domain.SopDocument;
import com.nexus.mes.sop.mapper.SopDocumentMapper;
import com.nexus.mes.sop.mapper.SopViewLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

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
    @InjectMocks private SopViewLogServiceImpl service;

    @Captor private ArgumentCaptor<SopViewLog> logCaptor;

    private MockedStatic<LoginHelper> loginHelperMock;

    @BeforeEach
    void setUp() {
        loginHelperMock = mockStatic(LoginHelper.class);
        loginHelperMock.when(LoginHelper::getUserId).thenReturn(66L);
        // 服务端准入：sopId 必须指向真实存在的 SOP（否则可直接调接口对不存在的 SOP 无限刷签名台账）
        when(sopDocumentMapper.selectById(any())).thenReturn(new SopDocument());
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
}

package com.nexus.mes.ncr.service.impl;

import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.domain.vo.NcrOrderVo;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.service.INcrDefectService;
import com.nexus.mes.ncr.service.INcrReworkOrderService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.system.strategy.AutoCodeUtil;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * NCR不合格品单 Service 单元测试 (23 tests)
 * <p>mockStatic(LambdaUtils,CALLS_REAL_METHODS) 解决 lambda 缓存;
 * autoCreate 传空 defectInfo 绕过 JsonUtils(需Spring上下文,回归由 E2E 覆盖)。
 * P0-6 处置签署(signDisposition + disposeWithSignature)的用例见文末。</p>
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(org.junit.jupiter.api.MethodOrderer.MethodName.class)
class NcrOrderServiceImplTest {

    @Mock private NcrOrderMapper baseMapper;
    @Mock private NcrDefectMapper defectMapper;
    @Mock private AutoCodeUtil autoCodeUtil;
    @Mock private ILtLotService ltLotService;
    @Mock private IProAndonService proAndonService;
    @Mock private INcrReworkOrderService ncrReworkOrderService;
    @Mock private INcrDefectService defectService;
    @Mock private ESignatureService eSignatureService;
    @InjectMocks private NcrOrderServiceImpl service;

    @Captor private ArgumentCaptor<NcrOrder> orderCaptor;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;

    @BeforeEach
    void setUp() {
        lambdaUtilsMock = mockStatic(LambdaUtils.class, CALLS_REAL_METHODS);
        Map<String, ColumnCache> cols = new HashMap<>();
        cols.put("NCRID", col("ncr_id")); cols.put("NCRCODE", col("ncr_code"));
        cols.put("SOURCETYPE", col("source_type")); cols.put("SOURCEID", col("source_id"));
        cols.put("WOID", col("wo_id")); cols.put("STATUS", col("status"));
        cols.put("DEFECTQTY", col("defect_qty")); cols.put("DISPOSITION", col("disposition"));
        cols.put("REVIEWBY", col("review_by")); cols.put("REVIEWTIME", col("review_time"));
        cols.put("DISPOSITIONBY", col("disposition_by")); cols.put("DISPOSITIONTIME", col("disposition_time"));
        cols.put("CLOSEDTIME", col("closed_time")); cols.put("CREATEDTIME", col("created_time"));
        cols.put("REMARK", col("remark")); cols.put("LOTID", col("lot_id"));
        cols.put("DISPOSITIONSIGNATUREID", col("disposition_signature_id"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(NcrOrder.class)).thenReturn(cols);
    }
    private ColumnCache col(String c) { return new ColumnCache(c, c); }
    @AfterEach void tearDown() { if (lambdaUtilsMock != null) lambdaUtilsMock.close(); }

    private void stubCreate(Long ncrId, String code) {
        when(baseMapper.selectVoOne(any())).thenReturn(null);
        when(autoCodeUtil.genSerialCode("MES_NCR_CODE", null)).thenReturn(code);
        when(baseMapper.insert(any(NcrOrder.class))).thenAnswer(inv -> { ((NcrOrder) inv.getArgument(0)).setNcrId(ncrId); return 1; });
    }

    // ==================== Bug1: defectQty null -> ZERO ====================

    @Test
    void autoCreateFromIqc_defectQtyNull_defaultsZero() {
        stubCreate(100L, "NCR-001");
        // 传空 defectInfo 绕过 parseAndInsertDefects(JsonUtils 需 Spring, E2E 已覆盖解析)
        service.autoCreateFromIqc(1L, 9001L, 1001L, "");

        verify(baseMapper).insert(orderCaptor.capture());
        assertEquals(BigDecimal.ZERO, orderCaptor.getValue().getDefectQty(), "Bug1: defectQty null 应默认 0");
        assertEquals("IQC", orderCaptor.getValue().getSourceType());
        assertEquals("OPEN", orderCaptor.getValue().getStatus());
    }

    // ==================== Bug3: defectInfo 解析 (E2E 已覆盖, 此处仅标记) ====================

    @Test
    @Disabled("JsonUtils 需 Spring 上下文; defectInfo 解析+defectCodeId null 回归由 E2E 覆盖(28/28)")
    void autoCreateFromIqc_parsesDefectInfo_defectCodeIdNull() {
        // E2E verified: IQC inspect UNQUALIFIED + defectInfo JSON -> autoCreate NCR -> ncr_defect(defectCode set, defectCodeId null)
    }

    @Test
    @Disabled("JsonUtils 需 Spring 上下文; 畸形 JSON 安全回归由 E2E 覆盖")
    void autoCreateFromIqc_defectInfoParseFail_safe() {
        // E2E verified: bad JSON doesn't crash; NCR still created, no defects
    }

    // ==================== 幂等 ====================

    @Test
    void autoCreateFromIqc_idempotent_returnsExisting() {
        NcrOrderVo existing = new NcrOrderVo();
        existing.setNcrId(999L);
        when(baseMapper.selectVoOne(any())).thenReturn(existing);

        service.autoCreateFromIqc(5L, 9005L, 1001L, "");  // idempotent: 幂等守卫在 insert 前, 不触发 JsonUtils

        verify(baseMapper, never()).insert(any(NcrOrder.class));
    }

    // ==================== 状态机 ====================

    @Test
    void review_openToReview_casSuccess() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("OPEN");
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.review(1L));
    }

    @Test
    void review_failsWhenCasRowZero() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("CLOSED");  // 非 OPEN, CAS 不匹配
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(any(), any())).thenReturn(0);
        assertTrue(assertThrows(RuntimeException.class, () -> service.review(1L))
            .getMessage().contains("状态已变更"));
    }

    @Test
    void dispose_rework_callsDeriveAndCasInRework() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("OPEN");
        o.setWoId(10L); o.setDefectQty(new BigDecimal("5"));
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.dispose(1L, "REWORK", "返工"));
        verify(ncrReworkOrderService).deriveReworkOrder(1L, 10L, new BigDecimal("5"), "返工");
    }

    @Test
    void dispose_scrap_casClosed() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("OPEN");
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.dispose(1L, "SCRAP", "报废"));
    }

    @Test
    void dispose_accept_releasesLot() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("REVIEW"); o.setLotId(20L);
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.dispose(1L, "ACCEPT_AS_IS", "让步"));
        verify(ltLotService).release(20L);
    }

    @Test
    void reworkCompleted_inReworkToClosed() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("IN_REWORK");
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(any(), any())).thenReturn(1);
        assertTrue(service.reworkCompleted(1L));
    }

    @Test
    void review_throwsWhenOrderNotFound() {
        when(baseMapper.selectById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.review(99L));
    }

    @Test
    void dispose_throwsWhenOrderNotFound() {
        when(baseMapper.selectById(99L)).thenReturn(null);
        assertThrows(RuntimeException.class, () -> service.dispose(99L, "SCRAP", ""));
    }

    // ==================== 删除守卫 ====================

    @Test
    void deleteByIds_rejectsNonClosed() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setNcrCode("NCR001"); o.setStatus("OPEN");
        when(baseMapper.selectById(1L)).thenReturn(o);
        assertTrue(assertThrows(RuntimeException.class, () -> service.deleteByIds(new Long[]{1L}))
            .getMessage().contains("未关闭"));
    }

    @Test
    void deleteByIds_allowsClosed() {
        NcrOrder o = new NcrOrder(); o.setNcrId(1L); o.setStatus("CLOSED");
        when(baseMapper.selectById(1L)).thenReturn(o);
        assertDoesNotThrow(() -> service.deleteByIds(new Long[]{1L}));
    }

    // ==================== P0-6 处置签署 ====================

    @Test
    void signDisposition_rejectsLoginOnly_beforeTouchingMapper() {
        // 处置是授权: LOGIN_ONLY 必须拒绝, 且不得读库/签署
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.signDisposition(1L, "SCRAP", SignAuthMethod.LOGIN_ONLY, "cred"));
        assertEquals("NCR 处置签署必须进行二级认证", ex.getMessage());
        verifyNoInteractions(baseMapper);
        verifyNoInteractions(eSignatureService);
    }

    @Test
    void signDisposition_throwsWhenOrderNotFound_neverSigns() {
        when(baseMapper.selectById(99L)).thenReturn(null);
        assertTrue(assertThrows(ServiceException.class,
            () -> service.signDisposition(99L, "SCRAP", SignAuthMethod.PASSWORD, "cred"))
            .getMessage().contains("NCR 单不存在"));
        verifyNoInteractions(eSignatureService);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void signDisposition_signsThenBindsReturnedId() {
        NcrOrder o = new NcrOrder(); o.setNcrId(7L); o.setNcrCode("NCR-007");
        when(baseMapper.selectById(7L)).thenReturn(o);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(12345L);

        Long signatureId = service.signDisposition(7L, "SCRAP", SignAuthMethod.PASSWORD, "cred");

        assertEquals(12345L, signatureId);
        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        // 签名库只在 master(ry-vue): 走 REQUIRED 的 sign 会在「调用方已开着 MES 事务」时
        // 加入该事务而写到 ry-mes, 故必须走独立事务入口 —— 这条断言锁死该数据源边界。
        verify(eSignatureService, never()).sign(any(SignCommand.class));
        SignCommand cmd = cmdCaptor.getValue();
        assertEquals(SignBizType.NCR_DISPOSITION, cmd.bizType());
        assertEquals(7L, cmd.bizId());
        assertEquals("NCR-007", cmd.bizNo(), "bizNo 取实体 getNcrCode()");
        assertEquals("不合格品处置批准：SCRAP", cmd.signatureMeaning());
        assertNull(cmd.sealId(), "处置签名不盖章");
        assertEquals(SignAuthMethod.PASSWORD, cmd.authMethod());
        assertEquals("cred", cmd.credential());
        // 绑定的是签署返回的ID(而非传入值)
        ArgumentCaptor<Wrapper> wrapperCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), wrapperCaptor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) wrapperCaptor.getValue();
        assertTrue(String.valueOf(wrapper.getSqlSet()).contains("disposition_signature_id"));
        assertTrue(wrapper.getParamNameValuePairs().containsValue(12345L), "应将签名ID写入 update set");
    }

    @Test
    void signDisposition_nullDisposition_meaningHasNoNullLiteral() {
        NcrOrder o = new NcrOrder(); o.setNcrId(8L); o.setNcrCode("NCR-008");
        when(baseMapper.selectById(8L)).thenReturn(o);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(1L);

        service.signDisposition(8L, null, SignAuthMethod.PASSWORD, "cred");

        ArgumentCaptor<SignCommand> cmdCaptor = ArgumentCaptor.forClass(SignCommand.class);
        verify(eSignatureService).signInNewTransaction(cmdCaptor.capture());
        String meaning = cmdCaptor.getValue().signatureMeaning();
        assertFalse(meaning.contains("null"), "处置结论为 null 时不得拼出字面量 null");
        assertEquals("不合格品处置批准", meaning);
    }

    // ==================== P0-6 处置签署: REST 端点接入的合并入口 ====================

    @Test
    void disposeWithSignature_loginOnly_rejectedBeforeAnyWrite() {
        // 未被授权时业务库一行不得动: 拒绝发生在处置(状态 CAS/分支副作用)之前
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.disposeWithSignature(1L, "SCRAP", "备注", SignAuthMethod.LOGIN_ONLY, "cred"));
        assertEquals("NCR 处置签署必须进行二级认证", ex.getMessage());
        verifyNoInteractions(baseMapper);
        verifyNoInteractions(eSignatureService);
    }

    @Test
    @SuppressWarnings("rawtypes")
    void disposeWithSignature_signsBeforeDisposing_neverSignsViaSharedTx() {
        NcrOrder o = new NcrOrder(); o.setNcrId(7L); o.setNcrCode("NCR-007");
        when(baseMapper.selectById(7L)).thenReturn(o);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(42L);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        assertTrue(service.disposeWithSignature(7L, "RETURN", "退货", SignAuthMethod.PASSWORD, "admin123"));

        // 顺序: 先签署 -> 再绑定(update#1) -> 再处置 CAS(update#2) -> 分支流转(update#3)
        InOrder inOrder = inOrder(eSignatureService, baseMapper);
        inOrder.verify(eSignatureService).signInNewTransaction(any(SignCommand.class));
        inOrder.verify(baseMapper).update(isNull(), any());
        verify(baseMapper, times(3)).update(isNull(), any());
        verify(eSignatureService, never()).sign(any(SignCommand.class));

        ArgumentCaptor<Wrapper> wrappers = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper, times(3)).update(isNull(), wrappers.capture());
        LambdaUpdateWrapper<?> bind = (LambdaUpdateWrapper<?>) wrappers.getAllValues().get(0);
        assertTrue(String.valueOf(bind.getSqlSet()).contains("disposition_signature_id"), "首次写入应为签名绑定");
        assertTrue(bind.getParamNameValuePairs().containsValue(42L), "绑定的应是签署返回的签名ID");
        LambdaUpdateWrapper<?> cas = (LambdaUpdateWrapper<?>) wrappers.getAllValues().get(1);
        assertTrue(String.valueOf(cas.getSqlSet()).contains("status"), "第二次写入应为处置状态 CAS(DISPOSITION)");
    }

    @Test
    void disposeWithSignature_signFailure_propagatesAndNeverDisposes() {
        NcrOrder o = new NcrOrder(); o.setNcrId(7L); o.setNcrCode("NCR-007");
        when(baseMapper.selectById(7L)).thenReturn(o);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class)))
            .thenThrow(new ServiceException("认证失败"));

        assertThrows(ServiceException.class,
            () -> service.disposeWithSignature(7L, "SCRAP", null, SignAuthMethod.PASSWORD, "wrong"));

        // 处置(含分支副作用)一次都不得发生: 授权未成立
        verify(baseMapper, never()).update(any(), any());
    }

    @Test
    void disposeWithSignature_statusConflict_throwsSoCallerSeesFailure() {
        NcrOrder o = new NcrOrder(); o.setNcrId(7L); o.setNcrCode("NCR-007");
        when(baseMapper.selectById(7L)).thenReturn(o);
        when(eSignatureService.signInNewTransaction(any(SignCommand.class))).thenReturn(9L);
        when(baseMapper.update(any(), any())).thenReturn(0);   // 状态已被他人变更

        assertTrue(assertThrows(RuntimeException.class,
            () -> service.disposeWithSignature(7L, "RETURN", null, SignAuthMethod.PASSWORD, "cred"))
            .getMessage().contains("处置失败"));
    }

    @Test
    void disposeWithSignature_transactionAndLockGuards() throws NoSuchMethodException {
        // 反射守卫: 丢掉 @Transactional 就会退回「签署与处置各自提交」的可发散状态
        Method m = NcrOrderServiceImpl.class.getMethod("disposeWithSignature",
            Long.class, String.class, String.class, String.class, String.class);
        assertNotNull(m.getAnnotation(Transactional.class), "disposeWithSignature 必须声明 @Transactional");
        assertNotNull(m.getAnnotation(Lock4j.class), "disposeWithSignature 必须持有与 dispose 相同的 NCR 锁");
    }
}

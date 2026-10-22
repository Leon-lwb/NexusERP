package com.nexus.mes.ncr.service.impl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.lock.annotation.Lock4j;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.core.toolkit.LambdaUtils;
import com.baomidou.mybatisplus.core.toolkit.support.ColumnCache;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.json.utils.JsonUtils;
import com.nexus.common.sign.constants.SignAuthMethod;
import com.nexus.common.sign.constants.SignBizType;
import com.nexus.common.sign.domain.dto.SignCommand;
import com.nexus.common.sign.service.ESignatureService;
import com.nexus.mes.lt.domain.LtLot;
import com.nexus.mes.lt.mapper.LtLotMapper;
import com.nexus.mes.lt.service.ILtLotService;
import com.nexus.mes.ncr.domain.NcrDefect;
import com.nexus.mes.ncr.domain.NcrOrder;
import com.nexus.mes.ncr.domain.bo.NcrOrderBo;
import com.nexus.mes.ncr.domain.vo.NcrOrderVo;
import com.nexus.mes.ncr.mapper.NcrDefectMapper;
import com.nexus.mes.ncr.mapper.NcrOrderMapper;
import com.nexus.mes.ncr.service.INcrDefectService;
import com.nexus.mes.ncr.service.INcrReworkOrderService;
import com.nexus.mes.pro.service.IProAndonService;
import com.nexus.mes.qc.mapper.QcIpqcMapper;
import com.nexus.mes.testsupport.SpringStaticBootstrap;
import com.nexus.system.strategy.AutoCodeUtil;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NCR不合格品单 Service 单元测试。
 * <p>mockStatic(LambdaUtils,CALLS_REAL_METHODS) 解决 lambda 缓存；
 * SpringStaticBootstrap 准备真实 ObjectMapper，缺陷 JSON 走实际 JsonUtils 和业务解析。
 * Mapper/写入服务使用替身，不代表数据库事务回滚或硬件端到端验收。
 * P0-6 处置签署(signDisposition + disposeWithSignature)的用例见文末。</p>
 */
@ExtendWith(MockitoExtension.class)
class NcrOrderServiceImplTest {

    @Mock
    private NcrOrderMapper baseMapper;
    @Mock
    private NcrDefectMapper defectMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private ILtLotService ltLotService;
    @Mock
    private LtLotMapper ltLotMapper;
    @Mock
    private QcIpqcMapper qcIpqcMapper;
    @Mock
    private IProAndonService proAndonService;
    @Mock
    private INcrReworkOrderService ncrReworkOrderService;
    @Mock
    private INcrDefectService defectService;
    @Mock
    private ESignatureService eSignatureService;
    @InjectMocks
    private NcrOrderServiceImpl service;

    @Captor
    private ArgumentCaptor<NcrOrder> orderCaptor;
    @Captor
    private ArgumentCaptor<List<NcrDefect>> defectsCaptor;
    private MockedStatic<LambdaUtils> lambdaUtilsMock;
    private static ObjectMapper sharedJsonMapper;
    private static DeserializationConfig sharedJsonConfiguration;

    @BeforeAll
    static void initLotTableInfo() {
        // 同套件其它测试可能已经初始化 JsonUtils；复用其真实缓存实例，不替换或改动配置。
        SpringStaticBootstrap.ensureActive();
        sharedJsonMapper = JsonUtils.getObjectMapper();
        sharedJsonConfiguration = sharedJsonMapper.getDeserializationConfig();
        assertFalse(mockingDetails(sharedJsonMapper).isMock(),
            "缺陷回归必须使用真实 ObjectMapper，不得 mock JsonUtils 解析结果");
        // LtLot 的真实列缓存: resolveLot/lotCodeOf 会构造 LtLot 查询与条件更新
        MybatisConfiguration configuration = new MybatisConfiguration();
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(configuration, ""), LtLot.class);
    }

    @AfterAll
    static void clearLotTableInfo() {
        TableInfoHelper.remove(LtLot.class);
    }

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
        // MES-NCR-03: updateByBo 白名单字段
        cols.put("WONO", col("wo_no")); cols.put("CARDID", col("card_id"));
        cols.put("STEPRECORDID", col("step_record_id")); cols.put("PROCESSID", col("process_id"));
        cols.put("PROCESSNAME", col("process_name")); cols.put("MATERIALID", col("material_id"));
        cols.put("MATERIALCODE", col("material_code")); cols.put("MATERIALNAME", col("material_name"));
        cols.put("LOTCODE", col("lot_code"));
        lambdaUtilsMock.when(() -> LambdaUtils.getColumnMap(NcrOrder.class)).thenReturn(cols);
    }
    private ColumnCache col(String c) { return new ColumnCache(c, c); }
    @AfterEach
    void tearDown() {
        try {
            // 所有正常/失败路径都不得更改共享 mapper，避免后续 suite/bootstrap 用户受影响。
            assertAll(
                () -> assertSame(sharedJsonMapper, JsonUtils.getObjectMapper()),
                () -> assertSame(sharedJsonConfiguration, sharedJsonMapper.getDeserializationConfig(),
                    "NCR 专用 reader 选项不得改变共享 ObjectMapper 配置"));
        } finally {
            if (lambdaUtilsMock != null) {
                lambdaUtilsMock.close();
            }
        }
    }

    private void stubCreate(Long ncrId, String code) {
        when(baseMapper.selectVoOne(any())).thenReturn(null);
        when(autoCodeUtil.genSerialCode("MES_NCR_CODE", null)).thenReturn(code);
        when(baseMapper.insert(any(NcrOrder.class))).thenAnswer(inv -> { ((NcrOrder) inv.getArgument(0)).setNcrId(ncrId); return 1; });
    }

    // ==================== Bug1: defectQty null -> ZERO ====================

    @Test
    void autoCreateFromIqc_defectQtyNull_defaultsZero() {
        stubCreate(100L, "NCR-001");
        // 此用例只验证 NCR 头数量默认值，JSON 明细另有真实解析回归。
        service.autoCreateFromIqc(1L, 9001L, 1001L, "");

        verify(baseMapper).insert(orderCaptor.capture());
        assertEquals(BigDecimal.ZERO, orderCaptor.getValue().getDefectQty(), "Bug1: defectQty null 应默认 0");
        assertEquals("IQC", orderCaptor.getValue().getSourceType());
        assertEquals("OPEN", orderCaptor.getValue().getStatus());
    }

    // ==================== defectInfo: 真实 JSON 解析与写入边界 ====================

    @Test
    void autoCreateFromIqc_parsesDefectInfo_defectCodeIdNull() {
        stubCreate(101L, "NCR-JSON-001");
        NcrOrderVo created = stubCreatedView(101L);

        NcrOrderVo result = service.autoCreateFromIqc(1L, 9001L, 1001L,
            "[{\"code\":\"SCRATCH\",\"qty\":5,\"desc\":\"划痕\"},"
                + "{\"code\":\"DENT\",\"qty\":\"0.1250\",\"desc\":\"凹痕\"}]");

        assertSame(created, result);
        InOrder writes = inOrder(baseMapper, defectService);
        writes.verify(baseMapper).insert(orderCaptor.capture());
        writes.verify(defectService).batchInsertForNcr(eq(101L), defectsCaptor.capture());
        writes.verify(baseMapper).selectVoById(101L);
        assertEquals(101L, orderCaptor.getValue().getNcrId());
        assertEquals("IQC", orderCaptor.getValue().getSourceType());
        assertEquals(1L, orderCaptor.getValue().getSourceId());
        assertEquals("OPEN", orderCaptor.getValue().getStatus());
        List<NcrDefect> defects = defectsCaptor.getValue();
        assertEquals(2, defects.size());
        assertDefect(defects.get(0), 101L, "SCRATCH", new BigDecimal("5"), "划痕");
        assertDefect(defects.get(1), 101L, "DENT", new BigDecimal("0.1250"), "凹痕");
        verifyNoInteractions(defectMapper);
        verifyNoMoreInteractions(defectService);
    }

    @Test
    void autoCreateFromIqc_defectInfoParseFail_safe() {
        assertNoDefectWriteForInput("[{\"code\":\"SCRATCH\",\"qty\":5}");
    }

    @ParameterizedTest(name = "empty defect info [{index}]")
    @NullAndEmptySource
    @ValueSource(strings = {" \t\n", "[]", "null"})
    void autoCreateFromIqc_emptyDefectInfo_createsNcrWithoutDefects(String defectInfo) {
        assertNoDefectWriteForInput(defectInfo);
    }

    @ParameterizedTest(name = "invalid defect input [{index}]")
    @ValueSource(strings = {
        "{\"code\":\"SCRATCH\",\"qty\":5}",
        "[null]",
        "[{\"code\":\"SCRATCH\",\"qty\":\"not-a-number\"}]",
        "[{\"code\":\"SCRATCH\",\"qty\":\"\"}]",
        "[{\"code\":\"SCRATCH\"}]",
        "[{\"code\":\"SCRATCH\",\"qty\":null}]",
        "[{\"code\":\"SCRATCH\",\"qty\":5},{\"code\":\"DENT\",\"qty\":\"bad\"}]"
    })
    void autoCreateFromIqc_invalidDefectInput_createsNcrWithoutPartialDefectWrites(String defectInfo) {
        assertNoDefectWriteForInput(defectInfo);
    }

    @ParameterizedTest(name = "quantity {0} is retained")
    @CsvSource({"0, 0", "2147483648, 2147483648", "2.75, 2.75", "\"0.1250\", 0.1250"})
    void autoCreateFromIqc_validQuantityRepresentations_areRetained(String jsonQty, String expectedQty) {
        stubCreate(103L, "NCR-JSON-003");
        NcrOrderVo created = stubCreatedView(103L);

        assertSame(created, service.autoCreateFromIqc(3L, 9003L, 1001L,
            "[{\"code\":\"SCRATCH\",\"qty\":" + jsonQty + ",\"desc\":\"划痕\"}]"));

        verify(defectService).batchInsertForNcr(eq(103L), defectsCaptor.capture());
        assertEquals(1, defectsCaptor.getValue().size());
        assertDefect(defectsCaptor.getValue().get(0), 103L, "SCRATCH", new BigDecimal(expectedQty), "划痕");
        verifyNoMoreInteractions(defectService);
    }

    @Test
    void autoCreateFromIqc_largeNumericDecimal_retainsExactQuantity() {
        stubCreate(105L, "NCR-JSON-005");
        NcrOrderVo created = stubCreatedView(105L);

        assertSame(created, service.autoCreateFromIqc(5L, 9005L, 1001L,
            "[{\"code\":\"SCRATCH\",\"qty\":10000000000000.0001,\"desc\":\"划痕\"}]"));

        verify(defectService).batchInsertForNcr(eq(105L), defectsCaptor.capture());
        assertEquals(1, defectsCaptor.getValue().size());
        assertDefect(defectsCaptor.getValue().get(0), 105L, "SCRATCH",
            new BigDecimal("10000000000000.0001"), "划痕");
        verifyNoMoreInteractions(defectService);
    }

    @ParameterizedTest(name = "trailing JSON content [{index}] is rejected")
    @ValueSource(strings = {
        "[{\"code\":\"SCRATCH\",\"qty\":5}] trailing-garbage",
        "[{\"code\":\"SCRATCH\",\"qty\":5}][{\"code\":\"DENT\",\"qty\":1}]"
    })
    void autoCreateFromIqc_trailingContent_createsNcrWithoutDefectWrites(String defectInfo) {
        assertNoDefectWriteForInput(defectInfo);
    }

    @Test
    void autoCreateFromIqc_localReader_doesNotChangeSharedMapper() {
        ObjectMapper originalMapper = JsonUtils.getObjectMapper();
        DeserializationConfig originalConfiguration = originalMapper.getDeserializationConfig();
        String defectInfo = "[{\"code\":\"SCRATCH\",\"qty\":2.75,\"desc\":\"划痕\"}]";
        Object genericQtyBefore = JsonUtils.parseArray(defectInfo, Map.class).get(0).get("qty");
        stubCreate(106L, "NCR-JSON-006");
        NcrOrderVo created = stubCreatedView(106L);

        assertSame(created, service.autoCreateFromIqc(6L, 9006L, 1001L, defectInfo));

        Object genericQtyAfter = JsonUtils.parseArray(defectInfo, Map.class).get(0).get("qty");
        assertAll(
            () -> assertSame(originalMapper, JsonUtils.getObjectMapper()),
            () -> assertSame(originalConfiguration, originalMapper.getDeserializationConfig()),
            () -> assertEquals(originalConfiguration.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS),
                originalMapper.isEnabled(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)),
            () -> assertEquals(originalConfiguration.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS),
                originalMapper.isEnabled(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)),
            () -> assertSame(genericQtyBefore.getClass(), genericQtyAfter.getClass()),
            () -> assertEquals(genericQtyBefore, genericQtyAfter));
        verify(defectService).batchInsertForNcr(eq(106L), defectsCaptor.capture());
        assertEquals(1, defectsCaptor.getValue().size());
        assertDefect(defectsCaptor.getValue().get(0), 106L, "SCRATCH", new BigDecimal("2.75"), "划痕");
        verifyNoMoreInteractions(defectService);
    }

    @Test
    void autoCreateFromIqc_defectWriteFailure_propagatesForRollback() {
        stubCreate(104L, "NCR-JSON-004");
        DataIntegrityViolationException writeFailure =
            new DataIntegrityViolationException("NCR defect persistence failed");
        doThrow(writeFailure).when(defectService).batchInsertForNcr(eq(104L), anyList());

        assertSame(writeFailure, assertThrows(DataIntegrityViolationException.class,
            () -> service.autoCreateFromIqc(4L, 9004L, 1001L,
                "[{\"code\":\"SCRATCH\",\"qty\":5}]")),
            "持久化失败不是 JSON 解析失败，必须向调用方传播以允许事务回滚");

        verify(baseMapper).insert(orderCaptor.capture());
        assertEquals(104L, orderCaptor.getValue().getNcrId());
        verify(defectService).batchInsertForNcr(eq(104L), defectsCaptor.capture());
        assertEquals(1, defectsCaptor.getValue().size());
        assertDefect(defectsCaptor.getValue().get(0), 104L, "SCRATCH", new BigDecimal("5"), null);
        verify(baseMapper, never()).selectVoById(any());
        verifyNoInteractions(ltLotService, proAndonService);
    }

    private NcrOrderVo stubCreatedView(Long ncrId) {
        NcrOrderVo created = new NcrOrderVo();
        created.setNcrId(ncrId);
        when(baseMapper.selectVoById(ncrId)).thenReturn(created);
        return created;
    }

    private void assertNoDefectWriteForInput(String defectInfo) {
        stubCreate(102L, "NCR-JSON-002");
        NcrOrderVo created = stubCreatedView(102L);

        assertSame(created, assertDoesNotThrow(
            () -> service.autoCreateFromIqc(2L, 9002L, 1001L, defectInfo)));

        verify(baseMapper).insert(orderCaptor.capture());
        NcrOrder order = orderCaptor.getValue();
        assertEquals(102L, order.getNcrId());
        assertEquals("NCR-JSON-002", order.getNcrCode());
        assertEquals("IQC", order.getSourceType());
        assertEquals(2L, order.getSourceId());
        assertEquals("OPEN", order.getStatus());
        verify(baseMapper).selectVoById(102L);
        verifyNoInteractions(defectService, defectMapper);
    }

    private void assertDefect(NcrDefect defect, Long ncrId, String code, BigDecimal qty, String desc) {
        assertAll(
            () -> assertEquals(ncrId, defect.getNcrId()),
            () -> assertEquals(code, defect.getDefectCode()),
            () -> assertNull(defect.getDefectCodeId(), "自动建单允许尚未关联正式缺陷代码库"),
            () -> assertEquals(qty, defect.getDefectQty()),
            () -> assertEquals(desc, defect.getRootCauseDesc()));
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

    // ==================== MES-NCR-01: lotId 必须落真实批次 ====================

    @Test
    void autoCreateFromIqc_linksRealLotFromIqcAssociation() {
        stubCreate(100L, "NCR-100");
        LtLot lot = new LtLot();
        lot.setLotId(500L);
        lot.setLotCode("LOT-500");
        when(ltLotMapper.selectList(any())).thenReturn(List.of(lot));

        service.autoCreateFromIqc(9L, 9001L, 1001L, "");

        verify(baseMapper).insert(orderCaptor.capture());
        assertEquals(500L, orderCaptor.getValue().getLotId(), "MES-NCR-01: 必须传入真实批次ID");
        verify(ltLotService).quarantineByNcr(eq(500L), any(), any());
    }

    @Test
    void autoCreateFromIqc_withoutResolvableLot_keepsNullAndDoesNotFakeLot() {
        stubCreate(101L, "NCR-101");
        when(ltLotMapper.selectList(any())).thenReturn(List.of());

        service.autoCreateFromIqc(10L, 9002L, 1002L, "");

        verify(baseMapper).insert(orderCaptor.capture());
        assertNull(orderCaptor.getValue().getLotId(), "无法取得批次时不得伪造批次ID");
        verify(ltLotService, never()).quarantineByNcr(any(), any(), any());
    }

    // ==================== MES-NCR-02: 批次动作失败必须回滚 ====================

    @Test
    void dispose_scrap_batchActionFailure_rollsBackWithoutClosing() {
        NcrOrder o = new NcrOrder();
        o.setNcrId(1L); o.setNcrCode("NCR-001"); o.setStatus("OPEN"); o.setLotId(20L);
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(ltLotService.scrap(eq(20L), any())).thenThrow(new RuntimeException("批次状态非 QUARANTINED"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.dispose(1L, "SCRAP", "报废"));

        assertTrue(ex.getMessage().contains("批次报废失败"), "MES-NCR-02: 批次报废失败必须抛出回滚");
        // 只允许处置 CAS(DISPOSITION) 一次写入; 不得继续 CAS 到 CLOSED
        verify(baseMapper, times(1)).update(isNull(), any());
    }

    @Test
    void dispose_accept_batchReleaseFailure_rollsBackWithoutClosing() {
        NcrOrder o = new NcrOrder();
        o.setNcrId(2L); o.setNcrCode("NCR-002"); o.setStatus("REVIEW"); o.setLotId(21L);
        when(baseMapper.selectById(2L)).thenReturn(o);
        when(baseMapper.update(isNull(), any())).thenReturn(1);
        when(ltLotService.release(21L)).thenThrow(new RuntimeException("批次状态已变更"));

        RuntimeException ex = assertThrows(RuntimeException.class, () -> service.dispose(2L, "ACCEPT_AS_IS", "让步"));

        assertTrue(ex.getMessage().contains("批次放行失败"), "MES-NCR-02: 批次放行失败必须抛出回滚");
        verify(baseMapper, times(1)).update(isNull(), any());
    }

    // ==================== MES-NCR-03: 编辑不得回落处置后的状态 ====================

    @Test
    @SuppressWarnings("rawtypes")
    void updateByBo_usesConditionalUpdate_neverUpdateById() {
        NcrOrder o = new NcrOrder();
        o.setNcrId(1L);
        o.setStatus("OPEN");
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(isNull(), any())).thenReturn(1);

        NcrOrderBo bo = new NcrOrderBo();
        bo.setNcrId(1L);
        bo.setWoNo("WO-001");
        bo.setDefectQty(new BigDecimal("3"));

        assertTrue(service.updateByBo(bo));

        // 绝不整实体 updateById: 否则会把并发的 CLOSED/DISPOSITION 状态写回去
        verify(baseMapper, never()).updateById(any(NcrOrder.class));
        ArgumentCaptor<Wrapper> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(isNull(), captor.capture());
        LambdaUpdateWrapper<?> wrapper = (LambdaUpdateWrapper<?>) captor.getValue();
        assertFalse(String.valueOf(wrapper.getSqlSet()).contains("status"),
            "MES-NCR-03: status 不在可编辑白名单内, 不得出现在 SET 中");
        assertTrue(String.valueOf(wrapper.getSqlSegment()).contains("status"),
            "必须带 status IN (OPEN, REVIEW) 的条件守卫");
    }

    @Test
    void updateByBo_statusChangedConcurrently_throws() {
        NcrOrder o = new NcrOrder();
        o.setNcrId(1L);
        o.setStatus("OPEN");
        when(baseMapper.selectById(1L)).thenReturn(o);
        when(baseMapper.update(isNull(), any())).thenReturn(0);

        NcrOrderBo bo = new NcrOrderBo();
        bo.setNcrId(1L);
        bo.setWoNo("WO-001");

        ServiceException ex = assertThrows(ServiceException.class, () -> service.updateByBo(bo));

        assertTrue(ex.getMessage().contains("状态已变更"), ex.getMessage());
        verify(baseMapper, never()).updateById(any(NcrOrder.class));
    }

    @Test
    void updateByBo_rejectsDispositionStatus() {
        NcrOrder o = new NcrOrder();
        o.setNcrId(1L);
        o.setStatus("DISPOSITION");
        when(baseMapper.selectById(1L)).thenReturn(o);

        NcrOrderBo bo = new NcrOrderBo();
        bo.setNcrId(1L);

        assertThrows(RuntimeException.class, () -> service.updateByBo(bo));
        verify(baseMapper, never()).update(any(), any());
        verify(baseMapper, never()).updateById(any(NcrOrder.class));
    }

    // ==================== MES-NCR-05: 工序出口 sourceId 唯一定位一次出站 ====================

    @Test
    void autoCreateFromExit_usesStepRecordIdAsSourceId() {
        stubCreate(300L, "NCR-300");

        service.autoCreateFromExit(11L, 22L, 33L, BigDecimal.ONE, "");

        verify(baseMapper).insert(orderCaptor.capture());
        assertEquals(22L, orderCaptor.getValue().getSourceId(),
            "MES-NCR-05: sourceId 必须能唯一定位一次出站(stepRecordId), 否则幂等检查被绕过");
    }

    // ==================== MES-NCR-06: 处置方式白名单 ====================

    @Test
    void disposeWithSignature_rejectsUnknownDispositionBeforeSigning() {
        ServiceException ex = assertThrows(ServiceException.class,
            () -> service.disposeWithSignature(1L, "FOO", null, SignAuthMethod.PASSWORD, "cred"));

        assertTrue(ex.getMessage().contains("不支持的处置方式"), ex.getMessage());
        verifyNoInteractions(eSignatureService);
        verifyNoInteractions(baseMapper);
    }
}

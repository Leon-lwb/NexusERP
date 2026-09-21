package com.nexus.erp.wm.service.impl;

import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.WmProductionIssueLine;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.domain.vo.WmProductionIssueVo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.mapper.WmBatchMapper;
import com.nexus.erp.wm.mapper.WmProductionIssueLineMapper;
import com.nexus.erp.wm.service.IWmProductionIssueService;
import com.nexus.system.service.ISysConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * PDA 发料批次质检门禁单元测试（收尾批次：MES-X-01 / MES-QC-01 口径统一）。
 * <p>核心断言：门禁改为<b>白名单</b>——仅 {@code qc_status=QUALIFIED} 放行，
 * PENDING / NULL / 批次记录缺失 / UNQUALIFIED 一律拦截。
 * 修复前只拦 UNQUALIFIED，PENDING 与 NULL 会被静默放行（断言 {@code ok=true} 时会失败）。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
@Tag("local")
class PdaIssueServiceImplTest {

    private static final String GATE_KEY = "wm.issue.batch.qcGate";
    private static final Long ISSUE_ID = 10L;
    private static final Long LINE_ID = 1L;
    private static final Long MATERIAL_ID = 5L;
    private static final String BATCH_CODE = "B20260920";

    @Mock
    private IWmProductionIssueService issueService;
    @Mock
    private WmProductionIssueLineMapper lineMapper;
    @Mock
    private WmBatchMapper batchMapper;
    @Mock
    private ISysConfigService configService;

    @InjectMocks
    private PdaIssueServiceImpl service;

    @BeforeEach
    void setUp() {
        WmProductionIssueVo head = new WmProductionIssueVo();
        head.setStatus("CREATED");
        lenient().when(issueService.selectById(ISSUE_ID)).thenReturn(head);

        WmProductionIssueLine line = new WmProductionIssueLine();
        line.setLineId(LINE_ID);
        line.setIssueId(ISSUE_ID);
        line.setMaterialId(MATERIAL_ID);
        line.setRequiredQty(new BigDecimal("10"));
        line.setIssuedQty(BigDecimal.ZERO);
        lenient().when(lineMapper.selectList(any())).thenReturn(List.of(line));
        lenient().when(lineMapper.updateById(any(WmProductionIssueLine.class))).thenReturn(1);
        lenient().when(configService.selectConfigByKey(GATE_KEY)).thenReturn("true");
    }

    /** 修复前会失败：PENDING 放行；修复后必须拦截。 */
    @Test
    void pickBlocksPendingBatch() {
        when(batchMapper.selectOne(any())).thenReturn(batch("PENDING"));

        PdaActionResultVo result = service.pick(ISSUE_ID, bo());

        assertEquals(Boolean.FALSE, result.getOk(), "PENDING 批次必须拦截");
        assertTrue(result.getMessage().contains("PENDING"), "应给出可读原因, 实际: " + result.getMessage());
        verify(issueService, never()).execute(anyLong());
    }

    /** 修复前会失败：qc_status 为 NULL（回写丢失/无质检记录）放行；修复后必须拦截。 */
    @Test
    void pickBlocksNullQcStatusBatch() {
        when(batchMapper.selectOne(any())).thenReturn(batch(null));

        PdaActionResultVo result = service.pick(ISSUE_ID, bo());

        assertEquals(Boolean.FALSE, result.getOk(), "qc_status=NULL 必须拦截");
        assertTrue(result.getMessage().contains("待检/未回写"), "实际: " + result.getMessage());
    }

    /** 修复前会失败：批次未登记（无 wm_batch 行）放行；修复后必须拦截。 */
    @Test
    void pickBlocksWhenBatchRecordMissing() {
        when(batchMapper.selectOne(any())).thenReturn(null);

        PdaActionResultVo result = service.pick(ISSUE_ID, bo());

        assertEquals(Boolean.FALSE, result.getOk(), "批次未登记必须拦截");
        assertTrue(result.getMessage().contains("无质检记录"), "实际: " + result.getMessage());
    }

    /** UNQUALIFIED 行为保持不变（修复前后都拦截）。 */
    @Test
    void pickBlocksUnqualifiedBatch() {
        when(batchMapper.selectOne(any())).thenReturn(batch("UNQUALIFIED"));

        PdaActionResultVo result = service.pick(ISSUE_ID, bo());

        assertEquals(Boolean.FALSE, result.getOk());
        assertTrue(result.getMessage().contains("UNQUALIFIED"), "实际: " + result.getMessage());
    }

    /** 白名单放行：QUALIFIED 正常执行过账。 */
    @Test
    void pickAllowsQualifiedBatch() {
        when(batchMapper.selectOne(any())).thenReturn(batch("QUALIFIED"));

        PdaActionResultVo result = service.pick(ISSUE_ID, bo());

        assertEquals(Boolean.TRUE, result.getOk(), "QUALIFIED 必须放行, 实际: " + result.getMessage());
        assertEquals(PdaActionResultVo.NEXT_SCAN_MATERIAL, result.getNextAction());
        verify(issueService).execute(ISSUE_ID);
        assertNotNull(result);
    }

    /** 门禁开关关闭时不做批次判定（保持既有开关语义）。 */
    @Test
    void pickSkipsGateWhenConfigDisabled() {
        when(configService.selectConfigByKey(GATE_KEY)).thenReturn("false");

        PdaActionResultVo result = service.pick(ISSUE_ID, bo());

        assertEquals(Boolean.TRUE, result.getOk());
        verify(batchMapper, never()).selectOne(any());
    }

    private WmBatch batch(String qcStatus) {
        WmBatch b = new WmBatch();
        b.setBatchCode(BATCH_CODE);
        b.setMaterialId(MATERIAL_ID);
        b.setQcStatus(qcStatus);
        return b;
    }

    private PdaPickBo bo() {
        PdaPickBo bo = new PdaPickBo();
        bo.setLineId(LINE_ID);
        bo.setQty(new BigDecimal("2"));
        bo.setBatchCode(BATCH_CODE);
        return bo;
    }
}

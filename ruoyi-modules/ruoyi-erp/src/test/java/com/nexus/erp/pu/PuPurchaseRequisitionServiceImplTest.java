package com.nexus.erp.pu;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.AbstractWrapper;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.bd.domain.BdMaterial;
import com.nexus.erp.bd.mapper.BdMaterialMapper;
import com.nexus.erp.pu.domain.PuPurchaseRequisition;
import com.nexus.erp.pu.domain.PuPurchaseRequisitionLine;
import com.nexus.erp.pu.domain.bo.PuPurchaseRequisitionBo;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseRequisitionMapper;
import com.nexus.erp.pu.service.impl.PuPurchaseRequisitionServiceImpl;
import com.nexus.erp.wm.mapper.WmMaterialStockMapper;
import com.nexus.system.strategy.AutoCodeUtil;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** 采购申请安全草稿创建与条件补偿测试。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuPurchaseRequisitionServiceImplTest {

    private static final long CREATE_MILLIS = 1_795_622_400_000L;
    private static final long UPDATE_MILLIS = 1_795_622_460_000L;

    @Mock
    private PuPurchaseRequisitionMapper baseMapper;
    @Mock
    private PuPurchaseRequisitionLineMapper lineMapper;
    @Mock
    private PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    @Mock
    private BdMaterialMapper bdMaterialMapper;
    @Mock
    private WmMaterialStockMapper materialStockMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;

    @InjectMocks
    private PuPurchaseRequisitionServiceImpl requisitionService;

    @BeforeAll
    static void initTableInfo() {
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, BdMaterial.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseRequisition.class);
        TableInfoHelper.initTableInfo(assistant, PuPurchaseRequisitionLine.class);
    }

    @Test
    void createDraftReturnsGeneratedIdAndWritesBackBo() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        bo.setStatus("APPROVED");
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-7001");
        doAnswer(invocation -> {
            PuPurchaseRequisition entity = invocation.getArgument(0);
            entity.setRequisitionId(7001L);
            entity.setCreateTime(new Date(CREATE_MILLIS));
            entity.setUpdateTime(new Date(UPDATE_MILLIS));
            return 1;
        }).when(baseMapper).insert(any(PuPurchaseRequisition.class));

        Long id = requisitionService.createDraft(bo);

        assertEquals(7001L, id);
        assertEquals(7001L, bo.getRequisitionId());
        assertEquals("PR-7001", bo.getRequisitionNo());
        assertEquals("DRAFT", bo.getStatus());
        assertEquals(new Date(CREATE_MILLIS), bo.getCreateTime());
        assertEquals(new Date(UPDATE_MILLIS), bo.getUpdateTime());
        ArgumentCaptor<PuPurchaseRequisitionLine> line = ArgumentCaptor.forClass(PuPurchaseRequisitionLine.class);
        verify(lineMapper).insert(line.capture());
        assertEquals(7001L, line.getValue().getRequisitionId());
        assertEquals(101L, line.getValue().getMaterialId());
    }

    @Test
    void insertByBoReturnsAffectedRowsAndUsesSameSafeCreateSemantics() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-7002");
        doAnswer(invocation -> {
            PuPurchaseRequisition entity = invocation.getArgument(0);
            entity.setRequisitionId(7002L);
            entity.setCreateTime(new Date(CREATE_MILLIS));
            entity.setUpdateTime(new Date(UPDATE_MILLIS));
            return 1;
        }).when(baseMapper).insert(any(PuPurchaseRequisition.class));

        int rows = requisitionService.insertByBo(bo);

        assertEquals(1, rows);
        assertEquals(7002L, bo.getRequisitionId());
        assertEquals("PR-7002", bo.getRequisitionNo());
        assertEquals("DRAFT", bo.getStatus());
        assertEquals(new Date(CREATE_MILLIS), bo.getCreateTime());
        assertEquals(new Date(UPDATE_MILLIS), bo.getUpdateTime());
        verify(lineMapper).insert(any(PuPurchaseRequisitionLine.class));
    }

    @Test
    void createDraftRejectsInvalidInputBeforeWriting() {
        PuPurchaseRequisitionBo withId = purchaseBo();
        withId.setRequisitionId(9L);
        PuPurchaseRequisitionBo withoutLines = new PuPurchaseRequisitionBo();

        assertThrows(IllegalArgumentException.class, () -> requisitionService.createDraft(null));
        assertThrows(ServiceException.class, () -> requisitionService.createDraft(withId));
        assertThrows(ServiceException.class, () -> requisitionService.createDraft(withoutLines));

        verifyNoInteractions(baseMapper, lineMapper, autoCodeUtil);
    }

    @Test
    void createDraftDoesNotWriteLinesWhenHeaderInsertAffectsZeroRows() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-FAIL");
        when(baseMapper.insert(any(PuPurchaseRequisition.class))).thenReturn(0);

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verifyNoInteractions(lineMapper);
    }

    @Test
    void createDraftDoesNotWriteLinesWhenGeneratedIdIsMissing() {
        PuPurchaseRequisitionBo bo = purchaseBo();
        when(autoCodeUtil.genSerialCode("PU_REQUISITION_CODE", null)).thenReturn("PR-NO-ID");
        when(baseMapper.insert(any(PuPurchaseRequisition.class))).thenReturn(1);

        assertThrows(ServiceException.class, () -> requisitionService.createDraft(bo));

        verifyNoInteractions(lineMapper);
    }

    @Test
    void deleteDraftIfUnchangedWithUpdateMarkerDeletesHeaderBeforeLines() {
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(1);
        when(lineMapper.delete(any(Wrapper.class))).thenReturn(1);

        boolean deleted = requisitionService.deleteDraftIfUnchanged(7001L, "U:" + UPDATE_MILLIS);

        assertTrue(deleted);
        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> header = wrapperCaptor();
        ArgumentCaptor<Wrapper<PuPurchaseRequisitionLine>> lines = wrapperCaptor();
        InOrder order = inOrder(baseMapper, lineMapper);
        order.verify(baseMapper).delete(header.capture());
        order.verify(lineMapper).delete(lines.capture());
        assertSqlContains(header.getValue(), "requisition_id", "status", "update_time");
        assertTrue(parameters(header.getValue()).containsValue(7001L));
        assertTrue(parameters(header.getValue()).containsValue("DRAFT"));
        assertTrue(parameters(header.getValue()).containsValue(new Date(UPDATE_MILLIS)));
        assertSqlContains(lines.getValue(), "requisition_id");
        assertTrue(parameters(lines.getValue()).containsValue(7001L));
    }

    @Test
    void deleteDraftIfUnchangedDoesNotDeleteLinesWhenStatusChanged() {
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertFalse(requisitionService.deleteDraftIfUnchanged(7001L, "U:" + UPDATE_MILLIS));

        verify(baseMapper).delete(any(Wrapper.class));
        verify(lineMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void deleteDraftIfUnchangedDoesNotDeleteLinesWhenMarkerChanged() {
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(0);

        assertFalse(requisitionService.deleteDraftIfUnchanged(7001L, "U:" + (UPDATE_MILLIS + 1)));

        verify(baseMapper).delete(any(Wrapper.class));
        verify(lineMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void createMarkerRequiresUpdateTimeToRemainNull() {
        when(baseMapper.delete(any(Wrapper.class))).thenReturn(0);
        ArgumentCaptor<Wrapper<PuPurchaseRequisition>> header = wrapperCaptor();

        assertFalse(requisitionService.deleteDraftIfUnchanged(7001L, "C:" + CREATE_MILLIS));

        verify(baseMapper).delete(header.capture());
        verify(lineMapper, never()).delete(any(Wrapper.class));
        assertSqlContains(header.getValue(), "create_time", "update_time is null");
        assertTrue(parameters(header.getValue()).containsValue(new Date(CREATE_MILLIS)));
    }

    @Test
    void invalidCompensationMarkerFailsClosedBeforeAnyDelete() {
        assertFalse(requisitionService.deleteDraftIfUnchanged(null, "U:" + UPDATE_MILLIS));
        assertFalse(requisitionService.deleteDraftIfUnchanged(0L, "U:" + UPDATE_MILLIS));
        assertFalse(requisitionService.deleteDraftIfUnchanged(7001L, null));
        assertFalse(requisitionService.deleteDraftIfUnchanged(7001L, "X:" + UPDATE_MILLIS));
        assertFalse(requisitionService.deleteDraftIfUnchanged(7001L, "U:not-a-number"));

        verifyNoInteractions(baseMapper, lineMapper);
    }

    /** 物料状态条件必须是数值 1（启用），且不得再出现字符串 'ACTIVE'。 */
    @Test
    @SuppressWarnings("unchecked")
    void generateFromLowStockMaterialStatusComparedNumerically() {
        when(bdMaterialMapper.selectList(any())).thenReturn(List.of());

        requisitionService.generateFromLowStock();

        ArgumentCaptor<LambdaQueryWrapper<BdMaterial>> captor =
            ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(bdMaterialMapper).selectList(captor.capture());
        LambdaQueryWrapper<BdMaterial> wrapper = captor.getValue();
        wrapper.getSqlSegment();
        assertNotNull(wrapper.getParamNameValuePairs());
        assertTrue(wrapper.getParamNameValuePairs().containsValue(1L),
            "状态条件应为数值 1L（启用），实际参数=" + wrapper.getParamNameValuePairs());
        assertFalse(wrapper.getParamNameValuePairs().containsValue("ACTIVE"),
            "不得再使用字符串 'ACTIVE' 与 tinyint 比较");
    }

    private PuPurchaseRequisitionBo purchaseBo() {
        PuPurchaseRequisitionLine line = new PuPurchaseRequisitionLine();
        line.setMaterialId(101L);
        line.setMaterialCode("M-101");
        line.setMaterialName("物料101");
        line.setApplyQuantity(new BigDecimal("5"));
        line.setShortageQuantity(new BigDecimal("5"));
        PuPurchaseRequisitionBo bo = new PuPurchaseRequisitionBo();
        bo.setRequisitionName("安全草稿");
        bo.setPuPurchaseRequisitionLineList(List.of(line));
        return bo;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private <T> ArgumentCaptor<Wrapper<T>> wrapperCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Wrapper.class);
    }

    private Map<String, Object> parameters(Wrapper<?> wrapper) {
        wrapper.getSqlSegment();
        return ((AbstractWrapper<?, ?, ?>) wrapper).getParamNameValuePairs();
    }

    private void assertSqlContains(Wrapper<?> wrapper, String... fragments) {
        String sql = wrapper.getSqlSegment().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
        for (String fragment : fragments) {
            assertTrue(sql.contains(fragment), () -> "SQL缺少片段 " + fragment + ": " + sql);
        }
    }
}

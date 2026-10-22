package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import cn.hutool.core.bean.BeanUtil;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.MapstructUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.erp.wm.domain.WmMaterialReceive;
import com.nexus.erp.wm.domain.WmMaterialReceiveDetail;
import com.nexus.erp.wm.domain.WmMaterialReceiveLine;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveDetailBo;
import com.nexus.erp.wm.domain.bo.WmMaterialReceiveLineBo;
import com.nexus.erp.wm.mapper.WmArrivalNoticeLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveDetailMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveLineMapper;
import com.nexus.erp.wm.mapper.WmMaterialReceiveMapper;
import com.nexus.erp.wm.service.WmCertifiedReceiptMutationGuard;
import com.nexus.erp.wm.service.impl.WmMaterialReceiveCrudServiceImpl;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

/** 编辑草稿后收货源行和上架明细的稳定 ID 回归。 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class WmMaterialReceiveEditAssociationTest {
    @Mock WmMaterialReceiveMapper baseMapper;
    @Mock WmMaterialReceiveLineMapper receiveLineMapper;
    @Mock WmMaterialReceiveDetailMapper receiveDetailMapper;
    @Mock WmArrivalNoticeLineMapper arrivalNoticeLineMapper;
    @Mock WmCertifiedReceiptMutationGuard certifiedReceiptGuard;
    @InjectMocks WmMaterialReceiveCrudServiceImpl service;

    @Test
    void permanentSourceGuardFailureStopsAllOldHeadAndLineWrites() {
        doThrow(new ServiceException("receipt source frozen"))
                .when(certifiedReceiptGuard)
                .requireUnposted(21L);
        assertThrows(ServiceException.class, () -> service.updateByBo(edit(51L)));
        verifyNoInteractions(baseMapper, receiveLineMapper, receiveDetailMapper);
    }

    @Test
    void createCannotClaimApprovalOrShelfAuditFromAnEditableForm() {
        WmMaterialReceiveBo bo = new WmMaterialReceiveBo();
        bo.setReceiveCode("RK202610030001");
        bo.setStatus("SHELVED");
        bo.setShelfBy(7L);
        bo.setShelfTime(new Date());
        WmMaterialReceive entity=existing("SHELVED");
        try(MockedStatic<MapstructUtils> convert=mockStatic(MapstructUtils.class)) {
            convert.when(()->MapstructUtils.convert(bo,WmMaterialReceive.class)).thenReturn(entity);
            assertThrows(ServiceException.class, () -> service.insertByBo(bo));
            verifyNoInteractions(baseMapper,receiveLineMapper,receiveDetailMapper);
        }
    }

    @BeforeAll
    static void initTableInfo() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceive.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveLine.class);
        TableInfoHelper.initTableInfo(assistant, WmMaterialReceiveDetail.class);
    }

    private WmMaterialReceive existing(String status) {
        WmMaterialReceive head = new WmMaterialReceive();
        head.setReceiveId(21L);
        head.setReceiveCode("CGRK20260929001");
        head.setReceiveName("铜芯导线采购收货");
        head.setPurId(31L);
        head.setNoticeId(41L);
        head.setStatus(status);
        return head;
    }

    private WmMaterialReceiveLine originalLine() {
        WmMaterialReceiveLine line = new WmMaterialReceiveLine();
        line.setLineId(51L);
        line.setReceiveId(21L);
        line.setMaterialId(61L);
        line.setPurchaseLineId(71L);
        line.setBatchId(81L);
        line.setBatchCode("B202609290001");
        return line;
    }

    private WmMaterialReceiveBo edit(Long lineId) {
        WmMaterialReceiveLineBo line = new WmMaterialReceiveLineBo();
        line.setLineId(lineId);
        line.setReceiveId(21L);
        line.setMaterialId(61L);
        line.setBatchId(81L);
        line.setBatchCode("B202609290001");
        line.setQuantityReceived(new BigDecimal("3"));
        WmMaterialReceiveDetailBo detail = new WmMaterialReceiveDetailBo();
        detail.setDetailId(91L);
        detail.setLineId(51L);
        detail.setReceiveId(21L);
        detail.setMaterialId(61L);
        detail.setBatchId(81L);
        detail.setQuantity(new BigDecimal("3"));
        line.setWmMaterialReceiveLineDetailList(List.of(detail));
        WmMaterialReceiveBo bo = new WmMaterialReceiveBo();
        bo.setReceiveId(21L);
        bo.setReceiveCode("CGRK20260929001");
        bo.setReceiveName("铜芯导线采购收货");
        bo.setPurId(31L);
        bo.setNoticeId(41L);
        bo.setStatus("DRAFT");
        bo.setWmMaterialReceiveLineList(List.of(line));
        return bo;
    }

    private MockedStatic<MapstructUtils> testMapper() {
        return mockStatic(MapstructUtils.class, invocation -> {
            Object source = invocation.getArgument(0);
            Class<?> target = invocation.getArgument(1);
            if (source instanceof List<?> items) {
                return items.stream().map(item -> BeanUtil.copyProperties(item, target)).toList();
            }
            return BeanUtil.copyProperties(source, target);
        });
    }
    @Test
    void webEdit_preservesPurchaseLineAndPhysicalLineAndDetailIds() {
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        WmMaterialReceiveDetail detail = new WmMaterialReceiveDetail();
        detail.setDetailId(91L);
        detail.setLineId(51L);
        detail.setReceiveId(21L);
        detail.setMaterialId(61L);
        detail.setBatchId(81L);
        when(receiveDetailMapper.selectList(any(Wrapper.class))).thenReturn(List.of(detail));
        when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(1);

        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertTrue(service.updateWithValidation(edit(51L)));
        }
        ArgumentCaptor<WmMaterialReceiveLine> lineCaptor = ArgumentCaptor.forClass(WmMaterialReceiveLine.class);
        verify(receiveLineMapper).updateById(lineCaptor.capture());
        assertEquals(51L, lineCaptor.getValue().getLineId());
        assertEquals(71L, lineCaptor.getValue().getPurchaseLineId());
        assertEquals(81L, lineCaptor.getValue().getBatchId());
        assertEquals(new BigDecimal("3"), lineCaptor.getValue().getQuantityReceived());
        ArgumentCaptor<WmMaterialReceiveDetail> detailCaptor = ArgumentCaptor.forClass(WmMaterialReceiveDetail.class);
        verify(receiveDetailMapper).updateById(detailCaptor.capture());
        assertEquals(91L, detailCaptor.getValue().getDetailId());
        assertEquals(51L, detailCaptor.getValue().getLineId());
        verify(receiveLineMapper, never()).delete(any(Wrapper.class));
        verify(receiveDetailMapper, never()).delete(any(Wrapper.class));
    }
    @Test
    void approvedReceiptCannotBeEditedByChangingRequestStatus() {
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        when(baseMapper.selectById(21L)).thenReturn(existing("APPROVED"));
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateWithValidation(edit(51L)));
        }
        verifyNoInteractions(receiveLineMapper, receiveDetailMapper);
        verify(baseMapper, never()).updateById(any(WmMaterialReceive.class));
    }

    @Test
    void anotherReceiptLineCannotBeSubmittedUnderThisReceipt() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(edit(52L)));
        }
        verify(receiveLineMapper, never()).insertBatch(any());
        verify(baseMapper, never()).updateById(any(WmMaterialReceive.class));
    }

    @Test
    void purchaseSourceCannotBeReboundToAnotherOrder() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        WmMaterialReceiveBo changed = edit(51L);
        changed.setPurId(32L);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(changed));
        }
        verifyNoInteractions(receiveLineMapper, receiveDetailMapper);
    }

    @Test
    void purchaseLineMaterialCannotBeSilentlyChanged() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        WmMaterialReceiveBo changed = edit(51L);
        changed.getWmMaterialReceiveLineList().get(0).setMaterialId(62L);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(changed));
        }
        verify(receiveLineMapper, never()).insertBatch(any());
    }

    @Test
    void newOrMissingPurchaseLineCannotBeInventedThroughEdit() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(edit(null)));
        }
        verify(receiveLineMapper, never()).delete(any(Wrapper.class));
    }

    @Test
    void detailIdFromAnotherLineCannotBeReboundToPurchaseReceipt() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        when(receiveDetailMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(edit(51L)));
        }
        verify(receiveDetailMapper, never()).insertBatch(any());
    }
    @Test
    void purchaseReferenceCodeCannotBeDisplayedAsAnotherPurchaseOrder() {
        WmMaterialReceive head = existing("DRAFT");
        head.setPurCode("CG20260929001");
        when(baseMapper.selectById(21L)).thenReturn(head);
        WmMaterialReceiveBo changed = edit(51L);
        changed.setPurCode("CG20260929002");
        changed.getWmMaterialReceiveLineList().get(0).setWmMaterialReceiveLineDetailList(null);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(changed));
        }
    }

    @Test
    void purchaseReceiptVendorCannotBeReboundToAnotherSupplier() {
        WmMaterialReceive head = existing("DRAFT");
        head.setVendorId(101L);
        when(baseMapper.selectById(21L)).thenReturn(head);
        WmMaterialReceiveBo changed = edit(51L);
        changed.setVendorId(102L);
        changed.getWmMaterialReceiveLineList().get(0).setWmMaterialReceiveLineDetailList(null);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(changed));
        }
    }
    @Test
    void twoSameMaterialRowsKeepDistinctPurchaseOrderLineMappingsDespiteRequestOrder() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        WmMaterialReceiveLine second = originalLine();
        second.setLineId(52L);
        second.setPurchaseLineId(72L);
        when(receiveLineMapper.selectList(any(Wrapper.class)))
            .thenReturn(List.of(originalLine(), second));
        when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(1);
        WmMaterialReceiveBo request = edit(51L);
        WmMaterialReceiveLineBo firstBo = request.getWmMaterialReceiveLineList().get(0);
        firstBo.setWmMaterialReceiveLineDetailList(null);
        WmMaterialReceiveLineBo secondBo = new WmMaterialReceiveLineBo();
        secondBo.setLineId(52L);
        secondBo.setMaterialId(61L);
        secondBo.setQuantityReceived(new BigDecimal("4"));
        request.setWmMaterialReceiveLineList(List.of(secondBo, firstBo));
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertEquals(1, service.updateByBo(request));
        }
        ArgumentCaptor<WmMaterialReceiveLine> saved = ArgumentCaptor.forClass(WmMaterialReceiveLine.class);
        verify(receiveLineMapper, times(2)).updateById(saved.capture());
        assertEquals(52L, saved.getAllValues().get(0).getLineId());
        assertEquals(72L, saved.getAllValues().get(0).getPurchaseLineId());
        assertEquals(51L, saved.getAllValues().get(1).getLineId());
        assertEquals(71L, saved.getAllValues().get(1).getPurchaseLineId());
        verify(receiveLineMapper, never()).insert(any(WmMaterialReceiveLine.class));
    }

    @Test
    void staleDraftStatusOnHeaderUpdateDoesNotModifyReceiptLines() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(0);
        WmMaterialReceiveBo request = edit(51L);
        request.getWmMaterialReceiveLineList().get(0).setWmMaterialReceiveLineDetailList(null);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(request));
        }
        verify(receiveLineMapper, never()).updateById(any(WmMaterialReceiveLine.class));
        verify(receiveDetailMapper, never()).updateById(any(WmMaterialReceiveDetail.class));
    }

    @Test
    void unlinkedReceiptStillAllowsAddingNormalInventoryRows() {
        WmMaterialReceive head = existing("DRAFT");
        head.setPurId(null);
        head.setNoticeId(null);
        when(baseMapper.selectById(21L)).thenReturn(head);
        WmMaterialReceiveLine original = originalLine();
        original.setPurchaseLineId(null);
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(1);
        WmMaterialReceiveBo request = edit(51L);
        request.setPurId(null);
        request.setNoticeId(null);
        WmMaterialReceiveLineBo first = request.getWmMaterialReceiveLineList().get(0);
        first.setWmMaterialReceiveLineDetailList(null);
        WmMaterialReceiveLineBo added = new WmMaterialReceiveLineBo();
        added.setMaterialId(62L);
        added.setQuantityReceived(new BigDecimal("5"));
        request.setWmMaterialReceiveLineList(List.of(first, added));
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertEquals(1, service.updateByBo(request));
        }
        ArgumentCaptor<WmMaterialReceiveLine> newLine = ArgumentCaptor.forClass(WmMaterialReceiveLine.class);
        verify(receiveLineMapper).insert(newLine.capture());
        assertEquals(21L, newLine.getValue().getReceiveId());
        assertNull(newLine.getValue().getPurchaseLineId());
        assertEquals(62L, newLine.getValue().getMaterialId());
        verify(receiveLineMapper).updateById(any(WmMaterialReceiveLine.class));
    }
    @Test
    void unlinkedReceiptCannotGainPurchaseOrderThroughEdit() {
        WmMaterialReceive head = existing("DRAFT");
        head.setPurId(null);
        head.setNoticeId(null);
        when(baseMapper.selectById(21L)).thenReturn(head);
        WmMaterialReceiveBo request = edit(51L);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            ServiceException error =
                    assertThrows(ServiceException.class, () -> service.updateByBo(request));
            assertTrue(error.getMessage().contains("来源"), error.getMessage());
        }
        verifyNoInteractions(receiveLineMapper, receiveDetailMapper);
    }
    @Test
    void approvedShelfDetailsCanBePersistedBeforeConfirmation() {
        WmMaterialReceive head = existing("APPROVED");
        head.setWarehouseId(5L);
        when(baseMapper.selectById(21L)).thenReturn(head);
        when(baseMapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        WmMaterialReceiveLine original = originalLine();
        original.setQuantityReceived(new BigDecimal("3"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        AtomicReference<List<WmMaterialReceiveDetail>> persisted = new AtomicReference<>(List.of());
        when(receiveDetailMapper.selectList(any(Wrapper.class))).thenAnswer(inv -> persisted.get());
        when(receiveDetailMapper.insert(any(WmMaterialReceiveDetail.class))).thenAnswer(inv -> {
            WmMaterialReceiveDetail detail = inv.getArgument(0);
            detail.setDetailId(901L);
            persisted.set(List.of(detail));
            return 1;
        });
        when(baseMapper.update(eq(null), any(Wrapper.class))).thenReturn(1);
        WmMaterialReceiveBo request = edit(51L);
        request.setWarehouseId(5L);
        request.setStatus("APPROVED");
        WmMaterialReceiveDetailBo detail = request.getWmMaterialReceiveLineList().get(0)
            .getWmMaterialReceiveLineDetailList().get(0);
        detail.setDetailId(-1L); /** 页面新增的未保存明细标识 */
        detail.setWarehouseId(5L);
        detail.setLocationId(111L);
        try (MockedStatic<MapstructUtils> mapping = testMapper();
             MockedStatic<LoginHelper> login = mockStatic(LoginHelper.class)) {
            login.when(LoginHelper::getUserId).thenReturn(101L);
            assertTrue(service.updateWithValidation(request));
            assertEquals(901L, persisted.get().get(0).getDetailId());
            assertEquals(21L, persisted.get().get(0).getReceiveId());
            assertEquals(51L, persisted.get().get(0).getLineId());
            assertEquals(111L, persisted.get().get(0).getLocationId());
            assertTrue(service.confirmShelf(21L), "上架确认必须从入库单所属行读到刚保存的明细");
        }
        verify(baseMapper, never()).update(any(WmMaterialReceive.class), any(Wrapper.class));
        verify(receiveLineMapper, never()).updateById(any(WmMaterialReceiveLine.class));
    }

    @Test
    void approvedShelfEditCannotChangeReceiptWarehouseOrLineQuantity() {
        WmMaterialReceive head = existing("APPROVED");
        head.setWarehouseId(5L);
        when(baseMapper.selectById(21L)).thenReturn(head);
        WmMaterialReceiveBo request = edit(51L);
        request.setWarehouseId(6L);
        request.setStatus("APPROVED");
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(request));
        }
        verifyNoInteractions(receiveDetailMapper);
        verify(baseMapper, never()).update(any(WmMaterialReceive.class), any(Wrapper.class));
    }

    @Test
    void changedQuantityWithoutSubmittedDetailsCannotLeaveStaleInventoryQuantity() {
        when(baseMapper.selectById(21L)).thenReturn(existing("DRAFT"));
        WmMaterialReceiveLine original = originalLine();
        original.setQuantityReceived(new BigDecimal("3"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        WmMaterialReceiveDetail stored = new WmMaterialReceiveDetail();
        stored.setDetailId(91L);
        stored.setLineId(51L);
        stored.setReceiveId(21L);
        stored.setQuantity(new BigDecimal("3"));
        lenient().when(receiveDetailMapper.selectList(any(Wrapper.class))).thenReturn(List.of(stored));
        lenient().when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(1);
        WmMaterialReceiveBo request = edit(51L);
        request.getWmMaterialReceiveLineList().get(0).setQuantityReceived(new BigDecimal("5"));
        request.getWmMaterialReceiveLineList().get(0).setWmMaterialReceiveLineDetailList(null);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            ServiceException error =
                    assertThrows(ServiceException.class, () -> service.updateByBo(request));
            assertTrue(error.getMessage().contains("数量"), error.getMessage());
        }
        verify(baseMapper, never()).update(any(WmMaterialReceive.class), any(Wrapper.class));
        verify(receiveLineMapper, never()).updateById(any(WmMaterialReceiveLine.class));
    }

    @Test
    void movedWarehouseMustNotRetainOldLocationIdentity() {
        WmMaterialReceive head = existing("DRAFT");
        head.setWarehouseId(5L);
        head.setAreaId(51L);
        head.setLocationId(511L);
        when(baseMapper.selectById(21L)).thenReturn(head);
        lenient().when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        lenient().when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(1);
        WmMaterialReceiveBo request = edit(51L);
        request.setWarehouseId(6L);
        request.setAreaId(51L); /** 页面修改了仓库，但仍保留旧库区和库位。 */
        request.setLocationId(511L);
        request.getWmMaterialReceiveLineList().get(0).setWmMaterialReceiveLineDetailList(null);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            ServiceException error =
                    assertThrows(ServiceException.class, () -> service.updateByBo(request));
            assertTrue(error.getMessage().contains("库"), error.getMessage());
        }
        verifyNoInteractions(receiveLineMapper, receiveDetailMapper);
    }

    @Test
    void clearedWarehouseLocationMustAppearAsNullInMybatisUpdateSet() {
        WmMaterialReceive head = existing("DRAFT");
        head.setWarehouseId(5L);
        head.setAreaId(51L);
        head.setLocationId(511L);
        when(baseMapper.selectById(21L)).thenReturn(head);
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(originalLine()));
        when(baseMapper.update(any(WmMaterialReceive.class), any(Wrapper.class))).thenReturn(1);
        WmMaterialReceiveBo request = edit(51L);
        request.setWarehouseId(6L);
        request.setAreaId(null);
        request.setLocationId(null);
        request.getWmMaterialReceiveLineList().get(0).setWmMaterialReceiveLineDetailList(null);
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertEquals(1, service.updateByBo(request));
        }
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Wrapper<WmMaterialReceive>> captured = ArgumentCaptor.forClass(Wrapper.class);
        ArgumentCaptor<WmMaterialReceive> entity = ArgumentCaptor.forClass(WmMaterialReceive.class);
        verify(baseMapper).update(entity.capture(), captured.capture());
        /** 真正解析 MyBatis-Plus 的 update MappedStatement/BoundSql，不把实体中的 null 当作 SQL 清空证据。 */
        MybatisConfiguration sqlConfiguration = new MybatisConfiguration();
        sqlConfiguration.addMapper(WmMaterialReceiveMapper.class);
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("et", entity.getValue());
        parameters.put("ew", captured.getValue());
        BoundSql bound = sqlConfiguration.getMappedStatement(WmMaterialReceiveMapper.class.getName() + ".update")
            .getBoundSql(parameters);
        assertSqlNullAssignment(sqlConfiguration, bound, parameters, "area_id");
        assertSqlNullAssignment(sqlConfiguration, bound, parameters, "location_id");
    }

    private void assertSqlNullAssignment(MybatisConfiguration configuration, BoundSql bound,
                                         Map<String, Object> parameters, String column) {
        var assignment = Pattern.compile("\\b" + column + "\\s*=\\s*\\?").matcher(bound.getSql());
        assertTrue(assignment.find(), "SQL 未显式清空 " + column + ": " + bound.getSql());
        int valuePosition = assignment.end() - 1;
        int parameterIndex = (int) bound.getSql().substring(0, valuePosition).chars().filter(c -> c == '?').count();
        String property = bound.getParameterMappings().get(parameterIndex).getProperty();
        Object value = bound.hasAdditionalParameter(property) ? bound.getAdditionalParameter(property)
            : configuration.newMetaObject(parameters).getValue(property);
        assertNull(value, column + " 的 SQL 绑定值必须为 NULL");
    }

    @Test
    void approvedShelfCannotChangeLineQuantityOrMaterial() {
        WmMaterialReceive head = existing("APPROVED");
        head.setWarehouseId(5L);
        when(baseMapper.selectById(21L)).thenReturn(head);
        WmMaterialReceiveLine original = originalLine();
        original.setQuantityReceived(new BigDecimal("3"));
        when(receiveLineMapper.selectList(any(Wrapper.class))).thenReturn(List.of(original));
        WmMaterialReceiveBo request = edit(51L);
        request.setWarehouseId(5L);
        request.setStatus("APPROVED");
        request.getWmMaterialReceiveLineList().get(0).setQuantityReceived(new BigDecimal("5"));
        try (MockedStatic<MapstructUtils> mapping = testMapper()) {
            assertThrows(ServiceException.class, () -> service.updateByBo(request));
        }
        verify(baseMapper, never()).update(any(WmMaterialReceive.class), any(Wrapper.class));
        verifyNoInteractions(receiveDetailMapper);
    }

}

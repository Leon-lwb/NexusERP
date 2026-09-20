package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.core.domain.ActStatus;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.domain.vo.SoContractLineVo;
import com.nexus.erp.so.domain.vo.SoContractVo;
import com.nexus.erp.so.mapper.SoContractLineMapper;
import com.nexus.erp.so.mapper.SoContractMapper;
import com.nexus.erp.so.service.ISoContractService;
import com.nexus.erp.so.service.impl.SoContractServiceImpl;
import com.nexus.erp.so.service.impl.SoContractSignStatusFiller;
import com.nexus.workflow.domain.WfMyBusiness;
import com.nexus.workflow.service.IWfProcessService;
import com.nexus.workflow.service.impl.WfMyBusinessServiceImpl;
import io.github.linpeilie.Converter;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.AfterAll;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 销售合同 Service 单元测试。
 * <p>覆盖安全加固点：新增/修改时状态由服务端强制（防客户端直发 EFFECTIVE 绕过审批）、
 * 明细行白名单重建（防 lineId/tenantId/delFlag/已发货已开票进度字段批量赋值）与
 * 行金额/税额服务端重算、删除的条件更新（CAS）、submit 的 ID 解析与状态守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class SoContractServiceImplTest {

    @Mock
    private SoContractMapper baseMapper;
    @Mock
    private SoContractLineMapper lineMapper;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @Mock
    private SoContractSignStatusFiller signStatusFiller;

    @InjectMocks
    private SoContractServiceImpl contractService;

    /** 静态 mock：MapstructUtils.CONVERTER 依赖 SpringUtils.getBean(Converter)，纯 Mockito 下注入反射实现 */
    private static MockedStatic<SpringUtil> springUtilMock;

    @BeforeAll
    static void initStatics() {
        springUtilMock = mockStatic(SpringUtil.class);
        Converter converter = new Converter() {
            @Override
            public <S, T> T convert(S source, Class<T> targetType) {
                return BeanUtil.copyProperties(source, targetType);
            }
        };
        lenient().when(SpringUtil.getBean(Converter.class)).thenReturn(converter);

        // 预热 MyBatis-Plus Lambda 缓存（删除/更新条件构造需要解析实体列名）
        MybatisConfiguration configuration = new MybatisConfiguration();
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(configuration, "");
        TableInfoHelper.initTableInfo(assistant, SoContract.class);
        TableInfoHelper.initTableInfo(assistant, SoContractLine.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private SoContractLine tamperedLine() {
        SoContractLine line = new SoContractLine();
        line.setLineId(999L);
        line.setTenantId("888888");
        line.setDelFlag("1");
        line.setCreateBy(1L);
        line.setDeliveredQty(new BigDecimal("999"));
        line.setInvoicedQty(new BigDecimal("999"));
        line.setLineStatus("DONE");
        line.setMaterialCode("M-SO-001");
        line.setUnitPrice(new BigDecimal("100"));
        line.setQuantity(new BigDecimal("2"));
        line.setTaxRate(new BigDecimal("13"));
        line.setLineAmount(new BigDecimal("999999"));
        line.setSortOrder(1);
        return line;
    }

    /** 新增：客户端伪造 status=EFFECTIVE 仍落库为 DRAFT；明细行白名单重建并重算行金额/税额/合同总额 */
    @Test
    void insertByBo_forcesDraft_andSanitizesLines() {
        SoContractBo bo = new SoContractBo();
        bo.setContractNo("SO-HT-001");
        bo.setContractName("设备销售合同");
        bo.setClientId(1L);
        bo.setStatus("EFFECTIVE");
        bo.setSoContractLineList(List.of(tamperedLine()));

        when(baseMapper.insert(any(SoContract.class))).thenAnswer(inv -> {
            inv.getArgument(0, SoContract.class).setContractId(123L);
            return 1;
        });

        assertEquals(1, contractService.insertByBo(bo));

        ArgumentCaptor<SoContract> headerCaptor = ArgumentCaptor.forClass(SoContract.class);
        verify(baseMapper).insert(headerCaptor.capture());
        SoContract inserted = headerCaptor.getValue();
        assertEquals(ISoContractService.STATUS_DRAFT, inserted.getStatus());
        assertEquals(0, new BigDecimal("200.00").compareTo(inserted.getTotalAmount()));

        ArgumentCaptor<SoContractLine> lineCaptor = ArgumentCaptor.forClass(SoContractLine.class);
        verify(lineMapper).insert(lineCaptor.capture());
        SoContractLine line = lineCaptor.getValue();
        // 白名单重建：ID/租户/逻辑删除/审计/执行进度字段全部丢弃，contractId 由头表回填
        assertNull(line.getLineId());
        assertNull(line.getTenantId());
        assertNull(line.getDelFlag());
        assertNull(line.getCreateBy());
        assertNull(line.getDeliveredQty());
        assertNull(line.getInvoicedQty());
        assertNull(line.getLineStatus());
        assertEquals(123L, line.getContractId());
        // 行金额=单价×数量、税额=行金额×税率÷100，均以服务端计算为准
        assertEquals(0, new BigDecimal("200.00").compareTo(line.getLineAmount()));
        assertEquals(0, new BigDecimal("26.00").compareTo(line.getTaxAmount()));
        assertEquals("M-SO-001", line.getMaterialCode());
    }

    /** 新增：负数单价直接拒绝 */
    @Test
    void insertByBo_negativePrice_rejected() {
        SoContractBo bo = new SoContractBo();
        bo.setContractNo("SO-HT-002");
        SoContractLine line = new SoContractLine();
        line.setUnitPrice(new BigDecimal("-1"));
        bo.setSoContractLineList(List.of(line));

        assertThrows(ServiceException.class, () -> contractService.insertByBo(bo));
        verify(baseMapper, never()).insert(any(SoContract.class));
    }

    /** 新增：税率越界（>100）直接拒绝 */
    @Test
    void insertByBo_taxRateOutOfRange_rejected() {
        SoContractBo bo = new SoContractBo();
        bo.setContractNo("SO-HT-004");
        SoContractLine line = new SoContractLine();
        line.setTaxRate(new BigDecimal("130"));
        bo.setSoContractLineList(List.of(line));

        assertThrows(ServiceException.class, () -> contractService.insertByBo(bo));
        verify(baseMapper, never()).insert(any(SoContract.class));
    }

    /** 修改：客户端携带 status=EFFECTIVE 也只沿用库中现值（DRAFT） */
    @Test
    void updateByBo_keepsExistingStatus() {
        SoContract existing = new SoContract();
        existing.setContractId(7L);
        existing.setStatus(ISoContractService.STATUS_DRAFT);
        when(baseMapper.selectById(7L)).thenReturn(existing);
        when(baseMapper.updateById(any(SoContract.class))).thenReturn(1);

        SoContractBo bo = new SoContractBo();
        bo.setContractId(7L);
        bo.setContractNo("SO-HT-003");
        bo.setStatus("EFFECTIVE");
        bo.setSoContractLineList(List.of(tamperedLine()));

        assertEquals(1, contractService.updateByBo(bo));

        ArgumentCaptor<SoContract> captor = ArgumentCaptor.forClass(SoContract.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(ISoContractService.STATUS_DRAFT, captor.getValue().getStatus());
        verify(lineMapper).delete(any());
        verify(lineMapper).insert(any(SoContractLine.class));
    }

    /** 修改：合同不存在时明确报错（原实现静默返回成功） */
    @Test
    void updateByBo_missingContract_throws() {
        when(baseMapper.selectById(404L)).thenReturn(null);
        SoContractBo bo = new SoContractBo();
        bo.setContractId(404L);
        assertThrows(ServiceException.class, () -> contractService.updateByBo(bo));
        verify(baseMapper, never()).updateById(any(SoContract.class));
    }

    /** 修改：已生效合同不可改（回归保护） */
    @Test
    void updateByBo_effectiveContract_throws() {
        SoContract existing = new SoContract();
        existing.setContractId(8L);
        existing.setStatus(ISoContractService.STATUS_EFFECTIVE);
        when(baseMapper.selectById(8L)).thenReturn(existing);

        SoContractBo bo = new SoContractBo();
        bo.setContractId(8L);
        assertThrows(ServiceException.class, () -> contractService.updateByBo(bo));
    }

    /** 删除：先查是草稿、条件删除影响 0 行（并发状态流转）时抛异常且不动明细行 */
    @Test
    void deleteByIds_casMiss_throws() {
        SoContract existing = new SoContract();
        existing.setContractId(9L);
        existing.setContractNo("SO-HT-009");
        existing.setStatus(ISoContractService.STATUS_DRAFT);
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(baseMapper.delete(any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> contractService.deleteByIds(new Long[] {9L}));
        verify(lineMapper, never()).delete(any());
    }

    /** 删除：非草稿直接拒绝 */
    @Test
    void deleteByIds_nonDraft_throws() {
        SoContract existing = new SoContract();
        existing.setContractId(10L);
        existing.setStatus(ISoContractService.STATUS_EFFECTIVE);
        when(baseMapper.selectById(10L)).thenReturn(existing);

        assertThrows(ServiceException.class, () -> contractService.deleteByIds(new Long[] {10L}));
        verify(baseMapper, never()).delete(any());
    }

    /** 提交审批：ID 非数字给业务异常而不是 NumberFormatException 500 */
    @Test
    void submit_malformedId_throws() {
        assertThrows(ServiceException.class, () -> contractService.submit("not-a-number", "1"));
    }

    /** 提交审批：已生效合同不允许再发起流程 */
    @Test
    void submit_effectiveContract_rejected() {
        SoContract existing = new SoContract();
        existing.setContractId(11L);
        existing.setStatus(ISoContractService.STATUS_EFFECTIVE);
        when(baseMapper.selectById(11L)).thenReturn(existing);

        assertThrows(ServiceException.class, () -> contractService.submit("11", "1"));
        verify(processService, never()).startProcessByDataId(anyString(), anyString(), anyString(), any());
    }

    /** 提交审批：草稿 + 已保存流程 → 正常发起 */
    @Test
    void submit_draftContract_startsProcess() {
        SoContract existing = new SoContract();
        existing.setContractId(12L);
        existing.setContractNo("SO-HT-012");
        existing.setStatus(ISoContractService.STATUS_DRAFT);
        existing.setSelectFlowId(55L);
        when(baseMapper.selectById(12L)).thenReturn(existing);
        when(processService.startProcessByDataId(eq("12"), eq("55"), eq("soContractService"), any()))
            .thenReturn(R.ok());

        assertTrue(contractService.submit("12", ""));
        verify(processService).startProcessByDataId(eq("12"), eq("55"), eq("soContractService"), any());
    }

    // ==================== selectById 明细行转 VO（批次 5）====================

    /**
     * 详情返回明细行 VO 而非实体：不暴露 tenantId/delFlag 等审计字段，
     * 同时保留合同表单实际绑定的字段（数量/单价/税率/税额/交货日期等）。
     */
    @Test
    void selectById_returnsLineVoKeepingFormFields() {
        SoContractVo header = new SoContractVo();
        header.setContractId(1L);
        when(baseMapper.selectVoById(1L)).thenReturn(header);

        SoContractLine line = new SoContractLine();
        line.setLineId(10L);
        line.setContractId(1L);
        line.setMaterialId(100L);
        line.setMaterialCode("M-001");
        line.setMaterialName("测试物料");
        line.setQuantity(new BigDecimal("2"));
        line.setUnit("台");
        line.setUnitPrice(new BigDecimal("100"));
        line.setLineAmount(new BigDecimal("200.00"));
        line.setTaxRate(new BigDecimal("13"));
        line.setTaxAmount(new BigDecimal("26.00"));
        line.setDeliveryDate(new Date());
        line.setSortOrder(1);
        line.setTenantId("888888");
        line.setDelFlag("1");
        when(lineMapper.selectList(any())).thenReturn(List.of(line));

        SoContractVo result = contractService.selectById(1L);

        assertEquals(1, result.getSoContractLineList().size());
        SoContractLineVo lineVo = result.getSoContractLineList().get(0);
        assertEquals("M-001", lineVo.getMaterialCode());
        assertEquals("测试物料", lineVo.getMaterialName());
        assertEquals(0, new BigDecimal("2").compareTo(lineVo.getQuantity()));
        assertEquals("台", lineVo.getUnit());
        assertEquals(0, new BigDecimal("200.00").compareTo(lineVo.getLineAmount()));
        assertEquals(0, new BigDecimal("13").compareTo(lineVo.getTaxRate()));
        assertEquals(0, new BigDecimal("26.00").compareTo(lineVo.getTaxAmount()));
        assertNotNull(lineVo.getDeliveryDate());
        assertEquals(1, lineVo.getSortOrder());
    }

    // ==================== 流程回调守卫（加固轮）====================

    /** 流程回调改为条件更新（不再无条件 updateById） */
    @Test
    void afterFlowHandle_usesConditionalUpdate() {
        SoContract contract = new SoContract();
        contract.setContractId(1L);
        contract.setStatus(ISoContractService.STATUS_APPROVING);
        when(baseMapper.selectById(1L)).thenReturn(contract);
        when(baseMapper.update(any(), any())).thenReturn(1);

        contractService.afterFlowHandle(flowBusiness(ActStatus.pass));

        ArgumentCaptor<Wrapper<SoContract>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        verify(baseMapper, never()).updateById(any(SoContract.class));
        assertTrue(captor.getValue().getSqlSegment().contains("status"), "应为带状态条件的更新");
    }

    /**
     * 已生效（EFFECTIVE）的合同不会被流程撤销回调改回 DRAFT ——
     * 否则已生效合同会重新变成可编辑/可删除状态。
     */
    @Test
    void afterFlowHandle_doesNotRevertEffectiveContract() {
        SoContract contract = new SoContract();
        contract.setContractId(1L);
        contract.setStatus(ISoContractService.STATUS_EFFECTIVE);
        when(baseMapper.selectById(1L)).thenReturn(contract);
        when(baseMapper.update(any(), any())).thenReturn(0);

        assertDoesNotThrow(() -> contractService.afterFlowHandle(flowBusiness(ActStatus.revoke)));

        verify(baseMapper, never()).updateById(any(SoContract.class));
    }

    /** 未知流程动作不产生任何更新 */
    @Test
    void afterFlowHandle_unknownAction_doesNothing() {
        SoContract contract = new SoContract();
        contract.setContractId(1L);
        contract.setStatus(ISoContractService.STATUS_DRAFT);
        when(baseMapper.selectById(1L)).thenReturn(contract);

        contractService.afterFlowHandle(flowBusiness("未知动作"));

        verify(baseMapper, never()).update(any(), any());
    }

    private WfMyBusiness flowBusiness(String actStatus) {
        WfMyBusiness business = new WfMyBusiness();
        business.setDataId("1");
        business.setActStatus(actStatus);
        return business;
    }

    // ==================== 合同终止（Q9）====================

    private SoContract contract(Long id, String status, String contractNo) {
        SoContract c = new SoContract();
        c.setContractId(id);
        c.setContractNo(contractNo);
        c.setStatus(status);
        return c;
    }

    /** 仅已生效的合同可终止；草稿直接拒绝 */
    @Test
    void terminate_rejectsNonEffective() {
        when(baseMapper.selectById(1L))
            .thenReturn(contract(1L, ISoContractService.STATUS_DRAFT, "HT-001"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.terminate(1L, "双方协商解除"));
        assertTrue(ex.getMessage().contains("仅已生效"), "实际: " + ex.getMessage());
        verify(baseMapper, never()).update(any(), any());
    }

    /** 重复终止给出明确提示而不是静默成功 */
    @Test
    void terminate_rejectsAlreadyTerminated() {
        when(baseMapper.selectById(1L))
            .thenReturn(contract(1L, ISoContractService.STATUS_TERMINATED, "HT-001"));

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.terminate(1L, "再次终止"));
        assertTrue(ex.getMessage().contains("已终止"), "实际: " + ex.getMessage());
    }

    /** 合同不存在 */
    @Test
    void terminate_rejectsMissingContract() {
        when(baseMapper.selectById(1L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> contractService.terminate(1L, "原因"));
    }

    /** 终止成功：状态置 TERMINATED，原因写入备注，且用条件更新做 CAS */
    @Test
    void terminate_success_setsTerminatedAndAppendsReason() {
        SoContract existing = contract(1L, ISoContractService.STATUS_EFFECTIVE, "HT-001");
        existing.setRemark("原备注");
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.update(any(), any())).thenReturn(1);

        contractService.terminate(1L, "双方协商解除");

        ArgumentCaptor<Wrapper<SoContract>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        verify(baseMapper, never()).updateById(any(SoContract.class));
        // 条件更新必须带 status 条件（CAS），防并发把非 EFFECTIVE 的合同也改掉
        assertTrue(captor.getValue().getSqlSegment().contains("status"), "应带状态条件: " + captor.getValue().getSqlSegment());
    }

    /** 并发已被他人终止（条件更新 0 行）时抛异常，不静默成功 */
    @Test
    void terminate_concurrentChangeThrows() {
        when(baseMapper.selectById(1L))
            .thenReturn(contract(1L, ISoContractService.STATUS_EFFECTIVE, "HT-001"));
        when(baseMapper.update(any(), any())).thenReturn(0);

        ServiceException ex = assertThrows(ServiceException.class,
            () -> contractService.terminate(1L, "原因"));
        assertTrue(ex.getMessage().contains("状态已变更"), "实际: " + ex.getMessage());
    }

    /** 到期扫描：条件更新一次性把过期 EFFECTIVE 合同置 EXPIRED */
    @Test
    void expireOverdueContracts_updatesOnlyEffectiveWithExpiryDate() {
        when(baseMapper.update(any(), any())).thenReturn(3);

        int updated = contractService.expireOverdueContracts();

        assertEquals(3, updated);
        ArgumentCaptor<Wrapper<SoContract>> captor = ArgumentCaptor.forClass(Wrapper.class);
        verify(baseMapper).update(any(), captor.capture());
        String segment = captor.getValue().getSqlSegment();
        // 只动 EFFECTIVE、且必须有失效日期、且严格小于今天
        assertTrue(segment.contains("status"), "应限定状态: " + segment);
        assertTrue(segment.contains("expiry_date"), "应限定失效日期: " + segment);
        assertTrue(segment.contains("<"), "应为严格小于（当天仍有效）: " + segment);
    }

    /** 到期扫描无命中时不报错 */
    @Test
    void expireOverdueContracts_noMatchReturnsZero() {
        when(baseMapper.update(any(), any())).thenReturn(0);
        assertEquals(0, contractService.expireOverdueContracts());
    }
}

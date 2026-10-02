package com.nexus.erp.pu;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.pu.domain.PuContract;
import com.nexus.erp.pu.domain.PuContractLine;
import com.nexus.erp.pu.domain.bo.PuContractBo;
import com.nexus.erp.pu.mapper.PuContractLineMapper;
import com.nexus.erp.pu.mapper.PuContractMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderLineMapper;
import com.nexus.erp.pu.mapper.PuPurchaseOrderMapper;
import com.nexus.erp.pu.service.IPuContractService;
import com.nexus.erp.pu.service.impl.PuContractServiceImpl;
import com.nexus.erp.pu.service.impl.PuContractSignStatusFiller;
import com.nexus.system.strategy.AutoCodeUtil;
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
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 采购合同 Service 单元测试。
 * <p>覆盖安全加固点：新增/修改时状态由服务端强制（防客户端直发 APPROVED 绕过审批）、
 * 明细行白名单重建（防 lineId/tenantId/delFlag 批量赋值）与行金额服务端重算、
 * 删除的条件更新（CAS）、submit 的 ID 解析与状态守卫。</p>
 */
@ExtendWith(MockitoExtension.class)
@Tag("dev")
class PuContractServiceImplTest {

    @Mock
    private PuContractMapper baseMapper;
    @Mock
    private PuContractLineMapper lineMapper;
    @Mock
    private PuPurchaseOrderMapper purchaseOrderMapper;
    @Mock
    private PuPurchaseOrderLineMapper purchaseOrderLineMapper;
    @Mock
    private AutoCodeUtil autoCodeUtil;
    @Mock
    private IWfProcessService processService;
    @Mock
    private WfMyBusinessServiceImpl wfMyBusinessServiceImpl;
    @Mock
    private FlowableProcInsIdHelper procInsIdHelper;
    @Mock
    private PuContractSignStatusFiller signStatusFiller;

    @InjectMocks
    private PuContractServiceImpl contractService;

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
        TableInfoHelper.initTableInfo(assistant, PuContract.class);
        TableInfoHelper.initTableInfo(assistant, PuContractLine.class);
    }

    @AfterAll
    static void closeStatics() {
        if (springUtilMock != null) {
            springUtilMock.close();
        }
    }

    private PuContractLine tamperedLine() {
        PuContractLine line = new PuContractLine();
        line.setLineId(999L);
        line.setTenantId("888888");
        line.setDelFlag("1");
        line.setCreateBy(1L);
        line.setMaterialCode("M-001");
        line.setUnitPrice(new BigDecimal("10.5000"));
        line.setQuantity(new BigDecimal("2"));
        line.setTaxRate(new BigDecimal("13"));
        line.setLineAmount(new BigDecimal("999999"));
        line.setSortOrder(1);
        return line;
    }

    /** 新增：客户端伪造 status=APPROVED 仍落库为 DRAFT；明细行被白名单重建并重算行金额/税额/合同总额 */
    @Test
    void approvedContractCannotBypassSourceCertificationByDirectlyInsertingAnOrder() {
        PuContract contract = new PuContract(); contract.setContractId(10L); contract.setStatus("APPROVED");
        PuContractLine line = new PuContractLine(); line.setMaterialId(101L); line.setQuantity(new java.math.BigDecimal("5"));
        lenient().when(baseMapper.selectById(10L)).thenReturn(contract);
        lenient().when(lineMapper.selectList(any())).thenReturn(java.util.List.of(line));
        lenient().when(baseMapper.update(any(),any())).thenReturn(1);
        ServiceException error=assertThrows(ServiceException.class,()->contractService.createPurchaseOrderFromContract(10L));
        assertTrue(error.getMessage().contains("/erp/pu/requisition/certified"),error.getMessage());
        org.mockito.Mockito.verifyNoInteractions(baseMapper,lineMapper,purchaseOrderMapper,purchaseOrderLineMapper,autoCodeUtil);
    }

    @Test
    void insertByBo_forcesDraft_andSanitizesLines() {
        PuContractBo bo = new PuContractBo();
        bo.setContractNo("PU-HT-001");
        bo.setContractName("钢材年度框架合同");
        bo.setVendorId(1L);
        bo.setStatus("APPROVED");
        PuContractLine tampered = tamperedLine();
        PuContractLine second = new PuContractLine();
        second.setUnitPrice(new BigDecimal("3.3333"));
        second.setQuantity(new BigDecimal("3"));
        bo.setPuContractLineList(List.of(tampered, second));

        when(baseMapper.insert(any(PuContract.class))).thenAnswer(inv -> {
            inv.getArgument(0, PuContract.class).setContractId(123L);
            return 1;
        });

        assertEquals(1, contractService.insertByBo(bo));

        ArgumentCaptor<PuContract> headerCaptor = ArgumentCaptor.forClass(PuContract.class);
        verify(baseMapper).insert(headerCaptor.capture());
        PuContract inserted = headerCaptor.getValue();
        assertEquals(IPuContractService.STATUS_DRAFT, inserted.getStatus());
        assertEquals(0, new BigDecimal("30.9999").compareTo(inserted.getTotalAmount()));
        // 税额合计/不含税合计按行汇总：仅第一行有税率（21×13%）
        assertEquals(0, new BigDecimal("2.73").compareTo(inserted.getTaxAmount()));
        assertEquals(0, new BigDecimal("18.27").compareTo(inserted.getAmountExcludingTax()));

        ArgumentCaptor<PuContractLine> lineCaptor = ArgumentCaptor.forClass(PuContractLine.class);
        verify(lineMapper, times(2)).insert(lineCaptor.capture());
        List<PuContractLine> lines = lineCaptor.getAllValues();
        // 白名单重建：ID/租户/逻辑删除/审计字段全部丢弃，contractId 由头表回填
        for (PuContractLine line : lines) {
            assertNull(line.getLineId());
            assertNull(line.getTenantId());
            assertNull(line.getDelFlag());
            assertNull(line.getCreateBy());
            assertEquals(123L, line.getContractId());
        }
        // 行金额按 单价×数量 服务端重算，不信任客户端的 999999；税率行同时重算税额与不含税金额
        assertEquals(0, new BigDecimal("21.0000").compareTo(lines.get(0).getLineAmount()));
        assertEquals(0, new BigDecimal("2.73").compareTo(lines.get(0).getTaxAmount()));
        assertEquals(0, new BigDecimal("18.27").compareTo(lines.get(0).getLineAmountExcludingTax()));
        assertEquals(0, new BigDecimal("9.9999").compareTo(lines.get(1).getLineAmount()));
        assertNull(lines.get(1).getTaxAmount());
        assertEquals("M-001", lines.get(0).getMaterialCode());
    }

    /** 新增：税率越界（>100）直接拒绝 */
    @Test
    void insertByBo_taxRateOutOfRange_rejected() {
        PuContractBo bo = new PuContractBo();
        bo.setContractNo("PU-HT-005");
        PuContractLine line = new PuContractLine();
        line.setTaxRate(new BigDecimal("130"));
        bo.setPuContractLineList(List.of(line));

        assertThrows(ServiceException.class, () -> contractService.insertByBo(bo));
        verify(baseMapper, never()).insert(any(PuContract.class));
    }

    /** 新增：负数数量直接拒绝 */
    @Test
    void insertByBo_negativeQuantity_rejected() {
        PuContractBo bo = new PuContractBo();
        bo.setContractNo("PU-HT-002");
        PuContractLine line = new PuContractLine();
        line.setQuantity(new BigDecimal("-1"));
        bo.setPuContractLineList(List.of(line));

        assertThrows(ServiceException.class, () -> contractService.insertByBo(bo));
        verify(baseMapper, never()).insert(any(PuContract.class));
    }

    /** 修改：客户端携带 status=APPROVED 也只沿用库中现值（DRAFT） */
    @Test
    void updateByBo_keepsExistingStatus() {
        PuContract existing = new PuContract();
        existing.setContractId(7L);
        existing.setStatus(IPuContractService.STATUS_DRAFT);
        when(baseMapper.selectById(7L)).thenReturn(existing);
        when(baseMapper.updateById(any(PuContract.class))).thenReturn(1);

        PuContractBo bo = new PuContractBo();
        bo.setContractId(7L);
        bo.setContractNo("PU-HT-003");
        bo.setStatus("APPROVED");
        bo.setPuContractLineList(List.of(tamperedLine()));

        assertEquals(1, contractService.updateByBo(bo));

        ArgumentCaptor<PuContract> captor = ArgumentCaptor.forClass(PuContract.class);
        verify(baseMapper).updateById(captor.capture());
        assertEquals(IPuContractService.STATUS_DRAFT, captor.getValue().getStatus());
        verify(lineMapper).delete(any());
        verify(lineMapper).insert(any(PuContractLine.class));
    }

    /** 修改：合同不存在时明确报错（原实现静默返回成功） */
    @Test
    void updateByBo_missingContract_throws() {
        when(baseMapper.selectById(404L)).thenReturn(null);
        PuContractBo bo = new PuContractBo();
        bo.setContractId(404L);
        assertThrows(ServiceException.class, () -> contractService.updateByBo(bo));
        verify(baseMapper, never()).updateById(any(PuContract.class));
    }

    /** 修改：非草稿/驳回状态不可改（回归保护） */
    @Test
    void updateByBo_approvedContract_throws() {
        PuContract existing = new PuContract();
        existing.setContractId(8L);
        existing.setStatus(IPuContractService.STATUS_APPROVED);
        when(baseMapper.selectById(8L)).thenReturn(existing);

        PuContractBo bo = new PuContractBo();
        bo.setContractId(8L);
        assertThrows(ServiceException.class, () -> contractService.updateByBo(bo));
    }

    /** 删除：先查是草稿、条件删除影响 0 行（并发状态流转）时抛异常且不动明细行 */
    @Test
    void deleteByIds_casMiss_throws() {
        PuContract existing = new PuContract();
        existing.setContractId(9L);
        existing.setContractNo("PU-HT-009");
        existing.setStatus(IPuContractService.STATUS_DRAFT);
        when(baseMapper.selectById(9L)).thenReturn(existing);
        when(baseMapper.delete(any())).thenReturn(0);

        assertThrows(ServiceException.class, () -> contractService.deleteByIds(new Long[] {9L}));
        verify(lineMapper, never()).delete(any());
    }

    /** 删除：非草稿直接拒绝 */
    @Test
    void deleteByIds_nonDraft_throws() {
        PuContract existing = new PuContract();
        existing.setContractId(10L);
        existing.setStatus(IPuContractService.STATUS_EXECUTING);
        when(baseMapper.selectById(10L)).thenReturn(existing);

        assertThrows(ServiceException.class, () -> contractService.deleteByIds(new Long[] {10L}));
        verify(baseMapper, never()).delete(any());
    }

    /** 提交审批：ID 非数字给业务异常而不是 NumberFormatException 500 */
    @Test
    void submit_malformedId_throws() {
        assertThrows(ServiceException.class, () -> contractService.submit("not-a-number", "1"));
    }

    /** 提交审批：已批准合同不允许重复提交发起流程 */
    @Test
    void submit_approvedContract_rejected() {
        PuContract existing = new PuContract();
        existing.setContractId(11L);
        existing.setStatus(IPuContractService.STATUS_APPROVED);
        when(baseMapper.selectById(11L)).thenReturn(existing);

        assertThrows(ServiceException.class, () -> contractService.submit("11", "1"));
        verify(processService, never()).startProcessByDataId(anyString(), anyString(), anyString(), any());
    }

    /** 提交审批：草稿 + 已保存流程 → 正常发起 */
    @Test
    void submit_draftContract_startsProcess() {
        PuContract existing = new PuContract();
        existing.setContractId(12L);
        existing.setContractNo("PU-HT-012");
        existing.setStatus(IPuContractService.STATUS_DRAFT);
        existing.setSelectFlowId(55L);
        when(baseMapper.selectById(12L)).thenReturn(existing);
        when(processService.startProcessByDataId(eq("12"), eq("55"), eq("puContractService"), any()))
            .thenReturn(R.ok());

        assertTrue(contractService.submit("12", ""));
        verify(processService).startProcessByDataId(eq("12"), eq("55"), eq("puContractService"), any(HashMap.class));
    }
}

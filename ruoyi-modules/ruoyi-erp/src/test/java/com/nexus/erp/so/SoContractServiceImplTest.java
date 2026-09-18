package com.nexus.erp.so;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.domain.R;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.flowable.helper.FlowableProcInsIdHelper;
import com.nexus.erp.so.domain.SoContract;
import com.nexus.erp.so.domain.SoContractLine;
import com.nexus.erp.so.domain.bo.SoContractBo;
import com.nexus.erp.so.mapper.SoContractLineMapper;
import com.nexus.erp.so.mapper.SoContractMapper;
import com.nexus.erp.so.service.ISoContractService;
import com.nexus.erp.so.service.impl.SoContractServiceImpl;
import com.nexus.erp.so.service.impl.SoContractSignStatusFiller;
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
}

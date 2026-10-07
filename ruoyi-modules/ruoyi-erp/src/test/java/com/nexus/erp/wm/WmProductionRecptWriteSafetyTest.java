package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.mapper.WmProductionRecptMapper;
import com.nexus.erp.wm.service.IWmBatchService;
import com.nexus.erp.wm.service.IWmStockOperationService;
import com.nexus.erp.wm.service.impl.WmProductionRecptServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.nexus.common.tenant.helper.TenantHelper;
import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.mapper.WmProductionRecptGuardMapper;
import com.nexus.erp.wm.service.WmProductionRecptWriteGuard;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import javax.sql.DataSource;
import java.sql.Connection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 普通生产入库写入口回归；只使用本地模拟依赖，不连接或改写业务数据库。 */
class WmProductionRecptWriteSafetyTest {
    private WmProductionRecptMapper mapper;
    private IWmStockOperationService stock;
    private IWmBatchService batches;
    private AutoCodeUtil codes;
    private WmProductionRecptServiceImpl service;
    private WmProductionRecpt original;
    private WmProductionRecptGuardMapper guardMapper;
    private PrdWorkOrder workOrder;
    private MockedStatic<TenantHelper> tenant;

    @BeforeAll
    static void tableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmProductionRecpt.class);
    }

    @BeforeEach
    void before() throws Exception {
        mapper = mock(WmProductionRecptMapper.class);
        stock = mock(IWmStockOperationService.class);
        batches = mock(IWmBatchService.class);
        codes = mock(AutoCodeUtil.class);
        guardMapper = mock(WmProductionRecptGuardMapper.class);
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        DynamicDataSourceContextHolder.push("erp");
        DataSource ds = mock(DataSource.class); Connection connection = mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        TransactionInterceptor interceptor = new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(ds));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy = new ProxyFactory(new WmProductionRecptServiceImpl(mapper, stock, codes, batches,
            new WmProductionRecptWriteGuard(guardMapper)));
        proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor);
        service = (WmProductionRecptServiceImpl) proxy.getProxy();
        original = new WmProductionRecpt();
        original.setTenantId("000000"); original.setDelFlag("0");
        original.setRecptId(101L); original.setRecptCode("SCRK202610070999");
        original.setWoId(201L); original.setWoNo("SCGD202610070099");
        original.setMaterialId(301L); original.setMaterialCode("IF20261007099");
        original.setQuantity(new BigDecimal("6")); original.setWarehouseId(3L); original.setLocationId(28L);
        original.setStatus("DRAFT"); original.setRecptDate(new Date());
        workOrder = new PrdWorkOrder(); workOrder.setTenantId("000000"); workOrder.setDelFlag("0");
        workOrder.setWoId(201L); workOrder.setWoNo(original.getWoNo()); workOrder.setMaterialId(301L);
        workOrder.setMaterialCode(original.getMaterialCode()); workOrder.setUnitId(26L);
        workOrder.setWoStatus("COMPLETED"); workOrder.setCompletedQty(new BigDecimal("6"));
        workOrder.setPlannedQty(new BigDecimal("6")); workOrder.setActualEndDate(new Date());
        when(guardMapper.findWorkOrder("000000", 101L)).thenReturn(201L);
        when(guardMapper.lockWorkOrder("000000", 201L)).thenReturn(workOrder);
        when(guardMapper.lockReceipts("000000", 201L)).thenAnswer(call -> List.of(original));
        when(guardMapper.lockTransactions("000000", 201L)).thenReturn(List.of());
        when(guardMapper.approvedReportedQuantity("000000", 201L)).thenReturn(BigDecimal.ZERO);
        when(mapper.selectById(101L)).thenReturn(original);
    }

    @AfterEach
    void after() {
        DynamicDataSourceContextHolder.poll();
        tenant.close();
    }

    private WmProductionRecptBo edit() {
        WmProductionRecptBo bo = new WmProductionRecptBo();
        bo.setRecptId(101L); bo.setRecptCode(original.getRecptCode());
        return bo;
    }

    @Test
    void executedReceiptCannotBeReopened() {
        original.setStatus("EXECUTED");
        WmProductionRecptBo bo = edit(); bo.setStatus("DRAFT");
        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "已执行原单不能普通编辑回草稿");
        verify(mapper, never()).updateById(any(WmProductionRecpt.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void executingReceiptCannotBeEdited() {
        original.setStatus("EXECUTING");
        WmProductionRecptBo bo = edit(); bo.setQuantity(new BigDecimal("7"));
        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "执行中的原单不能改数量");
        verify(mapper, never()).updateById(any(WmProductionRecpt.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void draftRequestCannotSetExecuted() {
        WmProductionRecptBo bo = edit(); bo.setStatus("EXECUTED");
        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "普通编辑不能伪造入库终态");
        verify(mapper, never()).updateById(any(WmProductionRecpt.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void draftCannotSwitchWorkOrder() {
        WmProductionRecptBo bo = edit(); bo.setWoId(202L);
        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "原草稿不能换工单来源");
        verify(mapper, never()).updateById(any(WmProductionRecpt.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void draftCannotSwitchFinishedMaterial() {
        WmProductionRecptBo bo = edit(); bo.setMaterialId(302L);
        assertThrows(ServiceException.class, () -> service.updateByBo(bo), "原草稿不能换成品物料");
        verify(mapper, never()).updateById(any(WmProductionRecpt.class));
        verify(mapper, never()).update(any(), any());
    }

    @Test
    void insertCannotPretendAlreadyExecuted() {
        WmProductionRecptBo bo = edit(); bo.setRecptId(null); bo.setStatus("EXECUTED");
        assertThrows(ServiceException.class, () -> service.insertByBo(bo), "普通新建只允许草稿");
        verify(mapper, never()).insert(any(WmProductionRecpt.class));
    }

    @Test
    void executedReceiptCannotBeDeleted() {
        original.setStatus("EXECUTED");
        assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[]{101L}), "已执行单据不能逻辑删除抹除凭据");
        verify(mapper, never()).deleteBatchIds(anyCollection());
    }

    @Test
    void unknownStateCannotBeExecuted() {
        original.setStatus("CANCELLED");
        assertThrows(ServiceException.class, () -> service.execute(101L), "取消或未知状态不能改为执行中");
        verify(mapper, never()).update(isNull(), any());
        verifyNoInteractions(stock, batches);
    }

    @Test
    void cumulativeCompletionCannotSkipStockButGrowReceipt() {
        original.setStatus("EXECUTED");
        when(mapper.selectList(any())).thenReturn(List.of(original));
        assertThrows(ServiceException.class, () -> service.createAndExecute(201L, original.getWoNo(), 301L,
            original.getMaterialCode(), "连接组件", "M12", new BigDecimal("8"), 3L, "WH003", "成品仓",
            28L, "L028", "金属库位", new Date()), "现行一次终态不能伪称支持累计补差额");
        verifyNoInteractions(stock);
        verify(mapper, never()).updateById(any(WmProductionRecpt.class));
        verify(mapper, never()).update(any(), any());
    }
}
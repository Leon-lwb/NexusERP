package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.domain.bo.WmProductionRecptBo;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import com.nexus.erp.wm.mapper.*;
import com.nexus.erp.wm.service.*;
import com.nexus.erp.wm.service.impl.WmProductionRecptServiceImpl;
import com.nexus.system.strategy.AutoCodeUtil;

import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.*;

import javax.sql.DataSource;

/** 运行真实入口、真实守卫和 Spring 事务代理的内存落账/回滚正控；不冒充真实 MySQL 锁验收。 */
class WmProductionRecptTransactionTest {
    private WmProductionRecptMapper rows;
    private WmProductionRecptGuardMapper source;
    private IWmStockOperationService stockOperation;
    private IWmBatchService batches;
    private AutoCodeUtil codes;
    private WmProductionRecptServiceImpl service;
    private MockedStatic<TenantHelper> tenant;
    private WmProductionRecpt stored;
    private WmProductionRecpt beforeReceipt;
    private List<WmMaterialStockTx> transactions;
    private List<WmMaterialStockTx> beforeTransactions;
    private PrdWorkOrder wo;
    private WmMaterialStock stock;
    private WmBatch batch;
    private BigDecimal beforeStockQuantity, beforeStockCost, beforeBatchReceived;
    private int committed, rolledBack;
    private boolean noTransactionProduced, finalCasLost, initialCasLost;

    @BeforeAll
    static void tableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(),""),WmProductionRecpt.class);
    }

    @BeforeEach
    void before() throws Exception {
        rows=mock(WmProductionRecptMapper.class); source=mock(WmProductionRecptGuardMapper.class);
        stockOperation=mock(IWmStockOperationService.class); batches=mock(IWmBatchService.class); codes=mock(AutoCodeUtil.class);
        tenant=mockStatic(TenantHelper.class); tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000"); DynamicDataSourceContextHolder.push("erp");
        transactions=new ArrayList<>(); committed=rolledBack=0;
        DataSource ds=mock(DataSource.class); Connection connection=mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        doAnswer(call->{ if (!call.getArgument(0,Boolean.class)) capture(); return null; }).when(connection).setAutoCommit(anyBoolean());
        doAnswer(call->{ committed++; return null; }).when(connection).commit();
        doAnswer(call->{ rolledBack++; restore(); return null; }).when(connection).rollback();
        TransactionInterceptor interceptor=new TransactionInterceptor();
        interceptor.setTransactionManager(new DataSourceTransactionManager(ds));
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        ProxyFactory proxy=new ProxyFactory(new WmProductionRecptServiceImpl(rows,stockOperation,codes,batches,new WmProductionRecptWriteGuard(source)));
        proxy.setProxyTargetClass(true); proxy.addAdvice(interceptor); service=(WmProductionRecptServiceImpl)proxy.getProxy();
        wo=new PrdWorkOrder(); wo.setWoId(201L); wo.setWoNo("SCGD202610070099"); wo.setTenantId("000000"); wo.setDelFlag("0");
        wo.setMaterialId(301L); wo.setMaterialCode("IF20261007099"); wo.setUnitId(26L); wo.setWoStatus("COMPLETED");
        wo.setPlannedQty(d("6")); wo.setCompletedQty(d("6")); wo.setActualEndDate(new Date()); wo.setQualityStatus("QUALIFIED");
        stock=new WmMaterialStock(); stock.setStockId(601L); stock.setTenantId("000000"); stock.setDelFlag("0"); stock.setWoId(201L);
        stock.setMaterialId(301L); stock.setMaterialCode(wo.getMaterialCode()); stock.setBaseUomId("26"); stock.setWarehouseId(3L);
        stock.setAreaId(9L); stock.setLocationId(28L); stock.setBatchId(401L); stock.setBatchCode("B202610070999");
        stock.setStockQuantity(d("0")); stock.setUnitCost(d("3.89")); stock.setTotalCost(d("0"));
        batch=new WmBatch(); batch.setBatchId(401L); batch.setBatchCode(stock.getBatchCode()); batch.setTenantId("000000"); batch.setDelFlag("0");
        batch.setMaterialId(301L); batch.setWarehouseId(3L); batch.setSourceDocType("PRD_WORK_ORDER"); batch.setSourceDocId(201L);
        batch.setSourceDocNo(wo.getWoNo()); batch.setStatus("ACTIVE"); batch.setReceivedQty(d("0")); batch.setIssuedQty(d("0"));
        when(source.findWorkOrder("000000",101L)).thenAnswer(call->stored==null?null:stored.getWoId());
        when(source.lockWorkOrder("000000",201L)).thenReturn(wo);
        when(source.lockReceipts("000000",201L)).thenAnswer(call->stored==null?List.of():List.of(copy(stored)));
        when(source.lockTransactions("000000",201L)).thenAnswer(call->new ArrayList<>(transactions));
        when(source.approvedReportedQuantity("000000",201L)).thenReturn(d("0"));
        when(source.lockStock("000000",601L)).thenReturn(stock); when(source.lockBatch("000000",401L)).thenReturn(batch);
        when(source.databaseDate()).thenReturn(LocalDate.now().toString());
        when(source.lockMaterial("000000",301L)).thenReturn(new WmProductionRecptGuardMapper.MaterialSource(301L,wo.getMaterialCode(),
            "连接组件","M12","1","0",26L,"件",26L,1L,d("1"),0,"1","0","Q-UNIT-26",new Date(),d("3.89")));
        when(source.lockStorage("000000",3L,28L)).thenReturn(new WmProductionRecptGuardMapper.StorageSource(3L,"WH003","成品仓",
            "1","0",9L,1L,0L,"0",28L,"L028","金属库位",1L,0L,"0"));
        when(codes.genSerialCode("WM_PRODUCTION_RECPT_CODE",null)).thenReturn("SCRK202610070999");
        when(rows.insert(any(WmProductionRecpt.class))).thenAnswer(call->{
            WmProductionRecpt r=call.getArgument(0); r.setRecptId(101L); stored=copy(r); return 1;
        });
        when(rows.update(any(),any())).thenAnswer(call->{
            WmProductionRecpt entity=call.getArgument(0);
            if (entity!=null && "EXECUTING".equals(entity.getStatus()) && "DRAFT".equals(stored.getStatus())) {
                if(initialCasLost)return 0; stored.setStatus("EXECUTING"); return 1;
            }
            if (entity!=null && "EXECUTED".equals(entity.getStatus()) && "EXECUTING".equals(stored.getStatus())) {
                if(finalCasLost)return 0; stored.setStatus("EXECUTED"); stored.setBatchId(entity.getBatchId()); stored.setBatchCode(entity.getBatchCode()); return 1;
            }
            if (entity!=null && "DRAFT".equals(entity.getStatus())) { stored=copy(entity); return 1; }
            return 0;
        });
        WmBatchVo result=new WmBatchVo(); result.setBatchId(401L); result.setBatchCode(batch.getBatchCode());
        when(batches.createBatchByMaterial(301L,"PRD_WORK_ORDER",201L,wo.getWoNo(),3L,null)).thenReturn(result);
        doAnswer(call->{
            BigDecimal q=call.getArgument(5); stock.setStockQuantity(stock.getStockQuantity().add(q));
            stock.setTotalCost(stock.getTotalCost().add(q.multiply(stock.getUnitCost()))); batch.setReceivedQty(batch.getReceivedQty().add(q));
            if(!noTransactionProduced) {
                WmMaterialStockTx tx=new WmMaterialStockTx(); tx.setTxId(501L); tx.setTenantId("000000"); tx.setDelFlag("0");
                tx.setSourceDocType("PRD_WORK_ORDER"); tx.setSourceDocId(201L); tx.setTxType("PRODUCTION_IN"); tx.setTxFlag(1);
                tx.setMaterialStockId(601L); tx.setMaterialId(301L); tx.setMaterialCode(wo.getMaterialCode()); tx.setBaseUomId("26");
                tx.setWarehouseId(3L); tx.setAreaId(9L); tx.setLocationId(28L); tx.setBatchId(401L); tx.setBatchCode(batch.getBatchCode());
                tx.setTxQuantity(q); tx.setBeforeQuantity(d("0")); tx.setAfterQuantity(q); tx.setUnitCost(stock.getUnitCost());
                tx.setTxAmount(q.multiply(tx.getUnitCost()).setScale(4)); tx.setTxDate(new Date()); transactions.add(tx);
            }
            return null;
        }).when(stockOperation).postProductionCompletion(anyLong(),anyString(),anyLong(),anyString(),anyString(),any(),anyLong(),anyLong(),anyLong(),anyString(),any());
    }

    @AfterEach
    void after() { DynamicDataSourceContextHolder.poll(); tenant.close(); }
    private static BigDecimal d(String n){return new BigDecimal(n);}
    private static WmProductionRecpt copy(WmProductionRecpt r){
        if(r==null)return null; WmProductionRecpt c=new WmProductionRecpt(); c.setRecptId(r.getRecptId()); c.setRecptCode(r.getRecptCode());
        c.setTenantId(r.getTenantId()); c.setDelFlag(r.getDelFlag()); c.setWoId(r.getWoId()); c.setWoNo(r.getWoNo());
        c.setMaterialId(r.getMaterialId()); c.setMaterialCode(r.getMaterialCode()); c.setMaterialName(r.getMaterialName()); c.setMaterialSpec(r.getMaterialSpec());
        c.setQuantity(r.getQuantity()); c.setWarehouseId(r.getWarehouseId()); c.setWarehouseCode(r.getWarehouseCode()); c.setWarehouseName(r.getWarehouseName());
        c.setLocationId(r.getLocationId()); c.setLocationCode(r.getLocationCode()); c.setLocationName(r.getLocationName());
        c.setRecptDate(r.getRecptDate()); c.setStatus(r.getStatus()); c.setBatchId(r.getBatchId()); c.setBatchCode(r.getBatchCode()); c.setRemark(r.getRemark()); return c;
    }
    private void capture(){beforeReceipt=copy(stored);beforeTransactions=new ArrayList<>(transactions);beforeStockQuantity=stock.getStockQuantity();
        beforeStockCost=stock.getTotalCost();beforeBatchReceived=batch.getReceivedQty();}
    private void restore(){stored=copy(beforeReceipt);transactions.clear();transactions.addAll(beforeTransactions);stock.setStockQuantity(beforeStockQuantity);
        stock.setTotalCost(beforeStockCost);batch.setReceivedQty(beforeBatchReceived);}
    private WmProductionRecptBo draft(){WmProductionRecptBo b=new WmProductionRecptBo();b.setWoId(201L);b.setQuantity(d("6"));
        b.setWarehouseId(3L);b.setLocationId(28L);b.setRecptDate(new Date());return b;}
    private Long auto(BigDecimal q){return service.createAndExecute(201L,wo.getWoNo(),301L,wo.getMaterialCode(),"请求显示名","请求规格",q,
        3L,"请求仓码","请求仓名",28L,"请求库位码","请求库位名",new Date());}

    @Test
    void manualDraftAndExecuteProduceOneVerifiedEffect(){
        WmProductionRecptBo b=draft();assertEquals(1,service.insertByBo(b));assertEquals(101L,b.getRecptId());
        assertEquals("DRAFT",stored.getStatus());assertEquals("连接组件",stored.getMaterialName());
        service.execute(101L);
        assertEquals("EXECUTED",stored.getStatus());assertEquals(1,transactions.size());assertEquals(d("6"),stock.getStockQuantity());
        assertEquals(d("23.34"),stock.getTotalCost());assertEquals(d("6"),batch.getReceivedQty());assertEquals(2,committed);
    }

    @Test
    void normalDraftEditRetainsSourceAndDoesNotPostStock(){
        WmProductionRecptBo created=draft();service.insertByBo(created);
        WmProductionRecptBo edit=new WmProductionRecptBo();edit.setRecptId(101L);edit.setRecptCode(stored.getRecptCode());
        edit.setQuantity(d("5"));edit.setRemark("调整草稿拟入库量，未确认完成事实");
        assertEquals(1,service.updateByBo(edit));assertEquals(d("5"),stored.getQuantity());assertEquals("DRAFT",stored.getStatus());
        assertEquals(201L,stored.getWoId());assertEquals(301L,stored.getMaterialId());verifyNoInteractions(stockOperation,batches);
    }

    @Test
    void normalUnpostedDraftCanBeDeletedAfterWholeScopeValidation(){
        WmProductionRecptBo created=draft();service.insertByBo(created);
        when(rows.deleteBatchIds(anyCollection())).thenAnswer(call->{stored=null;return 1;});
        assertEquals(1,service.deleteByIds(new Long[]{101L}));assertNull(stored);assertTrue(transactions.isEmpty());
        verifyNoInteractions(stockOperation,batches);assertEquals(2,committed);
    }

    @Test
    void automaticOriginalResultCanReplayWithoutAnyNewStock(){
        assertEquals(101L,auto(d("6")));WmProductionRecpt posted=copy(stored);BigDecimal quantity=stock.getStockQuantity();
        assertEquals(101L,auto(d("6.0000")));assertEquals(posted,stored);assertEquals(quantity,stock.getStockQuantity());
        assertEquals(1,transactions.size());verify(stockOperation,times(1)).postProductionCompletion(anyLong(),anyString(),anyLong(),anyString(),anyString(),any(),anyLong(),anyLong(),anyLong(),anyString(),any());
    }

    @Test
    void largerAutomaticQuantityCannotIncrementOldReceiptOrStock(){
        auto(d("6")); WmProductionRecpt posted=copy(stored);
        assertThrows(ServiceException.class,()->auto(d("8")),"累计量变化不是原完成事件重放");
        assertEquals(posted,stored);assertEquals(d("6"),stock.getStockQuantity());assertEquals(1,transactions.size());assertEquals(1,rolledBack);
    }

    @Test
    void missingProducedTransactionRollsBackHeadBatchAndStock(){
        noTransactionProduced=true;
        assertThrows(ServiceException.class,()->auto(d("6")),"无真实流水不能确认入库成功");
        assertNull(stored);assertTrue(transactions.isEmpty());assertEquals(d("0"),stock.getStockQuantity());
        assertEquals(d("0"),stock.getTotalCost());assertEquals(d("0"),batch.getReceivedQty());assertEquals(1,rolledBack);
    }

    @Test
    void lostFinalCasRollsBackAlreadyProducedPhysicalEffects(){
        finalCasLost=true;
        assertThrows(ServiceException.class,()->auto(d("6")),"终态回写失败必须回滚全部本次效果");
        assertNull(stored);assertTrue(transactions.isEmpty());assertEquals(d("0"),stock.getStockQuantity());assertEquals(d("0"),batch.getReceivedQty());
        assertEquals(0,committed);assertEquals(1,rolledBack);
    }

    @Test
    void lostInitialCasDoesNotInvokeBatchOrStock(){
        WmProductionRecptBo b=draft();service.insertByBo(b);initialCasLost=true;
        assertThrows(ServiceException.class,()->service.execute(101L),"原单状态竞争失败不能继续过账");
        assertEquals("DRAFT",stored.getStatus());verifyNoInteractions(stockOperation,batches);assertEquals(1,rolledBack);
    }

    @Test
    void anotherManualDraftCannotBeCreatedAfterOriginalExecution(){
        auto(d("6"));WmProductionRecpt posted=copy(stored);
        assertThrows(ServiceException.class,()->service.insertByBo(draft()),"已有原结果不能再建同源草稿");
        assertEquals(posted,stored);assertEquals(1,transactions.size());
    }

    @Test
    void originalReplayAfterShipmentDoesNotRestoreConsumedStock(){
        auto(d("6"));stock.setStockQuantity(d("0"));stock.setTotalCost(d("0"));batch.setIssuedQty(d("6"));
        assertEquals(101L,auto(d("6")));assertEquals(d("0"),stock.getStockQuantity());assertEquals(d("0"),stock.getTotalCost());
        assertEquals(1,transactions.size());assertEquals(d("6"),batch.getIssuedQty());
    }
}
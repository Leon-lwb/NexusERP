package com.nexus.erp.wm;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.baomidou.dynamic.datasource.toolkit.DynamicDataSourceContextHolder;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.*;
import com.nexus.erp.wm.mapper.WmProductionRecptGuardMapper;
import com.nexus.erp.wm.service.WmProductionRecptWriteGuard;

import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Supplier;

import javax.sql.DataSource;

// 同名类型说明：本文件同时使用 java.util.Date 和 java.sql.Date，后者保留全限定名以区分。

/** 原工单、单位、仓储和历史入库结果的真实守卫用例；模拟 JDBC 不代表 MySQL 现场验收。 */
class WmProductionRecptWriteGuardTest {
    private WmProductionRecptGuardMapper mapper;
    private WmProductionRecptWriteGuard guard;
    private TransactionTemplate transaction;
    private MockedStatic<TenantHelper> tenant;
    private PrdWorkOrder wo;
    private WmProductionRecpt receipt;
    private WmMaterialStockTx tx;
    private WmMaterialStock stock;
    private WmBatch batch;

    @BeforeEach
    void before() throws Exception {
        tenant = mockStatic(TenantHelper.class);
        tenant.when(TenantHelper::isEnable).thenReturn(true);
        tenant.when(TenantHelper::getTenantId).thenReturn("000000");
        DynamicDataSourceContextHolder.push("erp");
        DataSource ds = mock(DataSource.class); Connection connection = mock(Connection.class);
        when(ds.getConnection()).thenReturn(connection); when(connection.getAutoCommit()).thenReturn(true);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
        mapper = mock(WmProductionRecptGuardMapper.class); guard = new WmProductionRecptWriteGuard(mapper);
        wo = new PrdWorkOrder(); wo.setWoId(201L); wo.setWoNo("SCGD202610070099");
        wo.setTenantId("000000"); wo.setDelFlag("0"); wo.setMaterialId(301L); wo.setMaterialCode("IF20261007099");
        wo.setUnitId(26L); wo.setWoStatus("COMPLETED"); wo.setQualityStatus("QUALIFIED");
        wo.setPlannedQty(d("6")); wo.setCompletedQty(d("6")); wo.setActualEndDate(new Date());
        receipt = new WmProductionRecpt(); receipt.setRecptId(101L); receipt.setRecptCode("SCRK202610070999");
        receipt.setTenantId("000000"); receipt.setDelFlag("0"); receipt.setWoId(201L); receipt.setWoNo(wo.getWoNo());
        receipt.setMaterialId(301L); receipt.setMaterialCode(wo.getMaterialCode()); receipt.setQuantity(d("6"));
        receipt.setWarehouseId(3L); receipt.setLocationId(28L); receipt.setStatus("DRAFT"); receipt.setRecptDate(new Date());
        tx = new WmMaterialStockTx(); tx.setTxId(501L); tx.setTenantId("000000"); tx.setDelFlag("0");
        tx.setSourceDocType("PRD_WORK_ORDER"); tx.setSourceDocId(201L); tx.setTxType("PRODUCTION_IN"); tx.setTxFlag(1);
        tx.setMaterialStockId(601L); tx.setMaterialId(301L); tx.setMaterialCode(wo.getMaterialCode()); tx.setBaseUomId("26");
        tx.setWarehouseId(3L); tx.setAreaId(9L); tx.setLocationId(28L); tx.setBatchId(401L); tx.setBatchCode("B202610070999");
        tx.setTxQuantity(d("6")); tx.setBeforeQuantity(d("0")); tx.setAfterQuantity(d("6"));
        tx.setUnitCost(d("3.89")); tx.setTxAmount(d("23.3400")); tx.setTxDate(new Date());
        stock = new WmMaterialStock(); stock.setStockId(601L); stock.setWoId(201L); stock.setTenantId("000000"); stock.setDelFlag("0");
        stock.setMaterialId(301L); stock.setMaterialCode(wo.getMaterialCode()); stock.setBaseUomId("26");
        stock.setWarehouseId(3L); stock.setAreaId(9L); stock.setLocationId(28L); stock.setBatchId(401L); stock.setBatchCode(tx.getBatchCode());
        stock.setStockQuantity(d("6")); stock.setTotalCost(d("23.3400"));
        batch = new WmBatch(); batch.setBatchId(401L); batch.setBatchCode(tx.getBatchCode()); batch.setTenantId("000000"); batch.setDelFlag("0");
        batch.setMaterialId(301L); batch.setWarehouseId(3L); batch.setSourceDocType("PRD_WORK_ORDER"); batch.setSourceDocId(201L);
        batch.setSourceDocNo(wo.getWoNo()); batch.setReceivedQty(d("6")); batch.setIssuedQty(d("0")); batch.setStatus("ACTIVE");
        when(mapper.findWorkOrder("000000",101L)).thenReturn(201L);
        when(mapper.lockWorkOrder("000000",201L)).thenReturn(wo);
        when(mapper.lockReceipts("000000",201L)).thenAnswer(call -> List.of(receipt));
        when(mapper.lockTransactions("000000",201L)).thenReturn(List.of());
        when(mapper.approvedReportedQuantity("000000",201L)).thenReturn(d("0"));
        when(mapper.lockStock("000000",601L)).thenReturn(stock);
        when(mapper.lockBatch("000000",401L)).thenReturn(batch);
        when(mapper.databaseDate()).thenReturn(LocalDate.now().toString());
        when(mapper.lockMaterial("000000",301L)).thenReturn(new WmProductionRecptGuardMapper.MaterialSource(301L,
            wo.getMaterialCode(),"连接组件","M12","1","0",26L,"件",26L,1L,d("1"),0,"1","0","Q-UNIT-26",new Date(),d("3.89")));
        when(mapper.lockStorage("000000",3L,28L)).thenReturn(new WmProductionRecptGuardMapper.StorageSource(3L,"WH003","成品仓",
            "1","0",9L,1L,0L,"0",28L,"L028","金属库位",1L,0L,"0"));
    }

    @AfterEach
    void after() { DynamicDataSourceContextHolder.poll(); tenant.close(); }
    private <T> T inTransaction(Supplier<T> operation) { return transaction.execute(status -> operation.get()); }
    private void run(Runnable operation) { inTransaction(() -> { operation.run(); return null; }); }
    private static BigDecimal d(String n) { return new BigDecimal(n); }
    private void posted() {
        receipt.setStatus("EXECUTED"); receipt.setBatchId(401L); receipt.setBatchCode(tx.getBatchCode());
        when(mapper.lockTransactions("000000",201L)).thenReturn(List.of(tx));
    }

    @Test
    void validDraftUsesTrueSourceAndStorageNames() {
        run(() -> guard.validateReady(wo,receipt));
        assertEquals("连接组件",receipt.getMaterialName(),"成品名称来自原生物料而非请求");
        assertEquals("WH003",receipt.getWarehouseCode(),"仓库显示值来自真实父链");
    }

    @Test
    void nativeSqlDateUsesSameBusinessDateWithoutUnsupportedInstantConversion() {
        receipt.setRecptDate(java.sql.Date.valueOf(LocalDate.now()));
        run(() -> guard.validateReady(wo,receipt));
        assertEquals("WH003",receipt.getWarehouseCode(),"原生日期类型应按同一业务日期验证");
    }

    @Test
    void realParentLockPrecedesAllReceiptLocks() {
        var locked = inTransaction(() -> guard.lockReceipt(101L));
        assertSame(receipt,locked.receipt());
        var order = inOrder(mapper);
        order.verify(mapper).findWorkOrder("000000",101L);
        order.verify(mapper).lockWorkOrder("000000",201L);
        order.verify(mapper).lockReceipts("000000",201L);
    }

    @ParameterizedTest
    @ValueSource(strings={"CREATED","IN_PROGRESS","CANCELLED","APPROVING"})
    void unfinishedOrCancelledWorkOrderCannotAuthorizeStock(String status) {
        wo.setWoStatus(status);
        assertThrows(ServiceException.class, () -> run(() -> guard.validateReady(wo,receipt)),"真实完工之前不得产生入库");
    }

    @Test
    void closedWorkOrderCannotCreateAnotherDraft() {
        wo.setWoStatus("CLOSED");
        assertThrows(ServiceException.class, () -> run(() -> guard.validateDraft(wo,receipt)),"已关闭工单不能新建或编辑入库承诺");
    }

    @Test
    void knownUnqualifiedWorkOrderCannotBeReceived() {
        wo.setQualityStatus("UNQUALIFIED");
        assertThrows(ServiceException.class, () -> run(() -> guard.validateReady(wo,receipt)),"已知不合格不能绕质量状态入库");
    }

    @Test
    void quantityMustMatchTrueCompletionLessApprovedReports() {
        when(mapper.approvedReportedQuantity("000000",201L)).thenReturn(d("2"));
        assertThrows(ServiceException.class, () -> run(() -> guard.validateReady(wo,receipt)),"不能重复入已审核报工量");
        receipt.setQuantity(d("4")); run(() -> guard.validateReady(wo,receipt));
    }

    @Test
    void unknownReportedQuantityIsNotZero() {
        when(mapper.approvedReportedQuantity("000000",201L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> inTransaction(() -> guard.readyQuantity(wo)),"报工来源缺失不等于零");
    }

    @Test
    void receiptUnitAndMaterialCannotBeGuessed() {
        wo.setUnitId(27L);
        assertThrows(ServiceException.class, () -> run(() -> guard.validateReady(wo,receipt)),"工单单位与物料根单位不符时拒绝");
    }

    @Test
    void integerUnitCannotBeRoundedToAcceptFractionalInput() {
        receipt.setQuantity(d("5.5"));
        assertThrows(ServiceException.class, () -> run(() -> guard.validateDraft(wo,receipt)),"根单位精度零不能擅自取整");
    }

    @Test
    void missingStorageOrWrongTenantFailsClosed() {
        when(mapper.lockStorage("000000",3L,28L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> run(() -> guard.validateReady(wo,receipt)),"缺仓储来源不能执行");
        wo.setTenantId("000008");
        assertThrows(ServiceException.class, () -> inTransaction(() -> guard.lockWorkOrder(201L)),"工单返回跨租户行不得信任");
    }

    @Test
    void deletedAndDuplicateReceiptsRemainBlockingEvidence() {
        receipt.setDelFlag("2");
        assertThrows(ServiceException.class, () -> inTransaction(() -> guard.lockReceipt(101L)),"删除行不能当无入库历史");
        receipt.setDelFlag("0"); when(mapper.lockReceipts("000000",201L)).thenReturn(List.of(receipt,receipt));
        assertThrows(ServiceException.class, () -> inTransaction(() -> guard.lockReceipt(101L)),"重复凭据不能任选第一张");
    }

    @Test
    void existingEffectBlocksEditingEvenWhenHeadStillDraft() {
        when(mapper.lockTransactions("000000",201L)).thenReturn(List.of(tx));
        assertThrows(ServiceException.class, () -> run(() -> guard.requireNoEffects(wo)),"半过账不能重新写草稿");
    }

    @Test
    void actualCompletionIsCheckedAfterSubtractingApprovedReports(){
        wo.setCompletedQty(d("8")); when(mapper.approvedReportedQuantity("000000",201L)).thenReturn(d("2"));
        assertEquals(d("6"),assertDoesNotThrow(() -> inTransaction(() -> guard.readyQuantity(wo)),
            "真实本次数量有效时不能因累计量误拒绝"),"累计量含审核报工，应先扣已报量再核本次数量");
        run(() -> guard.validateReady(wo,receipt));
    }

    @Test
    void stockMaterialCodeDriftCannotRecoverOriginalEntry(){
        posted();stock.setMaterialCode("另一成品编码");
        assertThrows(ServiceException.class,() -> inTransaction(() -> guard.verifyExecuted(wo,d("6"),receipt)),"库存物料编码漂移不能因ID相同放行");
    }

    @Test
    void mutuallyDriftingWorkOrderAndTransactionUnitCannotOverrideMaterialRoot(){
        posted();wo.setUnitId(27L);tx.setBaseUomId("27");stock.setBaseUomId("27");
        assertThrows(ServiceException.class,() -> inTransaction(() -> guard.verifyExecuted(wo,d("6"),receipt)),"多份错误单位相互一致也不能替代实际物料根单位");
    }

    @Test
    void closedCompletedWorkOrderCanOnlyRecoverItsOriginalReceipt(){
        posted();wo.setWoStatus("CLOSED");
        BigDecimal original=assertDoesNotThrow(() -> inTransaction(() -> guard.readyQuantity(wo)),"合法关单不抹除过去完工来源");
        assertSame(receipt,inTransaction(() -> guard.verifyExecuted(wo,original,receipt)));
        receipt.setStatus("DRAFT");
        assertThrows(ServiceException.class,() -> run(() -> guard.validateReady(wo,receipt)),"关闭工单的原回执恢复不授权新的入库");
    }

    @Test
    void expiredKnownBatchStatusCanRecoverAlreadyPostedOriginal(){
        posted();batch.setStatus("EXPIRED");stock.setStockQuantity(d("0"));batch.setIssuedQty(d("6"));
        assertSame(receipt,assertDoesNotThrow(() -> inTransaction(() -> guard.verifyExecuted(wo,d("6"),receipt)),
            "合法到期不得误拒绝完整原回执"),"合法批次到期不抹除过去已证明的入库事实");
    }

    @Test
    void deletedZeroEffectDraftDoesNotBlockNewReceiptScope(){
        receipt.setDelFlag("2");
        assertTrue(inTransaction(() -> guard.receipts(wo)).isEmpty(),"合法删除的零效果草稿不是已执行凭据");
    }

    @Test
    void completeOriginalReceiptCanRecoverAfterLegalShipment() {
        posted(); stock.setStockQuantity(d("0")); stock.setTotalCost(d("0")); batch.setIssuedQty(d("6"));
        assertSame(receipt,inTransaction(() -> guard.verifyExecuted(wo,d("6.0000"),receipt)),"合法出库后恢复原入库事实，不要求库存仍有六件");
    }

    @ParameterizedTest
    @ValueSource(strings={"missing","duplicate","wrongUnit","wrongQuantity","wrongCost","wrongStock","wrongBatch","deletedTx"})
    void incompleteOrDriftingHistoricalEffectCannotRecover(String kind) {
        posted();
        switch (kind) {
            case "missing" -> when(mapper.lockTransactions("000000",201L)).thenReturn(List.of());
            case "duplicate" -> when(mapper.lockTransactions("000000",201L)).thenReturn(List.of(tx,tx));
            case "wrongUnit" -> tx.setBaseUomId("27");
            case "wrongQuantity" -> tx.setAfterQuantity(d("7"));
            case "wrongCost" -> tx.setTxAmount(d("24"));
            case "wrongStock" -> stock.setWoId(202L);
            case "wrongBatch" -> batch.setSourceDocId(202L);
            case "deletedTx" -> tx.setDelFlag("2");
        }
        assertThrows(ServiceException.class, () -> inTransaction(() -> guard.verifyExecuted(wo,d("6"),receipt)),"漂移或不完整原结果不得返回成功");
    }

    @Test
    void missingErpWriteTransactionCannotBePretendedByContext() {
        assertThrows(ServiceException.class,guard::tenant,"只有数据源路由没有真实事务仍拒绝");
        DynamicDataSourceContextHolder.push("master");
        try { assertThrows(ServiceException.class,() -> run(guard::tenant),"已有事务绑定错误数据源仍拒绝"); }
        finally { DynamicDataSourceContextHolder.poll(); }
    }

    @Test
    void fixedSqlPinsTenantAndLocksWithoutFilteringDeletedEvidence() throws Exception {
        for (String method : List.of("lockWorkOrder","lockReceipts","lockTransactions","lockStock","lockBatch")) {
            var found =
                    Arrays.stream(WmProductionRecptGuardMapper.class.getMethods())
                            .filter(m -> method.equals(m.getName()))
                            .findFirst()
                            .orElseThrow();
            String sql = String.join(" ", found.getAnnotation(Select.class).value());
            assertTrue(sql.contains("tenant_id=#{tenant}"),"固定原生读取必须显式限定租户："+method);
            assertTrue(sql.contains("FOR UPDATE"),"写授权读取必须锁住真实源："+method);
            assertFalse(sql.contains("del_flag='0'"),"异常删除凭据不能被读取器隐藏："+method);
        }
    }
}
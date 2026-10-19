package com.nexus.erp.wm;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.trace.SerialIdentity;
import com.nexus.common.trace.SerialReservation;
import com.nexus.common.trace.SerialReservationCommand;
import com.nexus.erp.wm.trace.ErpSerialIdentityService;
import com.nexus.erp.wm.controller.WmSnController;
import com.nexus.erp.wm.domain.WmSn;
import com.nexus.erp.wm.domain.bo.WmSnBo;
import com.nexus.erp.wm.domain.vo.WmSnVo;
import com.nexus.erp.wm.mapper.WmSnMapper;
import com.nexus.erp.wm.service.impl.WmSnServiceImpl;
import com.nexus.erp.wm.trace.ErpTraceContext;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 旧SN维护入口必须服从统一注册及业务单据边界；全部使用合成对象与Mock端口。 */
@ExtendWith(MockitoExtension.class)
class WmSnServiceImplTest {
    @Mock private WmSnMapper baseMapper;
    @Mock private ErpSerialIdentityService identities;
    @Mock private ErpTraceContext context;
    @InjectMocks private WmSnServiceImpl service;
    private WmSn existing;

    @BeforeAll static void initializeLambdaMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), WmSn.class);
    }

    @BeforeEach void setup() {
        existing = new WmSn();
        existing.setSnId(1L);
        existing.setSnCode("SN-EXISTING");
        existing.setMaterialId(100L);
        existing.setStatus("REGISTERED");
        lenient().when(context.tenant()).thenReturn("T1");
    }

    private WmSnBo command(String commandId) {
        WmSnBo bo = new WmSnBo();
        bo.setCommandId(commandId);
        bo.setMaterialId(100L);
        bo.setSourceDocType("ERP_MANUAL");
        return bo;
    }

    private void stubRegistry() {
        when(identities.reserveManual(eq("T1"), any())).thenAnswer(call -> {
            SerialReservationCommand command = call.getArgument(1);
            var serials = IntStream.range(0, command.quantity()).mapToObj(index -> new SerialIdentity(
                9007199254740993L + index, "ERP-" + command.commandId() + "-" + index,
                command.materialId(), command.batchId(), command.batchId() == null ? null : "B9")).toList();
            return new SerialReservation(88L, command.commandId(), command.fingerprint(), serials, "RESERVED");
        });
    }

    /** 新增不能在缺命令时自行造幂等号，更不能直接插入SN。 */
    @Test void insertRequiresExplicitCommandBeforeAnyWrite() {
        for (String invalid : new String[] {null, "", " ", "X".repeat(129)}) {
            WmSnBo bo = command(invalid);
            assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        }
        verifyNoInteractions(baseMapper, identities);
    }

    /** 单个新增接入ERP注册，回填统一身份，不把REGISTERED当库存。 */
    @Test void singleInsertUsesRegistryAndFrozenBatchIdentity() {
        stubRegistry();
        WmSnBo bo = command("single");
        bo.setBatchId(9L);
        assertEquals(1, service.insertByBo(bo));
        verify(identities).reserveManual("T1", new SerialReservationCommand("single",100L,1,null,"ERP_MANUAL",null,9L));
        assertEquals(9007199254740993L, bo.getSnId());
        assertEquals("ERP-single-0", bo.getSnCode());
        verifyNoInteractions(baseMapper);
    }

    /** 原批量URL必须把BO中的命令交给统一注册，不再调用无命令旧重载。 */
    @Test void originalControllerBatchUrlForwardsCommandAndWholeQuantity() {
        stubRegistry();
        WmSnBo bo = command("batch");
        bo.setQuantity(3);
        bo.setBatchId(9L);
        bo.setRuleId(7L);
        new WmSnController(service).batchGenerate(bo);
        verify(identities).reserveManual("T1",new SerialReservationCommand("batch",100L,3,7L,"ERP_MANUAL",null,9L));
        verifyNoInteractions(baseMapper);
    }

    /** 原新增URL与服务入口同样不能绕过命令和注册规则。 */
    @Test void originalControllerAddUrlRejectsMissingCommand() {
        assertThrows(ServiceException.class, () -> new WmSnController(service).add(command(null)));
        verifyNoInteractions(baseMapper, identities);
    }

    /** 连续批量和单个入口分别传递完整请求；号段唯一性由真实注册服务回归证明。 */
    @Test void consecutiveBatchAndSingleRequestsNeverUseLocalNumbering() {
        stubRegistry();
        WmSnBo first = command("first"); first.setQuantity(3);
        WmSnBo second = command("second"); second.setQuantity(2);
        service.batchGenerate(first);
        service.batchGenerate(second);
        service.insertByBo(command("single"));
        ArgumentCaptor<SerialReservationCommand> commands = ArgumentCaptor.forClass(SerialReservationCommand.class);
        verify(identities,times(3)).reserveManual(eq("T1"),commands.capture());
        assertEquals(List.of(3,2,1),commands.getAllValues().stream().map(SerialReservationCommand::quantity).toList());
        assertEquals(List.of("first","second","single"),commands.getAllValues().stream().map(SerialReservationCommand::commandId).toList());
        verifyNoInteractions(baseMapper);
    }

    @Test void commandlessLegacyBatchOverloadIsDisabledEvenForValidInput() {
        assertTrue(assertThrows(ServiceException.class, () -> service.batchGenerate(100L,3,9L,7L,200L,"PO","PO-200"))
            .getMessage().contains("commandId"));
        verifyNoInteractions(baseMapper,identities);
    }

    @Test void batchRequiresPositiveQuantityAndExplicitCommand() {
        for (Integer quantity : new Integer[] {null,0,-1}) {
            WmSnBo bo = command("quantity"); bo.setQuantity(quantity);
            assertThrows(ServiceException.class, () -> service.batchGenerate(bo));
        }
        WmSnBo missing = command(null); missing.setQuantity(2);
        assertThrows(ServiceException.class, () -> service.batchGenerate(missing));
        verifyNoInteractions(baseMapper,identities);
    }

    @Test void singleInsertCannotSmuggleBatchQuantityOrInventFormalNumber() {
        WmSnBo multiple = command("multiple"); multiple.setQuantity(2);
        assertThrows(ServiceException.class, () -> service.insertByBo(multiple));
        WmSnBo manual = command("manual"); manual.setSnCode("CUSTOMER-NUMBER");
        assertThrows(ServiceException.class, () -> service.insertByBo(manual));
        WmSnBo oversized = command("oversized"); oversized.setSnCode("X".repeat(65));
        assertTrue(assertThrows(ServiceException.class, () -> service.insertByBo(oversized)).getMessage().contains("64"));
        assertEquals(65,oversized.getSnCode().length());
        verifyNoInteractions(baseMapper,identities);
    }

    /** 登记不能携带在库声明或库存位置等业务事实。 */
    @Test void registrationCannotCreateInventoryOrChooseAnotherIdentity() {
        for (Consumer<WmSnBo> mutate : List.<Consumer<WmSnBo>>of(
            bo -> bo.setStatus("IN_STOCK"),bo -> bo.setStockId(1L),bo -> bo.setWarehouseId(2L),
            bo -> bo.setLocationId(3L),bo -> bo.setSnId(7L))) {
            WmSnBo bo = command("unsafe"); mutate.accept(bo);
            assertThrows(ServiceException.class, () -> service.insertByBo(bo));
        }
        verifyNoInteractions(baseMapper,identities);
    }

    @Test void metadataUpdateAllowsHistoricalLongNumberWithoutRewritingFacts() {
        String historical = "H".repeat(128);
        existing.setSnCode(historical);
        existing.setStockId(66L);
        existing.setStatus("IN_STOCK");
        when(baseMapper.selectById(1L)).thenReturn(existing);
        when(baseMapper.updateById(any(WmSn.class))).thenReturn(1);
        WmSnBo bo = new WmSnBo();
        bo.setSnId(1L); bo.setSnCode(historical); bo.setMaterialId(100L); bo.setStockId(66L); bo.setStatus("IN_STOCK");
        bo.setRemark("复核备注"); bo.setAttr1("维护信息");
        assertEquals(1,service.updateByBo(bo));
        ArgumentCaptor<WmSn> patch = ArgumentCaptor.forClass(WmSn.class);
        verify(baseMapper).updateById(patch.capture());
        assertEquals(1L,patch.getValue().getSnId());
        assertEquals("复核备注",patch.getValue().getRemark());
        assertEquals("维护信息",patch.getValue().getAttr1());
        assertAll(() -> assertNull(patch.getValue().getSnCode()), () -> assertNull(patch.getValue().getMaterialId()),
            () -> assertNull(patch.getValue().getBatchId()), () -> assertNull(patch.getValue().getStockId()),
            () -> assertNull(patch.getValue().getStatus()), () -> assertNull(patch.getValue().getRegistrationCommand()));
    }

    @ParameterizedTest(name="不能直接修改事实字段 {0}")
    @MethodSource("factChanges")
    void factChangesAreRejected(String name, Consumer<WmSnBo> mutate) {
        when(baseMapper.selectById(1L)).thenReturn(existing);
        WmSnBo bo = new WmSnBo(); bo.setSnId(1L); bo.setRemark("备注"); mutate.accept(bo);
        assertTrue(assertThrows(ServiceException.class, () -> service.updateByBo(bo)).getMessage().contains("业务单据"),name);
        verify(baseMapper,never()).updateById(any(WmSn.class));
    }

    static Stream<Arguments> factChanges() {
        return Stream.of(
            Arguments.of("SnCode",(Consumer<WmSnBo>) bo -> bo.setSnCode("OTHER")),
            Arguments.of("MaterialId",(Consumer<WmSnBo>) bo -> bo.setMaterialId(101L)),
            Arguments.of("MaterialCode",(Consumer<WmSnBo>) bo -> bo.setMaterialCode("OTHER")),
            Arguments.of("MaterialName",(Consumer<WmSnBo>) bo -> bo.setMaterialName("OTHER")),
            Arguments.of("MaterialSpec",(Consumer<WmSnBo>) bo -> bo.setMaterialSpec("OTHER")),
            Arguments.of("BatchId",(Consumer<WmSnBo>) bo -> bo.setBatchId(9L)),
            Arguments.of("BatchCode",(Consumer<WmSnBo>) bo -> bo.setBatchCode("B9")),
            Arguments.of("StockId",(Consumer<WmSnBo>) bo -> bo.setStockId(8L)),
            Arguments.of("WarehouseId",(Consumer<WmSnBo>) bo -> bo.setWarehouseId(2L)),
            Arguments.of("WarehouseCode",(Consumer<WmSnBo>) bo -> bo.setWarehouseCode("W")),
            Arguments.of("WarehouseName",(Consumer<WmSnBo>) bo -> bo.setWarehouseName("W")),
            Arguments.of("LocationId",(Consumer<WmSnBo>) bo -> bo.setLocationId(3L)),
            Arguments.of("LocationCode",(Consumer<WmSnBo>) bo -> bo.setLocationCode("L")),
            Arguments.of("Status",(Consumer<WmSnBo>) bo -> bo.setStatus("SCRAP")),
            Arguments.of("SourceDocId",(Consumer<WmSnBo>) bo -> bo.setSourceDocId(200L)),
            Arguments.of("SourceDocType",(Consumer<WmSnBo>) bo -> bo.setSourceDocType("PO")),
            Arguments.of("SourceDocNo",(Consumer<WmSnBo>) bo -> bo.setSourceDocNo("PO200")),
            Arguments.of("RuleId",(Consumer<WmSnBo>) bo -> bo.setRuleId(7L)),
            Arguments.of("ProductionDate",(Consumer<WmSnBo>) bo -> bo.setProductionDate(new Date(1L))),
            Arguments.of("ExpiryDate",(Consumer<WmSnBo>) bo -> bo.setExpiryDate(new Date(2L)))
        );
    }

    /** 危险入口只给出业务错误，既不查写实体，也不回收正式号码。 */
    @Test void deleteBindUnbindAndStatusRoutesAreClosed() {
        assertAll(
            () -> assertTrue(assertThrows(ServiceException.class, () -> service.deleteByIds(new Long[] {1L})).getMessage().contains("业务单据")),
            () -> assertTrue(assertThrows(ServiceException.class, () -> service.bindStock(1L,2L,3L,4L)).getMessage().contains("业务单据")),
            () -> assertTrue(assertThrows(ServiceException.class, () -> service.unbindStock(1L)).getMessage().contains("业务单据")),
            () -> assertTrue(assertThrows(ServiceException.class, () -> service.changeStatus(1L,"IN_STOCK")).getMessage().contains("业务单据"))
        );
        verifyNoInteractions(baseMapper,identities);
    }

    /** 原查询仍返回历史原文，统一身份别名随主字段稳定映射。 */
    @Test void queriesPreserveHistoryAndExposeStableIdentityAliases() {
        WmSnVo vo = new WmSnVo(); vo.setSnId(9007199254740993L); vo.setSnCode("H".repeat(128));
        when(baseMapper.selectVoById(9007199254740993L)).thenReturn(vo);
        assertSame(vo,service.selectById(9007199254740993L));
        assertEquals(vo.getSnId(),vo.getIdentityId());
        assertEquals(vo.getSnCode(),vo.getCanonicalSnCode());
        vo.setSnCode("CANONICAL");
        assertEquals("CANONICAL",vo.getCanonicalSnCode());
        WmSnBo filter = new WmSnBo(); filter.setMaterialId(100L); filter.setSnCode("H".repeat(128));
        when(baseMapper.selectVoList(any(LambdaQueryWrapper.class))).thenReturn(List.of(vo));
        assertEquals(List.of(vo),service.selectList(filter));
        assertEquals(List.of(vo),service.selectByMaterialAndStatus(100L,"REGISTERED"));
    }

    @Test void pageQueryKeepsExistingPaginationContract() {
        Page<WmSnVo> page = new Page<>(1,20); page.setTotal(0);
        when(baseMapper.selectVoPage(any(Page.class),any(LambdaQueryWrapper.class))).thenReturn(page);
        assertNotNull(service.selectPageList(new WmSnBo(),new PageQuery(20,1)));
    }
}

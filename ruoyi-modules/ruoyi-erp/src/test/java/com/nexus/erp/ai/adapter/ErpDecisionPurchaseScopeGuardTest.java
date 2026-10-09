package com.nexus.erp.ai.adapter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.nexus.common.ai.decision.dto.DecisionBusinessRef;
import com.nexus.common.ai.decision.enums.DecisionBusinessType;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.ai.decision.util.DecisionPurchaseContractCodec.SourceChangedException;
import com.nexus.common.tenant.helper.TenantHelper;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper;
import com.nexus.erp.ai.adapter.mapper.ErpDecisionPurchaseScopeMapper.*;
import com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor;
import com.nexus.erp.pu.service.PuRequirementErpTransactionQualification;
import com.nexus.erp.pu.service.PuRequirementMaterialLockService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.slf4j.LoggerFactory;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 全部合成来源，只证明拒绝规则与调用顺序，不冒充数据库物理验收。 */
class ErpDecisionPurchaseScopeGuardTest {
    private static final String TENANT="000000";
    private static final String PRIVATE = "SELECT private_table; password=FAKE_ONLY; Bearer FAKE_TOKEN_ONLY; jdbc:mysql://FAKE_ONLY";
    private static final String ROOT_STAGE = "PURCHASE_SCOPE_READ";
    private static final String ROOT_SAFE_MESSAGE = "采购根来源读取无法核验，请稍后重试或人工核对";
    private static final LocalDate DAY=LocalDate.of(2026,10,6);
    private final DecisionBusinessRef root=new DecisionBusinessRef(DecisionBusinessType.WORK_ORDER,11L,null);
    private ErpDecisionPurchaseScopeMapper mapper;
    private PuRequirementErpTransactionQualification qualification;
    private PuRequirementMaterialLockService locks;
    private ErpDecisionPurchaseScopeGuard guard;
    private MockedStatic<TenantHelper> tenants;
    private WorkSource work(Long id,String source,Long sourceId) { return new WorkSource(id,TENANT,31L,source,sourceId,"RELEASED","0"); }
    private VendorChoice vendor(Long relation,Long id,int mark) { return new VendorChoice(relation,TENANT,139L,id,mark,1,"0","ACTIVE","0"); }
    @BeforeEach void setup() {
        tenants=mockStatic(TenantHelper.class);tenants.when(TenantHelper::isEnable).thenReturn(true);
        tenants.when(TenantHelper::getTenantId).thenReturn(TENANT);
        mapper=mock(ErpDecisionPurchaseScopeMapper.class);qualification=mock(PuRequirementErpTransactionQualification.class);
        locks=mock(PuRequirementMaterialLockService.class);guard=new ErpDecisionPurchaseScopeGuard(mapper,qualification,locks);
        when(mapper.pinScopeDefinition()).thenReturn(List.of());when(mapper.scopeTableEngine()).thenReturn("InnoDB");
        var work=work(11L,"MATERIAL_REQUIREMENT",51L);
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(work);
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of(work));
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,1L,"0",31L,"CONFIRMED","0")));
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0)));
    }
    @AfterEach void close() {tenants.close();}
    private void check() {guard.requireLocked(TENANT,root,139L,9L,Set.of(21L),DAY);}
    @Test void acceptsUniqueDirectMrpOnlyAfterExistingMaterialGuard() {
        assertDoesNotThrow(this::check,"唯一直接MRP必须能通过真实归属规则");
        var order=inOrder(qualification,locks,mapper);
        order.verify(qualification).requireSourceChainTransaction();
        order.verify(locks).lockExisting(TENANT,List.of(139L));
        order.verify(mapper).pinScopeDefinition();
        verify(mapper).lockDirectWorkOrders(TENANT,List.of(51L));
    }
    @Test void acceptsOnlyUniqueExplicitPlanFallback() {
        var work=work(11L,"PLAN",31L);when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(work);
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of());
        assertDoesNotThrow(this::check,"唯一且明确PLAN来源才允许计划后备归属");
    }
    @Test void rejectsAmbiguousPlanAndDuplicateDirectSources() {
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work(11L,"PLAN",31L),work(12L,"PLAN",31L)));
        assertThrows(SourceChangedException.class,this::check,"同计划多工单不得借快照缩小候选");
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(work(11L,"MATERIAL_REQUIREMENT",51L)));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of(work(11L,"MATERIAL_REQUIREMENT",51L),work(12L,"MATERIAL_REQUIREMENT",51L)));
        assertThrows(SourceChangedException.class,this::check,"重复直接需求关联不能按最小ID挑选");
    }
    @Test void crossTenantOrTerminalRootIsDenied() {
        assertThrows(ServiceException.class,()->guard.requireLocked("111111",root,139L,9L,Set.of(21L),DAY),"跨租户须在查询前拒绝");
        var done=new WorkSource(11L,TENANT,31L,"MATERIAL_REQUIREMENT",51L,"COMPLETED","0");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(done);when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(done));
        assertThrows(SourceChangedException.class,this::check,"新建时已完工根工单不可采购");
    }
    @Test void unknownEngineAndIncompleteRowsFailClosed() {
        when(mapper.scopeTableEngine()).thenReturn("MyISAM");
        assertThrows(ServiceException.class,this::check,"新增根工单来源必须是认证事务引擎");
        when(mapper.scopeTableEngine()).thenReturn("InnoDB");when(mapper.lockRequirementSources(any(),any(),any(),any())).thenReturn(null);
        assertThrows(ServiceException.class,this::check,"读取失败不能解释为来源变化或零额度");
    }
    @Test void choosesOnlyUniqueActiveOrUniqueRealDefaultVendor() {
        assertEquals(9L,guard.selectVendorLocked(TENANT,139L),"唯一活跃关系可直接选择");
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0),vendor(72L,10L,1)));
        assertEquals(10L,guard.selectVendorLocked(TENANT,139L),"多活跃关系仅能选择唯一真实默认供应商");
        assertThrows(SourceChangedException.class,this::check,"默认供应商变化后旧合同不得继续采购原供应商");
    }
    @Test void ambiguousOrDuplicateVendorRelationshipsNeverPickFirst() {
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0),vendor(72L,10L,0)));
        assertThrows(ServiceException.class,()->guard.selectVendorLocked(TENANT,139L),"没有唯一默认供应商应拒绝");
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,1),vendor(72L,9L,0)));
        assertThrows(ServiceException.class,()->guard.selectVendorLocked(TENANT,139L),"重复关系不得伪装唯一供应商");
    }
    @Test void currentDraftMrpHeadDoesNotRequireInventedConfirmationStage() {
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,1L,"0",31L,"DRAFT","0")));
        assertDoesNotThrow(this::check,"真实认证支持的DRAFT需求头不必凭空升级为CONFIRMED");
    }
    @Test void rootAndRequirementLocksPrecedeVendorInAllCompleteChecks() {
        check();
        var order=inOrder(mapper);
        order.verify(mapper).lockPlanWorkOrders(TENANT,31L);
        order.verify(mapper).lockRequirementSources(TENANT,139L,List.of(21L),DAY);
        order.verify(mapper).lockDirectWorkOrders(TENANT,List.of(51L));
        order.verify(mapper).lockVendorChoices(TENANT,139L);
    }
    @Test void unknownSourceLinkIsUnavailableNotStale() {
        var unknown=new WorkSource(11L,TENANT,31L,null,null,"RELEASED","0");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(unknown);when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(unknown));
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of());
        assertThrows(ServiceException.class,this::check,"未知根来源类型不是已证明来源变化");
    }
    @Test void unknownRootStatusAndWrongTenantRowsStayOrdinarySafetyErrors() {
        var unknown=new WorkSource(11L,TENANT,31L,"MATERIAL_REQUIREMENT",51L,null,"0");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(unknown);
        assertThrows(ServiceException.class,this::check,"未知根状态不得自动记STALE");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(work(11L,"MATERIAL_REQUIREMENT",51L));
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,"111111",51L,139L,"4",DAY,1L,"0",31L,"CONFIRMED","0")));
        assertThrows(ServiceException.class,this::check,"跨租户错误保持权限安全异常");
    }
    @Test void positiveReadOfClosedRequirementIsSourceChanged() {
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,1L,"0",31L,"CLOSED","0")));
        assertThrows(SourceChangedException.class,this::check,"可信当前读确认MRP已闭合才可判来源变化");
    }
    @Test void duplicateDirectLinkOutsideRootPlanIsStillDenied() {
        when(mapper.lockDirectWorkOrders(TENANT,List.of(51L))).thenReturn(List.of(work(11L,"MATERIAL_REQUIREMENT",51L),
            new WorkSource(12L,TENANT,99L,"MATERIAL_REQUIREMENT",51L,"RELEASED","0")));
        assertThrows(SourceChangedException.class,this::check,"完整租户的其他计划重复直链不能被根计划过滤隐藏");
    }
    @Test void unknownDeletionAndStateFlagsAreSafetyErrorsNotProofOfChange() {
        var unknown=new WorkSource(11L,TENANT,31L,"MATERIAL_REQUIREMENT",51L,"RELEASED","未知");
        when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(unknown);when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(unknown));
        assertThrows(ServiceException.class,this::check,"未知工单删除标志不能解释为已删除或STALE");
        var valid=work(11L,"MATERIAL_REQUIREMENT",51L);when(mapper.findRootWorkOrder(TENANT,11L)).thenReturn(valid);
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(valid));
        when(mapper.lockRequirementSources(TENANT,139L,List.of(21L),DAY)).thenReturn(List.of(
            new RequirementSource(21L,TENANT,51L,139L,"4",DAY,99L,"0",31L,"CONFIRMED","0")));
        assertThrows(ServiceException.class,this::check,"未知MRP行状态不得冒充明确闭合变化");
    }
    @Test void unknownActiveVendorStatusCannotSilentlyLeaveAUniqueAlternative() {
        when(mapper.lockVendorChoices(TENANT,139L)).thenReturn(List.of(vendor(71L,9L,0),
            new VendorChoice(72L,TENANT,139L,10L,1,1,"0","未知","0")));
        assertThrows(ServiceException.class,()->guard.selectVendorLocked(TENANT,139L),"未知活跃供应商状态不能被删除后猜唯一剩余关系");
    }
    @Test void unreadableMapperFactsAreSanitizedAsUnavailableNotStale() {
        when(mapper.findRootWorkOrder(TENANT,11L)).thenThrow(new IllegalStateException("底层连接与SQL错误"));
        var failure=assertThrows(ServiceException.class,this::check,"无法读取来源必须普通安全拒绝而非来源变化");
        assertFalse(failure.getMessage().contains("SQL"),"不能暴露底层来源查询错误内容");
    }
    @Test void planLocatorRaceIsNotFollowedToAnotherPlan() {
        when(mapper.lockPlanWorkOrders(TENANT,31L)).thenReturn(List.of(new WorkSource(11L,TENANT,32L,"MATERIAL_REQUIREMENT",51L,"RELEASED","0")));
        assertThrows(SourceChangedException.class,this::check,"定位后的计划漂移应拒绝而不是追逐新计划");
        verify(mapper,never()).lockPlanWorkOrders(TENANT,32L);
    }


    /** 根读取原类型及有限业务帧必须在净化前可观察，不以丢失原异常内容换取日志。 */
    @Test void unknownRootReadLogsOriginalTypeAndOnlyThreeBusinessFramesBeforeSanitizing() {
        var original = new SensitiveFailure();
        var cause = new SensitiveFailure();
        var suppressed = new SensitiveFailure();
        original.initCause(cause);
        original.addSuppressed(suppressed);
        original.setStackTrace(new StackTraceElement[] {
            frame("java.sql.Statement", "execute", 1),
            frame("com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard$SyntheticRead", "readLocked", 17),
            frame("com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard", "requireSources", 29),
            frame("com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard", "requireRootSourcesLocked", 41),
            frame("com.nexus.erp.pu.service.PuRequirementErpTransactionExecutor", "executeRepeatableRead", 53)
        });
        failNextRead(original);
        try (var capture = new Capture()) {
            assertRootSanitized(assertThrows(ServiceException.class, this::checkRoot));
            assertEquals(0, original.forbiddenReads.get(), "禁止读取原异常内容");
            assertEquals(0, cause.forbiddenReads.get(), "禁止展开 cause");
            assertEquals(0, suppressed.forbiddenReads.get(), "禁止展开 suppressed");
            assertDiagnostic(capture, original.getClass().getName(),
                "[com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard$SyntheticRead#readLocked:17, "
                    + "com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard#requireSources:29, "
                    + "com.nexus.erp.ai.adapter.ErpDecisionPurchaseScopeGuard#requireRootSourcesLocked:41]");
            assertEquals(1, original.stackReads.get(), "只尝试一次原栈提取");
        }
    }

    /** 四个既有入口共用根读取捕获边界，不新增可传入阶段或回调的公开入口。 */
    @ParameterizedTest
    @ValueSource(strings = {"ROOT", "LOCKED", "WORK_ORDER", "VENDOR"})
    void allExistingReadEntriesUseTheFixedPurchaseScopeStage(String entry) {
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[0]);
        failNextRead(original);
        try (var capture = new Capture()) {
            var outward = assertThrows(ServiceException.class, () -> {
                switch (entry) {
                    case "ROOT" -> checkRoot();
                    case "LOCKED" -> check();
                    case "WORK_ORDER" -> guard.requireWorkOrderSourcesLocked(TENANT, root.id(), 139L, 9L, Set.of(21L), DAY);
                    case "VENDOR" -> guard.selectVendorLocked(TENANT, 139L);
                    default -> throw new AssertionError("未知合成入口");
                }
            });
            assertRootSanitized(outward);
            assertDiagnostic(capture, original.getClass().getName(), "[]");
            assertEquals(0, original.forbiddenReads.get());
        }
    }

    /** 扫描上限包括非业务帧，第六十五帧不得进入诊断。 */
    @Test void rootPositionScanStopsAtSixtyFourFrames() {
        var trace = new StackTraceElement[66];
        Arrays.fill(trace, frame("java.sql.Statement", "execute", 1));
        trace[63] = frame("com.nexus.erp.ai.adapter.LastVisibleRead", "readLocked", 64);
        trace[64] = frame("com.nexus.erp.ai.adapter.OutsideDiagnosticScan", "readLocked", 65);
        trace[65] = frame("com.nexus.erp.ai.adapter.OutsideDiagnosticScan", "previewLocked", 66);
        var original = new SensitiveFailure();
        original.setStackTrace(trace);
        failNextRead(original);
        try (var capture = new Capture()) {
            assertRootSanitized(assertThrows(ServiceException.class, this::checkRoot));
            assertDiagnostic(capture, original.getClass().getName(),
                "[com.nexus.erp.ai.adapter.LastVisibleRead#readLocked:64]");
        }
    }

    /** 伪业务前缀、超长及换行名称不可信，文件名、模块和类加载器同样不可输出。 */
    @Test void malformedOverlongAndLookalikeRootFrameNamesAreNotLogged() {
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[] {
            frame("com.nexusx.erp.NotApplication", "readLocked", 1),
            frame("com.nexus.erp.Bad\nFAKE_TOKEN_ONLY", "readLocked", 2),
            frame("com.nexus.erp.Valid", "read\r\nFAKE_TOKEN_ONLY", 3),
            frame("com.nexus." + "x".repeat(241), "readLocked", 4),
            frame("com.nexus.erp.Valid", "x".repeat(81), 5),
            frame("com.nexus.erp.Valid$NestedRead", "<init>", 6)
        });
        failNextRead(original);
        try (var capture = new Capture()) {
            assertRootSanitized(assertThrows(ServiceException.class, this::checkRoot));
            assertDiagnostic(capture, original.getClass().getName(), "[com.nexus.erp.Valid$NestedRead#<init>:6]");
        }
    }

    /** 空栈只保留原类型，不虚构 JDBC 或业务位置。 */
    @Test void emptyRootStackLogsOriginalTypeWithoutInventingPositions() {
        var original = new SensitiveFailure();
        original.setStackTrace(new StackTraceElement[0]);
        failNextRead(original);
        try (var capture = new Capture()) {
            assertRootSanitized(assertThrows(ServiceException.class, this::checkRoot));
            assertDiagnostic(capture, original.getClass().getName(), "[]");
        }
    }

    /** cause 中的可信来源变化类型不构成分类许可，也不得被读取来推断故障。 */
    @Test void typedCauseCannotPromoteUnknownRootFailureIntoSourceChange() {
        var original = new SensitiveFailure();
        original.initCause(new SourceChangedException());
        original.addSuppressed(new SensitiveFailure());
        original.setStackTrace(new StackTraceElement[0]);
        failNextRead(original);
        try (var capture = new Capture()) {
            assertRootSanitized(assertThrows(ServiceException.class, this::checkRoot));
            assertDiagnostic(capture, original.getClass().getName(), "[]");
            assertEquals(0, original.forbiddenReads.get(), "不能读取可信类型的 cause 来升级分类");
        }
    }

    /** 根读取层必须原对象透传既有两种分类，而不是改消息、清空链或重新包装。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void classifiedRootFailuresKeepTheirOriginalObjectWithoutDiagnostic(boolean sourceChanged) {
        RuntimeException original = sourceChanged ? new SourceChangedException() : new ServiceException("合成已分类安全拒绝", 409);
        original.initCause(new IllegalStateException(PRIVATE));
        original.addSuppressed(new IllegalArgumentException(PRIVATE));
        failNextRead(original);
        try (var capture = new Capture()) {
            assertSame(original, assertThrows(RuntimeException.class, this::checkRoot));
            assertTrue(capture.events.list.isEmpty(), "已分类对象不得进入根读取诊断");
        }
    }

    /** 原致命错误不是普通读取不可用，不能进入诊断、净化或新包装分支。 */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void originalRootFatalObjectStaysOutsideRuntimeDiagnosticBranch(boolean threadDeath) {
        Error original = threadDeath ? new ThreadDeath() : new OutOfMemoryError("离线模拟致命对象，不耗尽内存");
        failNextRead(original);
        try (var capture = new Capture()) {
            assertSame(original, assertThrows(Error.class, this::checkRoot));
            assertTrue(capture.events.list.isEmpty(), "原 fatal 不增加诊断操作");
        }
    }

    /** 成功路径不加载失败日志或改变原有来源核验结果。 */
    @Test void successfulRootReadDoesNotEmitFailureDiagnostic() {
        try (var capture = new Capture()) {
            assertDoesNotThrow(this::checkRoot);
            assertTrue(capture.events.list.isEmpty());
        }
    }

    /**
     * getSuppressed 为 final，毒化子类只能证明未泄漏，不能证明未读取。
     * 直接检查已编译的读取捕获与诊断方法调用，补足禁读内容及异常链变更的契约；
     * 不扫描测试对净化后返回对象的断言，也不禁止安全字符串列表的 toString。
     */
    @Test void readAndDiagnosticBytecodeNeverReadsExceptionContentsOrChangesChains() throws IOException {
        var inspected = new HashSet<String>();
        var forbidden = new ArrayList<String>();
        var forbiddenMethods = Set.of("getMessage", "getLocalizedMessage", "getCause", "getSuppressed",
            "initCause", "addSuppressed", "printStackTrace", "setStackTrace", "fillInStackTrace");
        for (Class<?> type : List.of(ErpDecisionPurchaseScopeGuard.class, PuRequirementErpTransactionExecutor.class)) {
            try (InputStream bytecode = type.getResourceAsStream(type.getSimpleName() + ".class")) {
                assertNotNull(bytecode, "必须检查实际编译的生产类，不能跳过契约");
                new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                    @Override public MethodVisitor visitMethod(int access, String name, String descriptor,
                                                             String signature, String[] exceptions) {
                        boolean selected = type == ErpDecisionPurchaseScopeGuard.class
                            ? "readSafely".equals(name) : name.startsWith("diagnos");
                        if (!selected) return null;
                        String method = type.getName() + "#" + name;
                        inspected.add(method);
                        return new MethodVisitor(Opcodes.ASM9) {
                            @Override public void visitMethodInsn(int opcode, String owner, String called,
                                                                  String descriptor, boolean isInterface) {
                                boolean throwableFormatting = "toString".equals(called)
                                    && Set.of("java/lang/Throwable", "java/lang/Exception", "java/lang/RuntimeException",
                                        "java/lang/Error", "java/util/Objects").contains(owner);
                                if (forbiddenMethods.contains(called) || throwableFormatting) {
                                    forbidden.add(method + " -> " + owner + "#" + called);
                                }
                            }
                        };
                    }
                }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
            }
        }
        assertTrue(inspected.contains(ErpDecisionPurchaseScopeGuard.class.getName() + "#readSafely"));
        assertTrue(inspected.contains(PuRequirementErpTransactionExecutor.class.getName() + "#diagnoseFailure"));
        assertTrue(forbidden.isEmpty(), () -> "诊断不能读取或改写原异常内容：" + forbidden);
    }

    /** 使用现有冻结根入口，而不是直接调用尚未添加的诊断转接。 */
    private void checkRoot() {
        guard.requireRootSourcesLocked(TENANT, root, 139L, Set.of(21L), DAY);
    }

    /** Answer 直接抛同一对象，避免 Mockito 的 thenThrow 重写合成栈。 */
    private void failNextRead(Throwable original) {
        doAnswer(call -> { throw original; }).when(mapper).findRootWorkOrder(anyString(), anyLong());
        doAnswer(call -> { throw original; }).when(mapper).lockVendorChoices(anyString(), anyLong());
    }

    private static void assertRootSanitized(ServiceException outward) {
        assertEquals(ServiceException.class, outward.getClass());
        assertEquals(ROOT_SAFE_MESSAGE, outward.getMessage());
        assertNull(outward.getCause());
        assertEquals(0, outward.getSuppressed().length);
        assertNull(outward.getDetailMessage());
    }

    /** 原始事件也必须只含三个安全字符串，不能仅检查格式化输出。 */
    private static void assertDiagnostic(Capture capture, String type, String positions) {
        assertEquals(1, capture.events.list.size(), "缺少净化前的根读取有限诊断，或根诊断重复");
        ILoggingEvent event = capture.events.list.get(0);
        assertEquals(PuRequirementErpTransactionExecutor.class.getName(), event.getLoggerName(), "复用现有 ERP 诊断设施");
        assertEquals(Level.ERROR, event.getLevel());
        assertNull(event.getThrowableProxy(), "logger 不得收到 Throwable");
        Object[] arguments = event.getArgumentArray();
        assertNotNull(arguments);
        assertEquals(3, arguments.length, "仅允许固定阶段、原类型与有限业务帧");
        for (Object argument : arguments) assertInstanceOf(String.class, argument);
        assertEquals(ROOT_STAGE, arguments[0]);
        assertEquals(type, arguments[1]);
        assertEquals(positions, arguments[2]);
        for (String forbidden : List.of(PRIVATE, "FAKE_TOKEN_ONLY", "private_table", "password=", "jdbc:mysql://", TENANT)) {
            assertFalse(event.getFormattedMessage().contains(forbidden), "安全诊断包含合成敏感哨兵或租户身份");
        }
    }

    /** 文件名、模块与类加载器携带假敏感文本，不能借帧的 toString 输出。 */
    private static StackTraceElement frame(String owner, String method, int line) {
        return new StackTraceElement(PRIVATE, PRIVATE, PRIVATE, owner, method, PRIVATE, line);
    }

    /** 内容访问即失败；栈读取计数只允许根诊断尝试一次。 */
    private static final class SensitiveFailure extends RuntimeException {
        final AtomicInteger forbiddenReads = new AtomicInteger();
        final AtomicInteger stackReads = new AtomicInteger();
        SensitiveFailure() { super(PRIVATE); }
        @Override public String getMessage() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原 message"); }
        @Override public String getLocalizedMessage() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原 localizedMessage"); }
        @Override public synchronized Throwable getCause() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止读取原 cause"); }
        @Override public String toString() { forbiddenReads.incrementAndGet(); throw new AssertionError("禁止格式化原 Throwable"); }
        @Override public StackTraceElement[] getStackTrace() { stackReads.incrementAndGet(); return super.getStackTrace(); }
    }

    /** 仅捕获被复用的 ERP 诊断 logger，阻止传播到外部 appender，结束后恢复配置。 */
    private static final class Capture implements AutoCloseable {
        final Logger logger = (Logger) LoggerFactory.getLogger(PuRequirementErpTransactionExecutor.class);
        final ListAppender<ILoggingEvent> events = new ListAppender<>();
        final Level oldLevel = logger.getLevel();
        final boolean oldAdditive = logger.isAdditive();
        Capture() {
            events.setContext(logger.getLoggerContext());
            events.start();
            logger.setLevel(Level.ERROR);
            logger.setAdditive(false);
            logger.addAppender(events);
        }
        @Override public void close() {
            logger.detachAppender(events);
            logger.setLevel(oldLevel);
            logger.setAdditive(oldAdditive);
            events.stop();
        }
    }
}

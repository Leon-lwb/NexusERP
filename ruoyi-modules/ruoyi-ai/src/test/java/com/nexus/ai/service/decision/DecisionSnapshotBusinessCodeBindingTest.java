package com.nexus.ai.service.decision;

import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.decision.dto.*;
import com.nexus.common.ai.decision.enums.*;
import com.nexus.common.ai.domain.entity.*;
import com.nexus.common.ai.mapper.*;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.common.core.utils.SpringUtils;
import com.nexus.common.satoken.utils.LoginHelper;
import com.nexus.common.tenant.helper.TenantHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 复现正常新运行初始根码为空的真实入口形状，不以人工填码的夹具掩盖绑定缺口。 */
class DecisionSnapshotBusinessCodeBindingTest {
    private static final String TENANT = "000007";
    private static final String CODE = "SCGD202610060010";
    private final AiDecisionRunMapper runs = mock(AiDecisionRunMapper.class);
    private final ObjectMapper mapper = new ObjectMapper()
        .registerModule(new com.nexus.common.json.config.JacksonConfig().registerJavaTimeModule())
        .registerModule(new com.nexus.common.web.config.JacksonConfig().bigNumberModule());
    private final DecisionPersistenceService store = new DecisionPersistenceService(runs,
        mock(AiDecisionEvidenceMapper.class), mock(AiDecisionActionMapper.class),
        new DecisionRunStateMachine(), new DecisionEvidenceService(), mapper);

    @BeforeAll static void initializeMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new com.baomidou.mybatisplus.core.MybatisConfiguration(), ""), AiDecisionRun.class);
    }

    @Test void normalStartNullCodeBindsTrustedErpCodeTogetherWithSnapshot() {
        withTenant(() -> {
            var request = new DeliveryRiskRunRequest(); request.setBizType(DecisionBusinessType.WORK_ORDER);
            request.setBizId(901L); request.setHorizonDays(30);
            when(runs.insert(any(AiDecisionRun.class))).thenAnswer(call -> { ((AiDecisionRun)call.getArgument(0)).setId(99L); return 1; });
            var created = store.createRun(request, new DecisionActor(TENANT, 7L, "采购负责人", Set.of("ai:decision:run")));
            assertNull(created.getBizCode(), "正常启动不能信任或构造客户端业务编码");
            created.setStatus("RUNNING"); when(runs.selectOne(any())).thenReturn(created);
            when(runs.update(isNull(), any())).thenReturn(1);
            store.saveSnapshot(99L, snapshot(DecisionBusinessType.WORK_ORDER, 901L, CODE));
            var update = lastUpdate(); String set = update.getSqlSet();
            assertTrue(set.contains("biz_code="), "真实ERP根码必须与快照在一次CAS中绑定");
            assertTrue(set.contains("input_snapshot_json=")); assertTrue(set.contains("input_snapshot_hash="));
            assertTrue(set.contains("snapshot_time=")); assertTrue(update.getParamNameValuePairs().containsValue(CODE));
            String where = update.getSqlSegment();
            assertTrue(where.contains("tenant_id")); assertTrue(where.contains("version")); assertTrue(where.contains("status"));
            assertTrue(where.contains("biz_code IS NULL"), "首次绑定应锁定尚未签入业务根的状态");
        });
    }
    @Test void nullErpCodeCannotBindVersionTwo() { rejectCode(null); }
    @Test void emptyErpCodeCannotBindVersionTwo() { rejectCode(""); }
    @Test void blankErpCodeCannotBindVersionTwo() { rejectCode("   "); }
    @Test void overlongErpCodeCannotReachDatabase() { rejectCode("长".repeat(65)); }
    @Test void knownRunCodeDriftCannotOverwriteBusinessIdentity() {
        withTenant(() -> { var row=row(2); row.setBizCode("SCGD202610060009"); reject(row, snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE)); });
    }
    @Test void previouslyFrozenHashWithoutCodeCannotBeBackfilled() {
        withTenant(() -> { var row=row(2); row.setInputSnapshotHash("既有快照摘要"); reject(row, snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE)); });
    }
    @Test void previouslyFrozenJsonWithoutCodeCannotBeBackfilled() {
        withTenant(() -> { var row=row(2); row.setInputSnapshotJson("{}"); reject(row, snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE)); });
    }
    @Test void previouslyFrozenTimeWithoutCodeCannotBeBackfilled() {
        withTenant(() -> { var row=row(2); row.setSnapshotTime(LocalDateTime.of(2026,10,6,10,0)); reject(row, snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE)); });
    }
    @Test void legacyNullContractDoesNotReceiveNewRootCode() { legacyNoBackfill(null); }
    @Test void legacyVersionOneDoesNotReceiveNewRootCode() { legacyNoBackfill(1); }
    @Test void sameKnownCodeStaysImmutableWhileSnapshotUsesExistingCas() {
        withTenant(() -> {
            var row=row(2); row.setBizCode(CODE); when(runs.selectOne(any())).thenReturn(row);
            when(runs.update(isNull(),any())).thenReturn(1); store.saveSnapshot(99L,snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE));
            assertFalse(lastUpdate().getSqlSet().contains("biz_code="), "已签入相同根码不能再改写");
            assertTrue(lastUpdate().getSqlSegment().contains("biz_code"), "后续快照仍需确认原根码");
        });
    }
    @Test void nonWorkOrderScopeDoesNotGainPurchaseRootCode() {
        withTenant(() -> {
            var row=row(2); row.setBizType("SHORTAGE_HORIZON"); row.setBizId(null);
            when(runs.selectOne(any())).thenReturn(row); when(runs.update(isNull(),any())).thenReturn(1);
            store.saveSnapshot(99L,snapshot(DecisionBusinessType.SHORTAGE_HORIZON,null,null));
            assertFalse(lastUpdate().getSqlSet().contains("biz_code="));
        });
    }
    @Test void rootIdMismatchCannotWriteCodeOrSnapshot() {
        withTenant(() -> reject(row(2),snapshot(DecisionBusinessType.WORK_ORDER,902L,CODE)));
    }
    @Test void bindingCasMissRemainsBusinessFailure() {
        withTenant(() -> { when(runs.selectOne(any())).thenReturn(row(2)); when(runs.update(isNull(),any())).thenReturn(0);
            assertThrows(ServiceException.class,()->store.saveSnapshot(99L,snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE)));
        });
    }
    private void rejectCode(String code) { withTenant(() -> reject(row(2),snapshot(DecisionBusinessType.WORK_ORDER,901L,code))); }
    private void reject(AiDecisionRun row, DeliveryContextSnapshot snapshot) {
        when(runs.selectOne(any())).thenReturn(row); when(runs.update(isNull(),any())).thenReturn(1);
        assertThrows(ServiceException.class,()->store.saveSnapshot(99L,snapshot), "未知或漂移根码不能补齐采购资格");
        verify(runs,never()).update(isNull(),any());
    }
    private void legacyNoBackfill(Integer version) {
        withTenant(() -> { when(runs.selectOne(any())).thenReturn(row(version)); when(runs.update(isNull(),any())).thenReturn(1);
            store.saveSnapshot(99L,snapshot(DecisionBusinessType.WORK_ORDER,901L,CODE));
            assertFalse(lastUpdate().getSqlSet().contains("biz_code="), "旧合同不能回填新版根元数据");
        });
    }
    private AiDecisionRun row(Integer marker) {
        var row=new AiDecisionRun(); row.setId(99L); row.setTenantId(TENANT); row.setSceneCode("DELIVERY_RISK");
        row.setBizType("WORK_ORDER"); row.setBizId(901L); row.setStatus("RUNNING"); row.setVersion(3); row.setDecisionContractVersion(marker);
        return row;
    }
    private DeliveryContextSnapshot snapshot(DecisionBusinessType type, Long id, String code) {
        return new DeliveryContextSnapshot(new DecisionBusinessRef(type,id,code),List.of(),List.of(),Map.of(),List.of(),List.of(),
            List.of(),Map.of(),List.of(),Map.of(),List.of(),"服务器可信快照摘要",LocalDateTime.of(2026,10,6,10,0),List.of());
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private LambdaUpdateWrapper<AiDecisionRun> lastUpdate() {
        var capture=ArgumentCaptor.forClass(Wrapper.class); verify(runs).update(isNull(),capture.capture());
        return (LambdaUpdateWrapper<AiDecisionRun>)capture.getValue();
    }
    private void withTenant(Runnable action) {
        try (MockedStatic<SpringUtil> spring=mockStatic(SpringUtil.class); MockedStatic<LoginHelper> login=mockStatic(LoginHelper.class)) {
            spring.when(()->SpringUtils.getProperty("tenant.enable")).thenReturn("true"); login.when(LoginHelper::isLogin).thenReturn(false);
            try { TenantHelper.dynamic(TENANT,()->{action.run();return null;}); } finally { TenantHelper.clearDynamic(); }
        }
    }
}

package com.nexus.system.runtime;

import static org.junit.jupiter.api.Assertions.*;

import com.nexus.runtime.attestation.RuntimeAttestationAgent;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** 无真实 premain 的当前测试 JVM 不能借助配置、类名或报告构造器签发证明。 */
class LoadedArtifactWitnessTest {
    @Test void assessmentExposesRealAgentSnapshotRatherThanAcceptingCallerWitness() throws Exception {
        var observation = LoadedArtifactAssessment.inspect(LoadedArtifactWitnessTest.class, List.of(), false);
        var method =
                Arrays.stream(observation.getClass().getMethods())
                        .filter(value -> value.getName().equals("witness"))
                        .findFirst();
        assertTrue(method.isPresent(), "system 尚未接入进程内真实见证");
        Object value = method.orElseThrow().invoke(observation);
        assertNotNull(value); assertEquals(RuntimeAttestationAgent.snapshot(LoadedArtifactWitnessTest.class, List.of()).state(),
            value.getClass().getMethod("state").invoke(value));
        assertEquals("NONE", observation.supportedProofDomain()); assertEquals("UNPROVEN", observation.proofStatus());
    }
    @Test void observationDeclaresTheBornV2ClassSetInsteadOfReusingOldBoundary() throws Exception {
        var observation = LoadedArtifactAssessment.inspect(LoadedArtifactWitnessTest.class, List.of(), false);
        Method boundary;
        try { boundary = observation.getClass().getMethod("boundaryContract"); }
        catch (NoSuchMethodException missing) { throw new AssertionError("缺少不可复用旧证明的新 Born V2 边界声明", missing); }
        assertEquals("PROCUREMENT_RECEIPT_IQC_BIRTH_V2", boundary.invoke(observation));
        assertTrue(observation.getClass().getMethod("requiredClassSetSha256").invoke(observation).toString().matches("[0-9a-f]{64}"));
    }
    @Test void reportCannotBeFabricatedAsVerifiedOrBusinessWriteAuthorized() {
        var build = LoadedArtifactAssessment.inspect(LoadedArtifactWitnessTest.class, List.of(), false);
        assertThrows(IllegalArgumentException.class, () -> new RuntimeAttestationReport(2, "VERIFIED", "fixture", false,
            "fixture", "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), null, "fixture", Map.of(), build));
        assertThrows(IllegalArgumentException.class, () -> new RuntimeAttestationReport(2, "BLOCKED", "fixture", true,
            "fixture", "a".repeat(64), "b".repeat(64), "c".repeat(64), "d".repeat(64), null, "fixture", Map.of(), build));
    }
    @Test void declaredProcurementBoundaryIsCompleteAndImmutable() throws Exception {
        Field field;
        try { field = LoadedArtifactAssessment.class.getField("REQUIRED_CLASSES"); }
        catch (NoSuchFieldException missing) { throw new AssertionError("必须绑定采购实际执行边界而非仅一个锚类", missing); }
        @SuppressWarnings("unchecked") List<String> required = (List<String>) field.get(null);
        assertEquals(25, required.size());
        assertTrue(required.contains("com.nexus.erp.pu.service.PuCertifiedPurchaseWorkflowService"));
        assertTrue(required.contains("com.nexus.erp.mq.consumer.PurchaseReceiveWritebackConsumer"));
        assertTrue(required.contains("com.nexus.common.mq.consumer.BaseMessageConsumer"));
        for (String name : List.of("com.nexus.erp.wm.service.WmReceiptIqcBirthService", "com.nexus.mes.qc.service.QcReceiptIqcBirthService",
            "com.nexus.erp.wm.service.WmReceiptIqcEvidenceService", "com.nexus.mes.qc.service.QcReceiptIqcEvidenceService")) assertTrue(required.contains(name), name);
        assertThrows(UnsupportedOperationException.class, required::clear);
    }
}

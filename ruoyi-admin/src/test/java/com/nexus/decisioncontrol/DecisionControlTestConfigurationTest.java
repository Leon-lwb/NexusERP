package com.nexus.decisioncontrol;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.common.ai.decision.dto.DecisionActionExecutionResult;
import com.nexus.common.ai.decision.dto.DecisionActionProposal;
import com.nexus.common.ai.decision.port.IndustrialDecisionDataPort;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Tag("dev")
class DecisionControlTestConfigurationTest {
    @TempDir Path directory;
    private MockEnvironment environment() {
        MockEnvironment env = new MockEnvironment();
        env.setActiveProfiles("dev", "decision-control-test");
        env.setProperty("decision.control.enabled", "true");
        env.setProperty("decision.control.marker-directory", directory.toString());
        env.setProperty("spring.data.redis.host", "127.0.0.1");
        env.setProperty("spring.data.redis.port", "6386");
        env.setProperty("spring.rabbitmq.host", "127.0.0.1");
        env.setProperty("spring.rabbitmq.port", "5685");
        env.setProperty("spring.rabbitmq.virtual-host", "decision-control-20260927");
        for (String name : new String[]{"master", "erp", "mes", "iot"})
            env.setProperty("spring.datasource.dynamic.datasource." + name + ".url",
                "jdbc:mysql://127.0.0.1:13307/nexus_ai_decision_control_20260927_" +
                    (name.equals("master") ? "sys" : name) + "?useUnicode=true");
        return env;
    }
    @Test void refusesProductionProfilesMissingSwitchAndAnyNonIsolatedDependency() {
        var inactive = environment(); inactive.setActiveProfiles("dev");
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(inactive));
        var env = environment();
        env.setProperty("decision.control.enabled", "false");
        var disabled = env;
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(disabled));
        for (String ds : new String[]{"master", "erp", "mes", "iot"}) {
            var unsafe = environment(); unsafe.setProperty("spring.datasource.dynamic.datasource." + ds + ".url",
                "jdbc:mysql://127.0.0.1:3306/ry-erp");
            assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(unsafe));
        }
        var pubsub = environment(); pubsub.setProperty("spring.data.redis.port", "6385");
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(pubsub));
        var mq = environment(); mq.setProperty("spring.rabbitmq.virtual-host", "/");
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(mq));
    }
    @Test void delegatesRealWritesVerificationAndCompensationWithoutAnyMarker() {
        var port = mock(IndustrialDecisionDataPort.class);
        var action = mock(DecisionActionProposal.class);
        when(action.actionId()).thenReturn(12L);
        var result = new DecisionActionExecutionResult(true, false, "PURCHASE_REQUEST", 34L, "TEST", Map.of(), null, null);
        when(port.executeBusinessAction(action, null)).thenReturn(result);
        when(port.verifyBusinessAction(action, result, null)).thenReturn(result);
        when(port.compensateBusinessAction(action, result, null)).thenReturn(result);
        var proxy = (IndustrialDecisionDataPort) DecisionControlTestConfiguration.processor(environment())
            .postProcessAfterInitialization(port, "port");
        assertSame(result, proxy.executeBusinessAction(action, null));
        assertSame(result, proxy.verifyBusinessAction(action, result, null));
        assertSame(result, proxy.compensateBusinessAction(action, result, null));
        verify(port).executeBusinessAction(action, null);
        verify(port).verifyBusinessAction(action, result, null);
        verify(port).compensateBusinessAction(action, result, null);
    }
    @Test void executeInjectionDoesNotWriteAndVerificationInjectionIsAfterRealVerification() throws Exception {
        var port = mock(IndustrialDecisionDataPort.class);
        var action = mock(DecisionActionProposal.class); when(action.actionId()).thenReturn(12L);
        var proxy = (IndustrialDecisionDataPort) DecisionControlTestConfiguration.processor(environment())
            .postProcessAfterInitialization(port, "port");
        Files.writeString(directory.resolve("fault.txt"), "12:EXECUTE_FAIL");
        assertThrows(IllegalStateException.class, () -> proxy.executeBusinessAction(action, null));
        verifyNoInteractions(port);
        var result = new DecisionActionExecutionResult(true, false, "PURCHASE_REQUEST", 34L, "TEST", Map.of(), null, null);
        when(port.verifyBusinessAction(action, result, null)).thenReturn(result);
        Files.writeString(directory.resolve("fault.txt"), "12:VERIFY_FAIL");
        assertThrows(IllegalStateException.class, () -> proxy.verifyBusinessAction(action, result, null));
        verify(port).verifyBusinessAction(action, result, null);
        assertEquals("34", Files.readString(directory.resolve("12.observed")));
        proxy.compensateBusinessAction(action, result, null);
        verify(port).compensateBusinessAction(action, result, null);
    }
    @Test void markerCannotSelectOtherActionsOrInventPhasesAndErrorsDoNotEchoInput() throws Exception {
        var port = mock(IndustrialDecisionDataPort.class);
        var action = mock(DecisionActionProposal.class); when(action.actionId()).thenReturn(12L);
        var proxy = (IndustrialDecisionDataPort) DecisionControlTestConfiguration.processor(environment())
            .postProcessAfterInitialization(port, "port");
        Files.writeString(directory.resolve("fault.txt"), "13:EXECUTE_FAIL");
        proxy.executeBusinessAction(action, null); verify(port).executeBusinessAction(action, null);
        Files.writeString(directory.resolve("fault.txt"), "12:sk-SENSITIVE");
        var error = assertThrows(IllegalStateException.class, () -> proxy.executeBusinessAction(action, null));
        assertFalse(error.getMessage().contains("SENSITIVE"));
    }
    @Test void productionProfileRemoteMqOrMixedSchemaDatesAreRefusedEvenWithTestSwitch() {
        var prod = environment(); prod.setActiveProfiles("prod", "decision-control-test");
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(prod));
        var remote = environment(); remote.setProperty("spring.rabbitmq.host", "example.org");
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(remote));
        var mixed = environment();
        mixed.setProperty("spring.datasource.dynamic.datasource.erp.url",
            "jdbc:mysql://127.0.0.1:13307/nexus_ai_decision_control_20260926_erp");
        assertThrows(IllegalStateException.class, () -> DecisionControlTestConfiguration.processor(mixed));
    }

    @Test void pausedPostVerifyInjectionWaitsForLocalReleaseAfterRealSuccessfulVerification() throws Exception {
        var port = mock(IndustrialDecisionDataPort.class);
        var action = mock(DecisionActionProposal.class); when(action.actionId()).thenReturn(12L);
        var result = new DecisionActionExecutionResult(true, false, "PURCHASE_REQUEST", 34L, "TEST", Map.of(), null, null);
        when(port.verifyBusinessAction(action, result, null)).thenReturn(result);
        var proxy = (IndustrialDecisionDataPort) DecisionControlTestConfiguration.processor(environment())
            .postProcessAfterInitialization(port, "port");
        Files.writeString(directory.resolve("fault.txt"), "12:VERIFY_PAUSE_FAIL");
        var worker = Executors.newSingleThreadExecutor();
        try {
            var task = worker.submit(() -> assertThrows(IllegalStateException.class,
                () -> proxy.verifyBusinessAction(action, result, null)));
            long deadline = System.nanoTime() + 5_000_000_000L;
            while (!Files.exists(directory.resolve("12.observed")) && System.nanoTime() < deadline) Thread.sleep(10);
            assertEquals("34", Files.readString(directory.resolve("12.observed")));
            assertFalse(task.isDone());
            Files.writeString(directory.resolve("12.release"), "release");
            task.get(5, TimeUnit.SECONDS);
        } finally { worker.shutdownNow(); }
        verify(port).verifyBusinessAction(action, result, null);
    }

}

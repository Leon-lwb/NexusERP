package com.nexus.ai.service.governance;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.nexus.ai.service.sse.AiChatSseLifecycle;
import com.nexus.ai.service.sse.AiSseMessageHelper;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.mapper.AiMessageMapper;

import org.junit.jupiter.api.*;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

class AiChatExecutionServiceTest {
    @Test
    void remoteCancelRemainsPendingUntilWorkerConfirmsExit() throws Exception {
        var fixture = new AiExecutionRepositoryTest();
        fixture.setup();
        var props = new AiGovernanceProperties();
        props.setEnabled(true);
        props.setChatEnabled(true);
        var messages = mock(AiMessageMapper.class);
        var owner = new AiChatExecutionService(fixture.repo, props, messages);
        var remote = new AiChatExecutionService(fixture.repo, props, messages);
        var managed = owner.prepare("000001", 7L, "fixture", 9L, 11L, "client-1");
        remote.cancel("000001", 7L, managed.id());
        assertThat(managed.cancelRequested()).isTrue();
        assertThat(fixture.repo.owned("000001", 7L, managed.id()).get("status"))
                .isEqualTo("CANCEL_REQUESTED");
        assertThat(managed.succeed()).isFalse();
        assertThat(managed.cancelComplete()).isTrue();
        assertThat(fixture.repo.owned("000001", 7L, managed.id()).get("status"))
                .isEqualTo("CANCELLED");
        verifyNoInteractions(messages);
    }

    @Test
    void terminalRequestsAreNotReopenedByRepeatedCancel() throws Exception {
        var f = new AiExecutionRepositoryTest();
        f.setup();
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setChatEnabled(true);
        var service = new AiChatExecutionService(f.repo, p, mock(AiMessageMapper.class));
        var m = service.prepare("000001", 7L, "fixture", 9L, 11L, null);
        assertThat(m.succeed()).isTrue();
        service.cancel("000001", 7L, m.id());
        assertThat(service.status("000001", 7L, m.id()).get("status")).isEqualTo("SUCCEEDED");
        assertThatThrownBy(() -> service.cancel("000001", 8L, m.id()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expiredExecutionCannotRunMessagePersistenceCallback() throws Exception {
        var f = new AiExecutionRepositoryTest();
        f.setup();
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setChatEnabled(true);
        var service = new AiChatExecutionService(f.repo, p, mock(AiMessageMapper.class));
        var m = service.prepare("000001", 7L, "fixture", 9L, 11L, "client-1234567890123456");
        f.clock.advance(31);
        f.repo.recoverAbandoned("CHAT");
        var writes = new AtomicInteger();
        try (var sse = mockStatic(AiSseMessageHelper.class)) {
            var lifecycle = new AiChatSseLifecycle(7L, 9L, m.clientRequestId(), m.id(), m);
            assertThat(lifecycle.complete(writes::incrementAndGet)).isFalse();
            assertThat(writes).hasValue(0);
        }
    }

    @Test
    void observerFailureDoesNotConfirmAStillRunningProducer() throws Exception {
        var f = new AiExecutionRepositoryTest();
        f.setup();
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setChatEnabled(true);
        var service = new AiChatExecutionService(f.repo, p, mock(AiMessageMapper.class));
        var m = service.prepare("000001", 7L, "fixture", 9L, 11L, "client-1234567890123456");
        try (var sse = mockStatic(AiSseMessageHelper.class)) {
            var lifecycle = new AiChatSseLifecycle(7L, 9L, m.clientRequestId(), m.id(), m);
            service.cancel("000001", 7L, m.id());
            lifecycle.eventFailure(new RejectedExecutionException());
            assertThat(service.status("000001", 7L, m.id()).get("status"))
                    .isEqualTo("CANCEL_REQUESTED");
            var entered = new AtomicInteger();
            lifecycle.run(entered::incrementAndGet);
            assertThat(entered).hasValue(0);
            assertThat(service.status("000001", 7L, m.id()).get("status")).isEqualTo("CANCELLED");
        }
    }

    @Test
    void messageAndTerminalRollbackTogetherAndCancellationCanRetryPersistence() throws Exception {
        var f = new AiExecutionRepositoryTest();
        f.setup();
        f.jdbc.execute(
                "CREATE TABLE ai_message(id bigint PRIMARY KEY,tenant_id varchar(20),session_id"
                        + " bigint,role varchar(32),content longtext,thinking_content longtext,model_id"
                        + " bigint,input_tokens int,output_tokens int,token_usage bigint,create_by"
                        + " bigint,create_time timestamp,update_by bigint,update_time timestamp)");
        var p = new AiGovernanceProperties();
        p.setEnabled(true);
        p.setChatEnabled(true);
        var service = new AiChatExecutionService(f.repo, p, mock(AiMessageMapper.class));
        var m = service.prepare("000001", 7L, "fixture", 9L, 11L, null);
        m.partial("visible");
        assertThatThrownBy(
                        () ->
                                m.complete(
                                        () -> {
                                            var message = new AiMessage();
                                            message.setSessionId(9L);
                                            message.setRole("ASSISTANT");
                                            message.setContent("complete");
                                            m.persist(message);
                                            throw new IllegalStateException("rollback");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(f.jdbc.queryForObject("SELECT COUNT(*) FROM ai_message", Integer.class))
                .isZero();
        assertThat(service.status("000001", 7L, m.id()).get("status")).isEqualTo("RUNNING");
        service.cancel("000001", 7L, m.id());
        assertThat(m.cancelComplete()).isTrue();
        assertThat(f.jdbc.queryForObject("SELECT content FROM ai_message", String.class))
                .isEqualTo("visible");
    }
}

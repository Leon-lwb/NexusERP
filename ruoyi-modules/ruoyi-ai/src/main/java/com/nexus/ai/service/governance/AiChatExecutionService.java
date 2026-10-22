package com.nexus.ai.service.governance;

import com.nexus.ai.context.AiBusinessContextScope;
import com.nexus.ai.context.AiRequestContext;
import com.nexus.common.ai.domain.entity.AiMessage;
import com.nexus.common.ai.governance.*;
import com.nexus.common.ai.mapper.AiMessageMapper;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** 聊天的持久化控制与本节点资源登记。远端取消以数据库状态为准。 */
@Service
public class AiChatExecutionService {
    private final AiExecutionRepository repository;
    private final AiGovernanceProperties properties;
    private final AiMessageMapper messages;
    private final String owner = UUID.randomUUID().toString();
    private final ConcurrentMap<String, Managed> active = new ConcurrentHashMap<>();

    public AiChatExecutionService(
            AiExecutionRepository repository,
            AiGovernanceProperties properties,
            AiMessageMapper messages) {
        this.repository = repository;
        this.properties = properties;
        this.messages = messages;
    }

    public void enrichHistory(String tenant, Long user, Long session, List<AiMessage> messages) {
        if (enabled()) repository.enrichHistory(tenant, user, session, messages);
    }

    public boolean enabled() {
        return properties.chatActive();
    }

    public Managed prepare(
            String tenant,
            Long user,
            String name,
            Long session,
            Long model,
            String clientRequestId) {
        if (!enabled()) throw new IllegalStateException("持久化聊天未启用，请先完成第二阶段数据库迁移");
        String id = repository.create(tenant, user, "CHAT", "", session, "{}", 600);
        var lease = repository.claim(tenant, id, owner);
        if (lease == null) {
            repository.rejected(tenant, id);
            throw new IllegalStateException("无法取得聊天执行权");
        }
        var managed = new Managed(lease, name, model, clientRequestId);
        active.put(id, managed);
        return managed;
    }

    public Managed local(String id) {
        return active.get(id);
    }

    public Map<String, Object> status(String tenant, Long user, String id) {
        if (!enabled()) throw new IllegalStateException("持久化聊天未启用");
        var row = repository.owned(tenant, user, id);
        if (!"CHAT".equals(row.get("kind"))) throw new IllegalArgumentException("聊天请求不存在");
        return safe(row);
    }

    public Map<String, Object> cancel(String tenant, Long user, String id) {
        status(tenant, user, id);
        var value = repository.cancel(tenant, user, id);
        var local = active.get(id);
        if (local != null) local.notifyCancel();
        return safe(value);
    }

    public void transport(String tenant, String request, String call, Runnable cancel) {
        var m = active.get(request);
        if (m != null && m.lease.tenant().equals(tenant)) m.transport(call, cancel);
    }

    public void untransport(String request, String call) {
        var m = active.get(request);
        if (m != null) m.untransport(call);
    }

    private Map<String, Object> safe(Map<String, Object> row) {
        var out = new LinkedHashMap<String, Object>();
        out.put("requestId", row.get("execution_id"));
        out.put("sessionId", row.get("session_id"));
        out.put("status", row.get("status"));
        out.put("messageId", row.get("message_id"));
        return out;
    }

    /** 1秒轮询是跨节点信号的可靠兜底，不能据通知丢失而恢复已取消任务。 */
    @Scheduled(fixedDelay = 1000)
    public void observeCancellation() {
        if (!enabled()) return;
        for (var m : active.values())
            try {
                if (m.cancelRequested()) m.notifyCancel();
            } catch (RuntimeException ignored) {
                /* DB不可用不假定取消成功；下一轮重试。 */
            }
    }

    @Scheduled(fixedDelay = 10000)
    public void heartbeat() {
        if (!enabled()) return;
        for (var m : active.values())
            try {
                if (!repository.heartbeat(m.lease)) active.remove(m.id(), m);
            } catch (RuntimeException ignored) {
                /* 租约到期后禁止提交。 */
            }
    }

    @Scheduled(fixedDelay = 5000)
    public void recover() {
        if (enabled()) repository.recoverAbandoned("CHAT");
    }

    public final class Managed {
        private final AiExecutionRepository.Lease lease;
        private final Long model;
        private final String clientRequestId;
        private final AiInvocationScope.Snapshot scope;
        private final ConcurrentMap<String, Runnable> transports = new ConcurrentHashMap<>();
        private final AtomicBoolean cancelNotified = new AtomicBoolean();
        private volatile Runnable onCancel = () -> {};
        private volatile String partial = "";
        private volatile Long message;
        private long lastSaved;

        Managed(
                AiExecutionRepository.Lease lease,
                String name,
                Long model,
                String clientRequestId) {
            this.lease = lease;
            this.model = model;
            this.clientRequestId = clientRequestId;
            scope =
                    new AiInvocationScope.Snapshot(
                            new AiCallContext(
                                    lease.tenant(),
                                    lease.userId(),
                                    name,
                                    lease.id(),
                                    lease.sessionId(),
                                    null,
                                    null,
                                    null,
                                    "CHAT"),
                            () -> repository.check(lease));
        }

        public String id() {
            return lease.id();
        }

        public AiInvocationScope open() {
            return AiInvocationScope.open(scope);
        }

        public void run(Runnable task) {
            try (var business =
                            AiBusinessContextScope.open(
                                    AiRequestContext.trusted(
                                            lease.tenant(),
                                            lease.userId(),
                                            scope.context().userName(),
                                            lease.sessionId(),
                                            model,
                                            null,
                                            null));
                    var ignored = open()) {
                scope.checkActive();
                task.run();
            }
        }

        public boolean cancelRequested() {
            var r = repository.get(lease.tenant(), id());
            return ((Number) r.get("cancel_requested")).intValue() != 0;
        }

        public void onCancel(Runnable callback) {
            onCancel = callback;
        }

        private void notifyCancel() {
            if (cancelRequested() && cancelNotified.compareAndSet(false, true)) {
                onCancel.run();
                for (var action : transports.values())
                    try {
                        action.run();
                    } catch (RuntimeException ignored) {
                        /* 不能以单个传输失败确认整体停止。 */
                    }
            }
        }

        public void transport(String callId, Runnable cancel) {
            transports.put(callId, cancel);
            if (cancelRequested()) cancel.run();
        }

        public void untransport(String callId) {
            transports.remove(callId);
        }

        public synchronized void partial(String content) {
            partial = content;
            if (System.nanoTime() - lastSaved > TimeUnit.MILLISECONDS.toNanos(250)) {
                repository.partial(lease, partial, message);
                lastSaved = System.nanoTime();
            }
        }

        public void linkedMessage(Long messageId) {
            message = messageId;
            repository.partial(lease, partial, message);
        }

        public boolean succeed() {
            boolean ok = repository.finish(lease, "SUCCEEDED", null);
            if (ok) active.remove(id(), this);
            return ok;
        }

        /** 只由实际工作退出的回调调用，HTTP取消接口不会调用此方法。 */
        private void persistPartial() {
            if (message == null && !partial.isBlank()) {
                var value = new AiMessage();
                value.setSessionId(lease.sessionId());
                value.setModelId(model);
                value.setRole("ASSISTANT");
                value.setContent(partial);
                repository.insertAssistant(lease, value);
                message = value.getId();
            }
            repository.partial(lease, partial, message);
        }

        public void persist(AiMessage value) {
            repository.insertAssistant(lease, value);
            message = value.getId();
            partial = value.getContent() == null ? "" : value.getContent();
        }

        public Long messageId() {
            return message;
        }

        public synchronized String complete(Runnable successful) {
            Long previous = message;
            String previousContent = partial;
            try {
                String state =
                        repository.completeAtomically(
                                lease, "SUCCEEDED", successful, this::persistPartial);
                active.remove(id(), this);
                return state;
            } catch (RuntimeException error) {
                message = previous;
                partial = previousContent;
                throw error;
            }
        }

        public synchronized boolean cancelComplete() {
            String state =
                    repository.completeAtomically(
                            lease, "CANCELLED", () -> {}, this::persistPartial);
            active.remove(id(), this);
            return "CANCELLED".equals(state);
        }

        public synchronized String fail(Throwable error) {
            String state =
                    repository.completeAtomically(lease, "FAILED", () -> {}, this::persistPartial);
            active.remove(id(), this);
            return state;
        }

        public String clientRequestId() {
            return clientRequestId;
        }
    }
}

package com.nexus.common.trace;

import com.nexus.common.trace.port.*;
import java.util.*;

/**
 * 调用方提供已认证的传输租户，并在目标事务开始前完成跨库来源准备。
 */
public final class TraceDeliveryCoordinator {
    private final Map<String, TraceInboxPort> inboxes = new HashMap<>();
    private final List<TraceEventHandler> handlers;
    private final Map<String, TraceSourceVerificationPort> verifiers = new HashMap<>();

    public TraceDeliveryCoordinator(
            List<TraceInboxPort> inboxes,
            List<TraceEventHandler> handlers,
            List<TraceSourceVerificationPort> verifiers) {
        for (var inbox : inboxes) {
            if (this.inboxes.putIfAbsent(inbox.targetModule(), inbox) != null) {
                throw new IllegalArgumentException("追溯目标模块存在重复事务入口");
            }
        }
        this.handlers = List.copyOf(handlers);
        for (var verifier : verifiers) {
            if (this.verifiers.putIfAbsent(verifier.sourceModule(), verifier) != null) {
                throw new IllegalArgumentException("事件来源校验器重复");
            }
        }
    }

    /**
     * 以认证租户编排来源实证与目标接收；外部准备在目标事务前完成，重放返回原已提交结果。
     *
     * @param event 已绑定认证租户并核验来源头的不可变事件。
     * @return 本次投递的目标处理状态及结果；重复事件返回原保存结果。
     */
    public TraceDeliveryResult deliver(String target, MaterialTraceEvent event) {
        if (!Set.of("ERP", "MES").contains(target) || target.equals(event.sourceModule())) {
            throw new IllegalArgumentException("事件投递目标无效");
        }
        TraceInboxPort inbox = Objects.requireNonNull(inboxes.get(target), "事件目标未就绪");
        var matches =
                handlers.stream()
                        .filter(
                                h ->
                                        target.equals(h.targetModule())
                                                && h.supports(event.eventType()))
                        .toList();
        if (matches.size() > 1 || matches.isEmpty() && !"TRACE_APPLIED".equals(event.eventType())) {
            throw new IllegalArgumentException("没有唯一的追溯事件处理器");
        }
        TraceEventHandler handler = matches.isEmpty() ? null : matches.get(0);
        TraceSourceVerificationPort verifier =
                Objects.requireNonNull(verifiers.get(event.sourceModule()), "事件来源凭据校验未就绪");
        verifier.verify(event);
        TraceDeliveryResult stored = inbox.findApplied(event);
        if (stored != null) {
            if (!stored.applied()) {
                throw new IllegalStateException("追溯重放探测返回非终态");
            }
            // 重放时在所属库写事务中返回原回执，避免重读已经变化的外部状态。
            return inbox.accept(event, handler, null);
        }
        Object prepared = handler == null ? null : handler.prepare(event);
        return inbox.accept(event, handler, prepared);
    }
}

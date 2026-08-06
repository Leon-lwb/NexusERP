package com.nexus.ai.flow.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 流程停止注册表（协作式取消，对齐 n8n AbortController 语义）
 * <p>引擎在节点边界检查 stop 标志；实例结束（无论结局）后 unregister 防泄漏</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class FlowStopRegistry {

    /** instanceId -> 停止标志 */
    private final Map<Long, AtomicBoolean> flags = new ConcurrentHashMap<>();

    /**
     * 注册实例
     *
     * @param instanceId 实例ID
     */
    public void register(Long instanceId) {
        flags.put(instanceId, new AtomicBoolean(false));
    }

    /**
     * 注销实例（实例结束后必须调用，防泄漏）
     *
     * @param instanceId 实例ID
     */
    public void unregister(Long instanceId) {
        flags.remove(instanceId);
    }

    /**
     * 请求停止
     *
     * @param instanceId 实例ID
     * @return 是否首次请求成功（已停止则返回 false）
     */
    public boolean requestStop(Long instanceId) {
        AtomicBoolean flag = flags.get(instanceId);
        if (flag == null) {
            return false;
        }
        return flag.compareAndSet(false, true);
    }

    /**
     * 是否已请求停止
     *
     * @param instanceId 实例ID
     * @return 是否停止
     */
    public boolean isStopped(Long instanceId) {
        AtomicBoolean flag = flags.get(instanceId);
        return flag != null && flag.get();
    }
}

package com.nexus.ai.flow.node;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 流程节点注册中心
 * <p>@PostConstruct 收集所有 FlowNodeProvider Bean（类比 BuiltinToolRegistry），
 * 供引擎按类型取执行器、供 /ai/flow/node-types 输出节点元数据</p>
 *
 * @author NexusERP
 */
@Slf4j
@Component
public class FlowNodeRegistry {

    private final List<FlowNodeProvider> providers;
    private final Map<String, FlowNodeProvider> registry = new ConcurrentHashMap<>();

    public FlowNodeRegistry(List<FlowNodeProvider> providers) {
        this.providers = providers;
    }

    /**
     * 注册所有 Provider（类型冲突告警）
     */
    @PostConstruct
    public void init() {
        for (FlowNodeProvider provider : providers) {
            FlowNodeProvider existing = registry.putIfAbsent(provider.getNodeType(), provider);
            if (existing != null) {
                log.warn("[Flow] 节点类型冲突: {} 已被 {} 注册，忽略 {}", provider.getNodeType(),
                    existing.getClass().getSimpleName(), provider.getClass().getSimpleName());
            } else {
                log.info("[Flow] 注册节点类型: {} ({})", provider.getNodeType(), provider.getClass().getSimpleName());
            }
        }
    }

    /**
     * 按类型取执行器
     *
     * @param nodeType 节点类型
     * @return 执行器，未注册返回 null
     */
    public FlowNodeProvider getProvider(String nodeType) {
        return registry.get(nodeType);
    }

    /**
     * 全部节点类型是否已注册校验器
     *
     * @param nodeType 节点类型
     * @return 是否已注册
     */
    public boolean contains(String nodeType) {
        return registry.containsKey(nodeType);
    }

    /**
     * 全部执行器
     *
     * @return 执行器集合
     */
    public Collection<FlowNodeProvider> getAll() {
        return registry.values();
    }
}

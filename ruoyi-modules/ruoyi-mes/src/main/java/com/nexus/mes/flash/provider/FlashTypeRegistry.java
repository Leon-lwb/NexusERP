package com.nexus.mes.flash.provider;

import com.nexus.common.core.exception.ServiceException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 刷写类型注册中心(仿 ruoyi-flow FlowNodeRegistry)
 * <p>@PostConstruct 收集所有 FlashTypeProvider Bean, 按 typeCode 注册;
 * 刷写确认/上报时按 flash_type_code 取执行器路由</p>
 *
 * @author Nexus
 */
@Slf4j
@Component
public class FlashTypeRegistry {

    private final List<FlashTypeProvider> providers;
    private final Map<String, FlashTypeProvider> registry = new ConcurrentHashMap<>();

    public FlashTypeRegistry(List<FlashTypeProvider> providers) {
        this.providers = providers;
    }

    @PostConstruct
    public void init() {
        for (FlashTypeProvider provider : providers) {
            FlashTypeProvider existing = registry.putIfAbsent(provider.getTypeCode(), provider);
            if (existing != null) {
                log.warn("[Flash] 刷写类型冲突: {} 已被 {} 注册, 忽略 {}",
                    provider.getTypeCode(), existing.getClass().getSimpleName(), provider.getClass().getSimpleName());
            } else {
                log.info("[Flash] 注册刷写类型: {} ({})", provider.getTypeCode(), provider.getClass().getSimpleName());
            }
        }
    }

    /** 按类型标识取执行器; 未注册抛 ServiceException */
    public FlashTypeProvider getProvider(String typeCode) {
        FlashTypeProvider p = registry.get(typeCode);
        if (p == null) {
            throw new ServiceException("不支持的刷写类型: " + typeCode);
        }
        return p;
    }

    public boolean contains(String typeCode) {
        return registry.containsKey(typeCode);
    }

    public Collection<FlashTypeProvider> getAll() {
        return registry.values();
    }
}

package org.dromara.erp.wm.service.impl;

import lombok.RequiredArgsConstructor;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.erp.wm.service.IPickingStrategyService;
import org.springframework.stereotype.Component;

import java.util.Map;

@RequiredArgsConstructor
@Component
public class PickingStrategyFactory {

    private final Map<String, IPickingStrategyService> strategyMap;

    public IPickingStrategyService getStrategy(String strategyType) {
        String beanName = switch (strategyType) {
            case "FIFO" -> "fifoPickingStrategy";
            case "FEFO" -> "fefoPickingStrategy";
            default -> throw new ServiceException("不支持的拣货策略类型: {}", strategyType);
        };
        IPickingStrategyService strategy = strategyMap.get(beanName);
        if (strategy == null) {
            throw new ServiceException("拣货策略实现不存在: {}", beanName);
        }
        return strategy;
    }
}

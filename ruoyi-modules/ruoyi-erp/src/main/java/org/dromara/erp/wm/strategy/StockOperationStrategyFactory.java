package org.dromara.erp.wm.strategy;

import org.dromara.erp.wm.enums.StockOperationTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存操作策略工厂
 */
@Component
public class StockOperationStrategyFactory {

    private final Map<StockOperationTypeEnum, StockOperationStrategy> strategyMap;

    public StockOperationStrategyFactory(List<StockOperationStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(StockOperationStrategy::getSupportedType, Function.identity()));
    }

    public StockOperationStrategy getStrategy(StockOperationTypeEnum type) {
        StockOperationStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new RuntimeException("不支持的库存操作类型: " + type);
        }
        return strategy;
    }
}

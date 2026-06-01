package com.nexus.erp.wm.strategy;

import com.nexus.erp.wm.enums.StockOperationTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 库存操作策略工厂
 * <p>
 * 根据库存操作类型枚举，获取对应的策略实现类。
 * 采用Spring依赖注入自动收集所有策略实例，构建类型到策略的映射关系。
 *
 * @author nexus
 */
@Component
public class StockOperationStrategyFactory {

    /** 策略映射表，缓存所有已注册的库存操作策略 */
    private final Map<StockOperationTypeEnum, StockOperationStrategy> strategyMap;

    /**
     * 构造策略工厂，自动收集Spring容器中所有的StockOperationStrategy实现
     *
     * @param strategies 库存操作策略列表
     */
    public StockOperationStrategyFactory(List<StockOperationStrategy> strategies) {
        this.strategyMap = strategies.stream()
            .collect(Collectors.toMap(StockOperationStrategy::getSupportedType, Function.identity()));
    }

    /**
     * 根据操作类型获取对应的库存操作策略
     *
     * @param type 库存操作类型枚举
     * @return 对应的策略实现类
     * @throws RuntimeException 当找不到对应策略时抛出异常
     */
    public StockOperationStrategy getStrategy(StockOperationTypeEnum type) {
        StockOperationStrategy strategy = strategyMap.get(type);
        if (strategy == null) {
            throw new RuntimeException("不支持的库存操作类型: " + type);
        }
        return strategy;
    }
}

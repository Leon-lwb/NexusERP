package com.nexus.erp.wm.service.impl;

import lombok.RequiredArgsConstructor;
import com.nexus.common.core.exception.ServiceException;
import com.nexus.erp.wm.service.IPickingStrategyService;
import org.springframework.stereotype.Component;

import java.util.Map;
/**
 * 库存操作策略工厂
 * <p>
 * 根据库存操作类型收集并缓存所有策略实例，提供类型到策略的路由能力。属于 ERP 仓储管理（wm）模块。
 * </p>
 */

@RequiredArgsConstructor
@Component
public class PickingStrategyFactory {

    /** 拣货（拣货相关字段） */
    private final Map<String, IPickingStrategyService> strategyMap;

    /**
     * 获取拣货
     *
     * @param strategyType 策略类型
     * @return 结果
     */
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

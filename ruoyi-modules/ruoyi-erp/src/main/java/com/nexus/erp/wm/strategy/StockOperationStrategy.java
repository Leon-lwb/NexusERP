package com.nexus.erp.wm.strategy;

import com.nexus.erp.wm.enums.StockOperationTypeEnum;

/**
 * 库存操作策略接口
 */
public interface StockOperationStrategy {

    /**
     * 获取策略支持的操作类型
     */
    StockOperationTypeEnum getSupportedType();

    /**
     * 执行库存操作
     *
     * @param args 操作参数，各策略按需解析
     */
    void execute(Object... args);
}

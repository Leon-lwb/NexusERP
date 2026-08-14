package com.nexus.erp.bd.service.impl;

import com.nexus.system.strategy.AutoCodeUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * BD 模块 ServiceImpl 公共依赖聚合器。
 * <p>
 * 收敛 bd 包下 ServiceImpl 重复注入的公共依赖：
 * {@link AutoCodeUtil}。
 * 各 ServiceImpl 注入此单一 Helper 即可替代重复字段声明。
 * </p>
 *
 * @author generated
 * @since 2026-08-14
 */
@Component
@RequiredArgsConstructor
public class BdServiceHelper {

    public final AutoCodeUtil autoCodeUtil;
}
package com.nexus.system.strategy;

import com.nexus.system.domain.SysAutoCodePart;

/**
 * 编码规则片段类型模板接口
 * <p>定义自动编码中各组成片段（如流水号、日期、固定字符、传入字符）的生成策略契约。
 * 各策略实现类通过 Spring 容器注入并由 PartTypeHandler 按类型路由调用。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
public interface PartTypeTemplate {
    /**
     * 根据编码组成配置生成对应的编码片段字符串。
     *
     * @param sysAutoCodePart 编码组成配置（含片段类型、长度、格式等）
     * @return 生成的编码片段
     */
    String partHandle(SysAutoCodePart sysAutoCodePart);
}

package com.nexus.system.strategy;

import com.nexus.system.domain.SysAutoCodePart;

/**
 * 编码规则片段类型模板接口
 *
 * @author wanghuan
 */
public interface PartTypeTemplate {
    String partHandle(SysAutoCodePart sysAutoCodePart);
}

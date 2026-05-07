package org.dromara.system.strategy;

import org.dromara.system.domain.SysAutoCodePart;

/**
 * 编码规则片段类型模板接口
 *
 * @author wanghuan
 */
public interface PartTypeTemplate {
    String partHandle(SysAutoCodePart sysAutoCodePart);
}

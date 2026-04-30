package org.dromara.system.strategy;

import org.dromara.system.domain.SysAutoCodePart;

public interface PartTypeTemplate {
    String partHandle(SysAutoCodePart sysAutoCodePart);
}

package com.nexus.system.strategy;

import com.nexus.system.domain.SysAutoCodePart;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(2)
public class PartTypeFixCharHandler implements PartTypeTemplate {
    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        return sysAutoCodePart.getFixCharacter();
    }
}

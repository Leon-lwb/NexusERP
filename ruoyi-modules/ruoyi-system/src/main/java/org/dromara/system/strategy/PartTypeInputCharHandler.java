package org.dromara.system.strategy;

import cn.hutool.core.lang.Assert;
import org.dromara.system.domain.SysAutoCodePart;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(0)
public class PartTypeInputCharHandler implements PartTypeTemplate {

    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        String inputCharacter = sysAutoCodePart.getInputCharacter();
        Assert.notNull(inputCharacter, "编码规则传入字符不能为空！");
        Assert.isTrue(inputCharacter.length() <= sysAutoCodePart.getPartLength(), "传入字符的长度错误！");
        return inputCharacter;
    }
}

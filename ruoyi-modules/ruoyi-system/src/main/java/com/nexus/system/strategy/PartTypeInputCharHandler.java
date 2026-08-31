package com.nexus.system.strategy;

import cn.hutool.core.lang.Assert;
import com.nexus.system.domain.SysAutoCodePart;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 输入字符处理器
 * <p>按编码组成中用户传入的字符生成片段，校验传入字符非空且不超过组成长度。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Component
@Order(0)
public class PartTypeInputCharHandler implements PartTypeTemplate {

    /**
     * 返回组成配置中的传入字符（inputCharacter）作为编码片段。
     *
     * @param sysAutoCodePart 编码组成配置
     * @return 传入的字符片段
     */
    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        String inputCharacter = sysAutoCodePart.getInputCharacter();
        Assert.notNull(inputCharacter, "编码规则传入字符不能为空！");
        Assert.isTrue(inputCharacter.length() <= sysAutoCodePart.getPartLength(), "传入字符的长度错误！");
        return inputCharacter;
    }
}

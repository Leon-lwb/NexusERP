package com.nexus.system.strategy;

import com.nexus.system.domain.SysAutoCodePart;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 固定字符处理器
 * <p>返回组成配置中的固定字符（fixCharacter）作为编码片段。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Component
@Order(2)
public class PartTypeFixCharHandler implements PartTypeTemplate {

    /**
     * 返回组成配置中的固定字符作为编码片段。
     *
     * @param sysAutoCodePart 编码组成配置（含 fixCharacter 固定字符）
     * @return 固定字符片段
     */
    @Override
    public String partHandle(SysAutoCodePart sysAutoCodePart) {
        return sysAutoCodePart.getFixCharacter();
    }
}

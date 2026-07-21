package com.nexus.system.strategy;

import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.enums.PartTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 编码规则片段类型处理器
 * <p>根据编码组成的片段类型，从策略实现列表中选择对应处理器生成编码片段。属于系统管理（System）模块。</p>
 *
 * @author wanghuan
 */
@Component
public class PartTypeHandler {

    /** 所有编码片段策略实现（按 @Order 注入，索引与 PartTypeEnum.getBeanIndex 对应）。 */
    @Autowired
    List<PartTypeTemplate> partTypeTemplates;

    /**
     * 按组成类型路由到对应策略并生成编码片段。
     *
     * @param sysAutoCodePart 编码组成配置
     * @return 对应策略生成的编码片段字符串
     */
    public String choiceExecute(SysAutoCodePart sysAutoCodePart) {
        String partType = sysAutoCodePart.getPartType();
        return partTypeTemplates.get(PartTypeEnum.getByCode(partType).getBeanIndex()).partHandle(sysAutoCodePart);
    }
}

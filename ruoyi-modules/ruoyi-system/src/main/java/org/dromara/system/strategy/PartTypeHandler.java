package org.dromara.system.strategy;

import org.dromara.system.domain.SysAutoCodePart;
import org.dromara.system.domain.enums.PartTypeEnum;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 编码规则片段类型处理器
 *
 * @author wanghuan
 */
@Component
public class PartTypeHandler {

    @Autowired
    List<PartTypeTemplate> partTypeTemplates;

    public String choiceExecute(SysAutoCodePart sysAutoCodePart) {
        String partType = sysAutoCodePart.getPartType();
        return partTypeTemplates.get(PartTypeEnum.getByCode(partType).getBeanIndex()).partHandle(sysAutoCodePart);
    }
}

package com.nexus.system.strategy;

import com.nexus.common.enums.PartTypeEnum;
import com.nexus.system.domain.SysAutoCodePart;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PartTypeHandler {

    @Autowired
    List<PartTypeTemplate> partTypeTemplates;

    public String choiceExecute(SysAutoCodePart sysAutoCodePart){
        String partType = sysAutoCodePart.getPartType();
        return partTypeTemplates.get(PartTypeEnum.getByCode(partType).getBeanIndex()).partHandle(sysAutoCodePart);
    }

}

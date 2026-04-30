package org.dromara.system.controller.system;

import org.dromara.system.strategy.AutoCodeUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/system/autocode")
public class SysAutoCodeController {

    @Autowired
    private AutoCodeUtil autoCodeUtil;

    @GetMapping(value = {"/get/{ruleCode}/{inputCharacter}", "/get/{ruleCode}"})
    public String getAutoCode(@PathVariable String ruleCode, @PathVariable(required = false) String inputCharacter) {
        return autoCodeUtil.genSerialCode(ruleCode, inputCharacter);
    }
}

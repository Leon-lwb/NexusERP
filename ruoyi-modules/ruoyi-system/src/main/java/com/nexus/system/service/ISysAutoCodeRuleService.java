package com.nexus.system.service;

import com.nexus.system.domain.SysAutoCodeRule;
import com.nexus.system.domain.vo.SysAutoCodeRuleVo;

import java.util.List;

public interface ISysAutoCodeRuleService {

    SysAutoCodeRule getOne(String ruleCode);

    List<SysAutoCodeRuleVo> selectAutoCodeList(SysAutoCodeRule sysAutoCodeRule);

    SysAutoCodeRuleVo findById(Long ruleId);

    boolean checkRuleCodeUnique(SysAutoCodeRule sysAutoCodeRule);

    boolean checkRuleNameUnique(SysAutoCodeRule sysAutoCodeRule);

    int insertInfo(SysAutoCodeRule rule);

    int updateInfo(SysAutoCodeRule rule);

    int deleteById(Long ruleId);

    int deleteByIds(Long[] ruleIds);
}

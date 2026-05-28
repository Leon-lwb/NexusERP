package com.nexus.system.service;

import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.vo.SysAutoCodePartVo;

import java.util.List;

public interface ISysAutoCodePartService {

    List<SysAutoCodePart> listPart(SysAutoCodePart sysAutoCodePart);

    SysAutoCodePartVo findById(Long partId);

    boolean checkPartUnique(SysAutoCodePart sysAutoCodePart);

    int insertPart(SysAutoCodePart sysAutoCodePart);

    int updatePart(SysAutoCodePart sysAutoCodePart);

    int deleteByIds(Long[] partIds);
}

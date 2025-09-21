package com.nexus.system.mapper;


import com.nexus.system.domain.SysAutoCodePart;

import java.util.List;

public interface SysAutoCodePartMapper {
    public List<SysAutoCodePart> selectSysAutoCodePartList(SysAutoCodePart sysAutoCodePart);

    public SysAutoCodePart findById(Long partId);

    public SysAutoCodePart checkPartUnique(SysAutoCodePart sysAutoCodePart);

    public int add(SysAutoCodePart sysAutoCodePart);

    public int updateSysAutoCodePart(SysAutoCodePart sysAutoCodePart);

    public int deleteById(Long partId);
}

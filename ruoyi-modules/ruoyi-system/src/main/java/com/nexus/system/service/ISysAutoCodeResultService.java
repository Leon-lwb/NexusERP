package com.nexus.system.service;

import com.nexus.system.domain.SysAutoCodeResult;

import java.util.List;

public interface ISysAutoCodeResultService {

    List<SysAutoCodeResult> list(SysAutoCodeResult sysAutoCodeResult);

    void saveAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);

    void updateAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);
}

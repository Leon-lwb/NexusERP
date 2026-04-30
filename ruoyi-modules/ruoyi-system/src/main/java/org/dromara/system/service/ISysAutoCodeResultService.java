package org.dromara.system.service;

import org.dromara.system.domain.SysAutoCodeResult;

import java.util.List;

public interface ISysAutoCodeResultService {

    List<SysAutoCodeResult> list(SysAutoCodeResult sysAutoCodeResult);

    void saveAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);

    void updateAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);
}

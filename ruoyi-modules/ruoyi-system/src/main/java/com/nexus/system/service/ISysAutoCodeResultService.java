package com.nexus.system.service;

import com.nexus.system.domain.SysAutoCodeResult;

import java.util.List;

/**
 * 自动编码生成结果服务接口
 * <p>提供自动编码规则执行后产生的「编码结果（Result）」记录查询与持久化能力。
 * 属于 System 系统管理模块下的编码生成（AutoCode）子模块，用于记录每次编码生成的历史结果。</p>
 */
public interface ISysAutoCodeResultService {

    /**
     * 根据条件查询编码生成结果列表。
     *
     * @param sysAutoCodeResult 查询条件（可包含规则编码、业务标识等过滤项）
     * @return 匹配条件的编码结果列表
     */
    List<SysAutoCodeResult> list(SysAutoCodeResult sysAutoCodeResult);

    /**
     * 新增一条编码生成结果记录。
     *
     * @param sysAutoCodeResult 待保存的编码结果
     */
    void saveAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);

    /**
     * 更新已有的编码生成结果记录。
     *
     * @param sysAutoCodeResult 待更新的编码结果
     */
    void updateAutoCodeResult(SysAutoCodeResult sysAutoCodeResult);
}

package com.nexus.system.service;

import com.nexus.system.domain.SysAutoCodeRule;
import com.nexus.system.domain.vo.SysAutoCodeRuleVo;

import java.util.List;

/**
 * 自动编码规则服务接口
 * <p>提供自动编码规则（Rule）的查询、唯一性校验、增删改能力，是编码生成（AutoCode）子模块的核心配置入口。
 * 属于 System 系统管理模块，规则定义了一条业务编码如何由各组成部分拼接生成。</p>
 */
public interface ISysAutoCodeRuleService {

    /**
     * 根据规则编码获取唯一一条编码规则。
     *
     * @param ruleCode 规则编码
     * @return 对应的编码规则实体
     */
    SysAutoCodeRule getOne(String ruleCode);

    /**
     * 根据条件分页查询编码规则列表。
     *
     * @param sysAutoCodeRule 查询条件（可包含规则编码、名称等过滤项）
     * @return 编码规则视图对象列表
     */
    List<SysAutoCodeRuleVo> selectAutoCodeList(SysAutoCodeRule sysAutoCodeRule);

    /**
     * 根据规则 ID 查询编码规则详情。
     *
     * @param ruleId 规则 ID
     * @return 编码规则视图对象
     */
    SysAutoCodeRuleVo findById(Long ruleId);

    /**
     * 校验规则编码是否唯一。
     *
     * @param sysAutoCodeRule 待校验的规则（含规则编码）
     * @return 唯一返回 {@code true}，已存在重复则返回 {@code false}
     */
    boolean checkRuleCodeUnique(SysAutoCodeRule sysAutoCodeRule);

    /**
     * 校验规则名称是否唯一。
     *
     * @param sysAutoCodeRule 待校验的规则（含规则名称）
     * @return 唯一返回 {@code true}，已存在重复则返回 {@code false}
     */
    boolean checkRuleNameUnique(SysAutoCodeRule sysAutoCodeRule);

    /**
     * 新增编码规则。
     *
     * @param rule 待新增的规则
     * @return 受影响行数
     */
    int insertInfo(SysAutoCodeRule rule);

    /**
     * 更新编码规则。
     *
     * @param rule 待更新的规则
     * @return 受影响行数
     */
    int updateInfo(SysAutoCodeRule rule);

    /**
     * 根据规则 ID 删除编码规则。
     *
     * @param ruleId 规则 ID
     * @return 受影响行数
     */
    int deleteById(Long ruleId);

    /**
     * 批量删除编码规则。
     *
     * @param ruleIds 待删除的规则 ID 数组
     * @return 受影响行数
     */
    int deleteByIds(Long[] ruleIds);
}

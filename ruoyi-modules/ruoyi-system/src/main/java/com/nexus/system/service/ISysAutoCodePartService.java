package com.nexus.system.service;

import com.nexus.system.domain.SysAutoCodePart;
import com.nexus.system.domain.vo.SysAutoCodePartVo;

import java.util.List;

/**
 * 自动编码组成部分服务接口
 * <p>提供自动编码规则中各「组成部分（Part）」的增删改查与唯一性校验能力。
 * 属于 System 系统管理模块下的编码生成（AutoCode）子模块，每部分定义编码的一段生成规则。</p>
 */
public interface ISysAutoCodePartService {

    /**
     * 根据条件查询自动编码组成部分列表。
     *
     * @param sysAutoCodePart 查询条件（可包含规则关联、部分类型等过滤项）
     * @return 匹配条件的组成部分列表
     */
    List<SysAutoCodePart> listPart(SysAutoCodePart sysAutoCodePart);

    /**
     * 根据组成部分 ID 查询详情。
     *
     * @param partId 组成部分 ID
     * @return 组成部分详情视图对象
     */
    SysAutoCodePartVo findById(Long partId);

    /**
     * 校验组成部分是否唯一（新增/修改时防止重复配置）。
     *
     * @param sysAutoCodePart 待校验的组成部分（含规则关联与唯一标识字段）
     * @return 唯一返回 {@code true}，已存在重复则返回 {@code false}
     */
    boolean checkPartUnique(SysAutoCodePart sysAutoCodePart);

    /**
     * 新增一个自动编码组成部分。
     *
     * @param sysAutoCodePart 待新增的组成部分
     * @return 受影响行数
     */
    int insertPart(SysAutoCodePart sysAutoCodePart);

    /**
     * 更新自动编码组成部分。
     *
     * @param sysAutoCodePart 待更新的组成部分
     * @return 受影响行数
     */
    int updatePart(SysAutoCodePart sysAutoCodePart);

    /**
     * 批量删除自动编码组成部分。
     *
     * @param partIds 待删除的组成部分 ID 数组
     * @return 受影响行数
     */
    int deleteByIds(Long[] partIds);
}

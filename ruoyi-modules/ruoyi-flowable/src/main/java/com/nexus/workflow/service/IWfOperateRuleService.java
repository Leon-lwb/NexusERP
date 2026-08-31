package com.nexus.workflow.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.workflow.domain.vo.WfOperateRuleVo;
import com.nexus.workflow.domain.bo.WfOperateRuleBo;

import java.util.Collection;
import java.util.List;

/**
 * 流程操作规则Service接口
 *
 * @author wanghuan
 * @date 2023-11-23
 */
public interface IWfOperateRuleService {

    /**
     * 查询流程操作规则
     */
    WfOperateRuleVo queryById(Long id);

    /**
     * 查询流程操作规则列表
     */
    TableDataInfo<WfOperateRuleVo> queryPageList(WfOperateRuleBo bo, PageQuery pageQuery);

    /**
     * 查询流程操作规则列表
     */
    List<WfOperateRuleVo> queryList(WfOperateRuleBo bo);

    /**
     * 新增流程操作规则
     */
    Boolean insertByBo(WfOperateRuleBo bo);

    /**
     * 修改流程操作规则
     */
    Boolean updateByBo(WfOperateRuleBo bo);

    /**
     * 校验并批量删除流程操作规则信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}

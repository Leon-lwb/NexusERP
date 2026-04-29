package org.dromara.workflow.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.workflow.domain.vo.WfOperateRuleVo;
import org.dromara.workflow.domain.bo.WfOperateRuleBo;

import java.util.Collection;
import java.util.List;

/**
 * 流程操作规则Service接口
 *
 * @author nbacheng
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

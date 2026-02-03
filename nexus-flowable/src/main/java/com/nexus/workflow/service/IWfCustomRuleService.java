package com.nexus.workflow.service;

import com.nexus.common.core.page.PageQuery;
import com.nexus.common.core.page.TableDataInfo;
import com.nexus.common.core.page.WFTableDataInfo;
import com.nexus.workflow.domain.bo.WfCustomRuleBo;
import com.nexus.workflow.domain.vo.WfCustomRuleVo;

import java.util.Collection;
import java.util.List;

/**
 * 流程自定义业务规则Service接口
 *
 * @author nbacheng
 * @date 2023-11-23
 */
public interface IWfCustomRuleService {

    /**
     * 查询流程自定义业务规则
     */
    WfCustomRuleVo queryById(Long id);

    /**
     * 查询流程自定义业务规则列表
     */
    WFTableDataInfo<WfCustomRuleVo> queryPageList(WfCustomRuleBo bo, PageQuery pageQuery);

    /**
     * 查询流程自定义业务规则列表
     */
    List<WfCustomRuleVo> queryList(WfCustomRuleBo bo);

    /**
     * 新增流程自定义业务规则
     */
    Boolean insertByBo(WfCustomRuleBo bo);

    /**
     * 修改流程自定义业务规则
     */
    Boolean updateByBo(WfCustomRuleBo bo);

    /**
     * 校验并批量删除流程自定义业务规则信息
     */
    Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid);
}

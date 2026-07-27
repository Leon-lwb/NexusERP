package com.nexus.mes.pro.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pro.domain.bo.ProStepCheckRuleBo;
import com.nexus.mes.pro.domain.vo.ProStepCheckRuleVo;

import java.util.Collection;
import java.util.List;

/**
 * MES工序校验规则配置 Service 接口(P0-3)
 *
 * @author Nexus
 */
public interface IProStepCheckRuleService {

    /**
     * 分页查询工序校验规则列表
     */
    TableDataInfo<ProStepCheckRuleVo> selectPageList(ProStepCheckRuleBo bo, PageQuery pageQuery);

    /**
     * 根据ID获取工序校验规则详情
     */
    ProStepCheckRuleVo selectById(Long ruleId);

    /**
     * 不分页查询工序校验规则列表(导出用)
     */
    List<ProStepCheckRuleVo> selectList(ProStepCheckRuleBo bo);

    /**
     * 新增工序校验规则
     */
    Boolean insertByBo(ProStepCheckRuleBo bo);

    /**
     * 编辑工序校验规则
     */
    Boolean updateByBo(ProStepCheckRuleBo bo);

    /**
     * 批量删除工序校验规则
     */
    Boolean deleteByIds(Collection<Long> ids);
}

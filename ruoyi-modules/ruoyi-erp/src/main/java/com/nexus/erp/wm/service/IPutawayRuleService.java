package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmPutawayRuleBo;
import com.nexus.erp.wm.domain.vo.WmPutawayRuleVo;

import java.util.List;

public interface IPutawayRuleService {

    TableDataInfo<WmPutawayRuleVo> selectPageList(WmPutawayRuleBo bo, PageQuery pageQuery);

    List<WmPutawayRuleVo> selectList(WmPutawayRuleBo bo);

    WmPutawayRuleVo selectById(Long id);

    int insertByBo(WmPutawayRuleBo bo);

    int updateByBo(WmPutawayRuleBo bo);

    int deleteByIds(Long[] ids);
}

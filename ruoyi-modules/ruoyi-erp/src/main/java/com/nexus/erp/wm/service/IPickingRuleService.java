package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.WmPickingRuleBo;
import com.nexus.erp.wm.domain.vo.WmPickingRuleVo;

import java.util.List;

public interface IPickingRuleService {

    TableDataInfo<WmPickingRuleVo> selectPageList(WmPickingRuleBo bo, PageQuery pageQuery);

    List<WmPickingRuleVo> selectList(WmPickingRuleBo bo);

    WmPickingRuleVo selectById(Long id);

    int insertByBo(WmPickingRuleBo bo);

    int updateByBo(WmPickingRuleBo bo);

    int deleteByIds(Long[] ids);
}

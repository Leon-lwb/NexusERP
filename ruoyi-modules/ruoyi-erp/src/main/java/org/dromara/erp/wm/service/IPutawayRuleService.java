package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmPutawayRuleBo;
import org.dromara.erp.wm.domain.vo.WmPutawayRuleVo;

import java.util.List;

public interface IPutawayRuleService {

    TableDataInfo<WmPutawayRuleVo> selectPageList(WmPutawayRuleBo bo, PageQuery pageQuery);

    List<WmPutawayRuleVo> selectList(WmPutawayRuleBo bo);

    WmPutawayRuleVo selectById(Long id);

    int insertByBo(WmPutawayRuleBo bo);

    int updateByBo(WmPutawayRuleBo bo);

    int deleteByIds(Long[] ids);
}

package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmPickingRuleBo;
import org.dromara.erp.wm.domain.vo.WmPickingRuleVo;

import java.util.List;

public interface IPickingRuleService {

    TableDataInfo<WmPickingRuleVo> selectPageList(WmPickingRuleBo bo, PageQuery pageQuery);

    List<WmPickingRuleVo> selectList(WmPickingRuleBo bo);

    WmPickingRuleVo selectById(Long id);

    int insertByBo(WmPickingRuleBo bo);

    int updateByBo(WmPickingRuleBo bo);

    int deleteByIds(Long[] ids);
}

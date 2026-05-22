package org.dromara.erp.wm.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.wm.domain.bo.WmStockTakingPlanBo;
import org.dromara.erp.wm.domain.vo.WmStockTakingPlanVo;

import java.util.List;

public interface IWmStockTakingPlanService {

    TableDataInfo<WmStockTakingPlanVo> selectPageList(WmStockTakingPlanBo bo, PageQuery pageQuery);

    List<WmStockTakingPlanVo> selectList(WmStockTakingPlanBo bo);

    WmStockTakingPlanVo selectById(Long id);

    int insertByBo(WmStockTakingPlanBo bo);

    int updateByBo(WmStockTakingPlanBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 生成盘点行（从库存快照生成盘点明细）
     */
    void generateLines(Long planId);

    /**
     * 执行盘点过账（将差异更新到库存）
     */
    void postAdjustment(Long planId);
}

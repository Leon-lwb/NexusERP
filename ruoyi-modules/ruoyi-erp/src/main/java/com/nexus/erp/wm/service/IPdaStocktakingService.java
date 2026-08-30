package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.pda.PdaCountBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaStocktakingDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;

/**
 * PDA 盘点作业服务
 */
public interface IPdaStocktakingService {

    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    PdaStocktakingDetailVo detail(Long planId);

    /** 生成盘点明细行 (库存快照) */
    PdaActionResultVo generate(Long planId);

    /** 行级实盘录入 (覆盖语义, 可重复扫码改数) */
    PdaActionResultVo count(Long planId, PdaCountBo bo);

    /** 差异过账 */
    PdaActionResultVo post(Long planId);
}

package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.pda.PdaHandoverConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaHandoverDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;

/**
 * PDA 上料交接作业接口
 */
public interface IPdaHandoverService {

    /** 待交接/待签收列表 (DRAFT) */
    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    /** 交接单详情 (头+行) */
    PdaHandoverDetailVo detail(Long handoverId);

    /** 行级核销: 累计实交, 不超过计划数量 */
    PdaActionResultVo confirmLine(Long handoverId, PdaHandoverConfirmBo bo);

    /** 签收: 同事务逐行过账(源库位出 + 线边库位入), 状态 DRAFT -> SIGNED */
    PdaActionResultVo sign(Long handoverId);
}

package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.pda.PdaReturnConfirmBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaReturnDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;

/**
 * PDA 退料作业服务 (PC 建单, PDA 扫码核销; 良品/不良品回库存, 报废留痕)
 */
public interface IPdaReturnService {

    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    PdaReturnDetailVo detail(Long returnId);

    /** 行级扫码核销 (累计语义, 不超过计划数量) */
    PdaActionResultVo confirmLine(Long returnId, PdaReturnConfirmBo bo);

    /** 退料完成: 良品/不良品行按实退数量回库存, 报废行留痕 */
    PdaActionResultVo finish(Long returnId);
}

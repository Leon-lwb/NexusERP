package com.nexus.erp.wm.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.wm.domain.bo.pda.PdaPickBo;
import com.nexus.erp.wm.domain.vo.pda.PdaActionResultVo;
import com.nexus.erp.wm.domain.vo.pda.PdaIssueDetailVo;
import com.nexus.erp.wm.domain.vo.pda.PdaTodoItemVo;

/**
 * PDA 备料发料作业服务 (摘果式拣料, 复用既有发料单多次部分领料能力)
 */
public interface IPdaIssueService {

    TableDataInfo<PdaTodoItemVo> todo(String keyword, PageQuery pageQuery);

    PdaIssueDetailVo detail(Long issueId);

    /** 行级拣货核销: 校验库位/数量后置该行 currentQty, 调用既有 execute 过账(支持多次) */
    PdaActionResultVo pick(Long issueId, PdaPickBo bo);

    /** 完成发料: 全部行领满后执行; 未领满时拒绝(缺货通道为 M2) */
    PdaActionResultVo finish(Long issueId);
}

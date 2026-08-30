package com.nexus.mes.pda.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.mes.pda.domain.bo.PdaReportCompleteBo;
import com.nexus.mes.pda.domain.vo.PdaActionResultVo;
import com.nexus.mes.pda.domain.vo.PdaDispatchItemVo;

/**
 * PDA 扫码报工作业服务 (编排派工单: PENDING→IN_PROGRESS→COMPLETED)
 */
public interface IPdaReportService {

    /** 待报工派工列表 (未完成的, 按工单号/派工单号模糊) */
    TableDataInfo<PdaDispatchItemVo> todo(String keyword, PageQuery pageQuery);

    /** 派工详情 (刷新状态用) */
    PdaDispatchItemVo detail(Long dispatchId);

    /** 开工: PENDING→IN_PROGRESS (已开工幂等返回成功) */
    PdaActionResultVo start(Long dispatchId);

    /** 完工报工: 数量校验+回写工序/工单 (已完成幂等返回成功) */
    PdaActionResultVo complete(Long dispatchId, PdaReportCompleteBo bo);
}

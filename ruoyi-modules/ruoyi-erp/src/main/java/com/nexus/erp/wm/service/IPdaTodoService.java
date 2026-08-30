package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.vo.pda.PdaTodoSummaryVo;

/**
 * PDA 任务中心待办聚合服务
 */
public interface IPdaTodoService {

    /**
     * 待办聚合: 各流程可作业单据计数 (按权限点过滤可见流程)
     */
    PdaTodoSummaryVo summary();
}

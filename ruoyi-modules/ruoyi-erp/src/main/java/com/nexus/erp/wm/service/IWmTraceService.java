package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.vo.WmTraceResultVo;

/**
 * 追溯查询Service接口
 *
 * @author nexus
 * @date 2026-06-02
 */
public interface IWmTraceService {

    /**
     * 正向追溯（原材料 → 成品）
     * <p>
     * 从指定的物料/批次/序列号出发，追踪其后续流转路径，
     * 即该物料被用在了哪些成品或半成品中。
     *
     * @param materialId 物料ID
     * @param batchCode  批次编码（可选）
     * @param snCode     序列号编码（可选）
     * @return 正向追溯结果
     */
    WmTraceResultVo traceForward(Long materialId, String batchCode, String snCode);

    /**
     * 反向追溯（成品 → 原材料）
     * <p>
     * 从指定的物料/批次/序列号出发，追溯其来源，
     * 即该成品/半成品使用了哪些原材料。
     *
     * @param materialId 物料ID
     * @param batchCode  批次编码（可选）
     * @param snCode     序列号编码（可选）
     * @return 反向追溯结果
     */
    WmTraceResultVo traceBackward(Long materialId, String batchCode, String snCode);
}

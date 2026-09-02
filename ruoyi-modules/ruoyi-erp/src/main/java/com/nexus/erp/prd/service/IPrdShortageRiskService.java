package com.nexus.erp.prd.service;

import com.nexus.erp.prd.domain.vo.PrdShortageRiskVo;

import java.util.List;

/**
 * 供应链缺料风险预测 Service 接口
 * <p>MRP 毛需求(未满足部分) + 库存(扣预留) + 安全库存 + 在途采购 → 逐日推演缺料时间线。</p>
 */
public interface IPrdShortageRiskService {

    /**
     * 推演未来 N 天的缺料风险(按最早缺料日升序), 结果缓存 Redis 30 分钟
     *
     * @param days 推演天数
     * @return 风险清单(含无风险物料, 调用方按 hasRisk 过滤)
     */
    List<PrdShortageRiskVo> predict(int days);

    /**
     * 强制刷新(跳过缓存重新推演)
     */
    List<PrdShortageRiskVo> predictRefresh(int days);
}

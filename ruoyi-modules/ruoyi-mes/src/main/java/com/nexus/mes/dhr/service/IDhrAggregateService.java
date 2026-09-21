package com.nexus.mes.dhr.service;

import com.nexus.mes.pro.domain.ProWorkOrder;

import java.util.List;
import java.util.Map;

/**
 * DHR 批记录分册聚合服务（只读）。
 *
 * <p>按 10 个固定分册编码聚合 MES 既有表的证据条目；<b>无数据的分册返回空列表</b>，
 * 由调用方落一条 {@code item_count=0} 的空分册，保证分册集合完整可校验。</p>
 *
 * <p>每条条目都带 {@code linkage} 标注关联口径（直接外键 / 间接链），供审计复核 ——
 * 无工单外键的表绝不伪造工单关联。</p>
 *
 * @author Nexus
 */
public interface IDhrAggregateService {

    /**
     * 聚合分册条目。
     *
     * @param workOrder MES 工单（提供 mes_wo_id / wo_no / material_id / 时间窗）
     * @param lotNo     批次号（可空）
     * @param sn        SN 序列号（可空）
     * @return 分册编码 -> 条目列表（含全部 10 个 key）
     */
    Map<String, List<Map<String, Object>>> aggregate(ProWorkOrder workOrder, String lotNo, String sn);
}

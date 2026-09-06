package com.nexus.erp.wm.service;

import com.nexus.erp.wm.domain.bo.WmSubcontractIssueBo;
import com.nexus.erp.wm.domain.vo.WmSubcontractIssueVo;

import java.util.List;
import java.util.Map;

/**
 * 委外发料单 Service 接口 (PCBA 闭环)
 *
 * @author nexus
 */
public interface IWmSubcontractIssueService {

    /**
     * 按委外订单查询发料单列表
     *
     * @param subcontractId 委外订单ID
     * @return 发料单列表
     */
    List<WmSubcontractIssueVo> listBySubcontract(Long subcontractId);

    /**
     * 查询发料单详情(含行)
     *
     * @param issueId 发料单ID
     * @return 发料单详情
     */
    Map<String, Object> selectById(Long issueId);

    /**
     * 备料建议: 按 BOM 快照"自备料"行 × 实时可用库存, 自动生成 FEFO/FIFO 批次分配建议
     *
     * @param subcontractId 委外订单ID
     * @return [{bomLineId, materialId, materialCode, materialName, demandQty, issuedQty, remainingQty,
     *         allocations:[{stockId,batchId,batchCode,locationId,deductQty}], shortage}]
     */
    List<Map<String, Object>> suggestAllocations(Long subcontractId);

    /**
     * 创建委外发料单(含行与可选分配明细)
     *
     * @param bo 发料单业务对象
     * @return 发料单ID
     */
    Long createIssue(WmSubcontractIssueBo bo);

    /**
     * 删除发料单(仅 DRAFT)
     *
     * @param issueId 发料单ID
     * @return 删除行数
     */
    int deleteIssue(Long issueId);

    /**
     * 发料过账: 订单维度加锁, 按分配明细(或自动FEFO/FIFO)扣减库存并写流水,
     * 累加快照行已发量, 全部自供行发齐后推进订单(RELEASED+工单+SN MQ)
     *
     * @param issueId 发料单ID
     */
    void postIssue(Long issueId);
}

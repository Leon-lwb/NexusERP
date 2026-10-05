package com.nexus.erp.wm.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.wm.domain.WmBatch;
import com.nexus.erp.wm.domain.vo.WmBatchVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

public interface WmBatchMapper extends BaseMapperPlus<WmBatch, WmBatchVo> {

    /**
     * 原子累计实际库存批次的已发数量，与领料库存扣减、成本和流水共用调用方事务。
     * <p>相对累加避免并发覆盖；身份与收量上限在同一语句中校验，不改变批次状态。</p>
     *
     * @param batchId 实际库存批次ID
     * @param materialId 实际库存物料ID
     * @param tenantId 实际库存租户ID
     * @param issueQty 本次实际发料数量，必须大于零
     * @return 影响行数；0 表示身份不匹配、批次已删除或发料将超过已收数量
     */
    @Update("UPDATE wm_batch SET issued_qty = IFNULL(issued_qty, 0) + #{issueQty}, update_time = NOW() " +
        "WHERE batch_id = #{batchId} AND material_id = #{materialId} AND tenant_id = #{tenantId} " +
        "AND del_flag = '0' AND #{issueQty} > 0 " +
        "AND IFNULL(issued_qty, 0) + #{issueQty} <= IFNULL(received_qty, 0)")
    int increaseIssuedQty(@Param("batchId") Long batchId,
                          @Param("materialId") Long materialId,
                          @Param("tenantId") String tenantId,
                          @Param("issueQty") BigDecimal issueQty);

    /**
     * 原子累计原生产工单批次的实际已收数量，与完工库存和流水共用调用方事务。
     * <p>只相对增加已收量，不改变批次数量、已发量、状态或来源。</p>
     *
     * @param batchId 本次入库指定的生产批次ID
     * @param materialId 实际库存物料ID
     * @param tenantId 实际库存租户ID
     * @param woId 原生产工单ID
     * @param receiveQty 本次实际入库数量，必须大于零
     * @return 影响行数；0 表示身份不匹配、批次已删除或入库数量非正
     */
    @Update("UPDATE wm_batch SET received_qty = IFNULL(received_qty, 0) + #{receiveQty}, update_time = NOW() " +
        "WHERE batch_id = #{batchId} AND material_id = #{materialId} AND tenant_id = #{tenantId} " +
        "AND source_doc_type = 'PRD_WORK_ORDER' AND source_doc_id = #{woId} " +
        "AND del_flag = '0' AND #{receiveQty} > 0")
    int increaseProductionReceivedQty(@Param("batchId") Long batchId,
                                     @Param("materialId") Long materialId,
                                     @Param("tenantId") String tenantId,
                                     @Param("woId") Long woId,
                                     @Param("receiveQty") BigDecimal receiveQty);
}

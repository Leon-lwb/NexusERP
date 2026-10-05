package com.nexus.erp.prd.mapper;

import com.nexus.erp.prd.domain.PrdWorkOrder;
import com.nexus.erp.wm.domain.WmMaterialStock;
import com.nexus.erp.wm.domain.WmMaterialStockTx;
import com.nexus.erp.wm.domain.WmProductionRecpt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.math.BigDecimal;
import java.util.List;

/** 完工过账专用只读映射器，所有查询显式限定租户；不依赖消息缓存判断成功。 */
@Mapper
public interface ProductionCompletionPostingMapper {

    /** 锁住真实工单，同租户同工单的不同完工消息在 ERP 事务内串行。 */
    @Select("""
        SELECT * FROM prd_work_order
        WHERE tenant_id = #{tenantId} AND wo_id = #{woId} AND del_flag = '0'
        FOR UPDATE
        """)
    PrdWorkOrder lockWorkOrder(@Param("tenantId") String tenantId, @Param("woId") Long woId);

    /** 已审核报工曾累加工单数量，逻辑删除并未冲减，故这里不能过滤删除标志。 */
    @Select("""
        SELECT COALESCE(SUM(reported_qty), 0) FROM prd_work_report
        WHERE tenant_id = #{tenantId} AND wo_id = #{woId} AND report_status = 'APPROVED'
        """)
    BigDecimal selectApprovedReportedQty(@Param("tenantId") String tenantId, @Param("woId") Long woId);

    /** 包含已删除或未执行单据，避免把半过账当作无凭据重新扣料。 */
    @Select("""
        SELECT * FROM wm_production_recpt
        WHERE tenant_id = #{tenantId} AND wo_id = #{woId}
        FOR UPDATE
        """)
    List<WmProductionRecpt> selectReceipts(@Param("tenantId") String tenantId, @Param("woId") Long woId);

    /** 包含删除、反向及异常流水；调用方必须对完整集合验账，不能仅查正向成功行。 */
    @Select("""
        SELECT * FROM wm_material_stock_tx
        WHERE tenant_id = #{tenantId} AND source_doc_type = 'PRD_WORK_ORDER' AND source_doc_id = #{woId}
        FOR UPDATE
        """)
    List<WmMaterialStockTx> selectCompletionTransactions(@Param("tenantId") String tenantId, @Param("woId") Long woId);

    /** 校验流水关联的有效库存维度，不能把不存在的库存行当成已入库。 */
    @Select("""
        SELECT * FROM wm_material_stock
        WHERE tenant_id = #{tenantId} AND stock_id = #{stockId} AND del_flag = '0'
        """)
    WmMaterialStock selectStock(@Param("tenantId") String tenantId, @Param("stockId") Long stockId);

    /** 旧链路倒冲单独提交后不得自动再次扣料；倒冲单完成后仍保留此固定备注前缀。 */
    @Select("""
        SELECT COUNT(*) FROM wm_production_issue
        WHERE tenant_id = #{tenantId} AND wo_id = #{woId}
          AND (status = 'BACKFLUSH' OR remark LIKE '完工倒冲领料，完工数量:%')
        """)
    long countExistingBackflushIssues(@Param("tenantId") String tenantId, @Param("woId") Long woId);
}

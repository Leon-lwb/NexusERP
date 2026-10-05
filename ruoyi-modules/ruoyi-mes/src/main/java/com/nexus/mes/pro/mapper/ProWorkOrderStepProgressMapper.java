package com.nexus.mes.pro.mapper;

import com.nexus.mes.pro.domain.ProFlowCard;
import com.nexus.mes.pro.domain.ProFlowCardStep;
import com.nexus.mes.pro.domain.ProWorkOrder;
import com.nexus.mes.pro.domain.ProWorkOrderStep;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/** 同库工序投影的显式租户当前读；先锁工单，执行写者才可继续取得卡或步骤锁。 */
@Mapper
public interface ProWorkOrderStepProgressMapper {
    /** 仅取得父引用，不将这个非锁定读取作为执行进度快照。 */
    @Select("SELECT mes_wo_id FROM pro_flow_card WHERE tenant_id=#{tenant} AND card_id=#{cardId} AND del_flag='0'")
    Long findCardParent(@Param("tenant") String tenant, @Param("cardId") Long cardId);

    /** SN 编码必须唯一，不能用 LIMIT 掩盖重复来源。 */
    @Select("SELECT mes_wo_id FROM pro_sn WHERE tenant_id=#{tenant} AND sn_code=#{sn} AND del_flag='0' ORDER BY sn_id")
    List<Long> findSnParents(@Param("tenant") String tenant, @Param("sn") String sn);

    /** 工单是所有有效卡执行写者共享的首把数据库行锁。 */
    @Select("SELECT * FROM pro_work_order WHERE tenant_id=#{tenant} AND mes_wo_id=#{woId} AND del_flag='0' FOR UPDATE")
    ProWorkOrder lockWorkOrder(@Param("tenant") String tenant, @Param("woId") Long woId);

    /** 父锁之后重读卡，防止引用查询与加锁之间被改绑。 */
    @Select("SELECT * FROM pro_flow_card WHERE tenant_id=#{tenant} AND card_id=#{cardId} AND del_flag='0' FOR UPDATE")
    ProFlowCard lockCard(@Param("tenant") String tenant, @Param("cardId") Long cardId);

    @Select("SELECT * FROM pro_work_order_step WHERE tenant_id=#{tenant} AND mes_wo_id=#{woId} AND del_flag='0' ORDER BY sequence_num,step_id FOR UPDATE")
    List<ProWorkOrderStep> lockWorkOrderSteps(@Param("tenant") String tenant, @Param("woId") Long woId);

    /** 已关闭卡是已完工归档；不能从统计中删除其真实执行量。 */
    @Select("SELECT * FROM pro_flow_card WHERE tenant_id=#{tenant} AND mes_wo_id=#{woId} AND del_flag='0' ORDER BY card_id FOR UPDATE")
    List<ProFlowCard> lockCards(@Param("tenant") String tenant, @Param("woId") Long woId);

    @Select("SELECT s.* FROM pro_flow_card_step s JOIN pro_flow_card c ON c.card_id=s.card_id AND c.tenant_id=s.tenant_id "
        + "WHERE c.tenant_id=#{tenant} AND c.mes_wo_id=#{woId} AND c.del_flag='0' AND s.del_flag='0' ORDER BY s.card_id,s.sequence_num,s.step_record_id FOR UPDATE")
    List<ProFlowCardStep> lockCardSteps(@Param("tenant") String tenant, @Param("woId") Long woId);

    /** 只写执行投影，不改计划/工艺/刷写绑定，也不增加工单头或库存。 */
    @Update("UPDATE pro_work_order_step SET completed_qty=#{good},scrap_qty=#{scrap},step_status=#{status},start_time=#{start},end_time=#{end},update_time=NOW() "
        + "WHERE tenant_id=#{tenant} AND mes_wo_id=#{woId} AND step_id=#{stepId} AND del_flag='0'")
    int updateProgress(@Param("tenant") String tenant, @Param("woId") Long woId,
        @Param("stepId") Long stepId, @Param("good") BigDecimal good, @Param("scrap") BigDecimal scrap,
        @Param("status") String status, @Param("start") Date start, @Param("end") Date end);
}

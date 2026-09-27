package com.nexus.erp.prd.mapper;

import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;
import com.nexus.erp.prd.domain.PrdSchedulePlan;
import com.nexus.erp.prd.domain.vo.PrdSchedulePlanVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * APS排程方案Mapper接口
 *
 * @author nexus
 */
public interface PrdSchedulePlanMapper extends BaseMapperPlus<PrdSchedulePlan, PrdSchedulePlanVo> {

    /** 唯一键冲突后识别本租户已补偿的永久占用键；绕过 @TableLogic 的常规过滤。 */
    @Select("SELECT plan_id FROM prd_schedule_plan WHERE tenant_id = #{tenantId} "
        + "AND decision_idempotency_key = #{key} AND del_flag = '1' FOR UPDATE")
    Long findCompensatedDecisionId(@Param("tenantId") String tenantId, @Param("key") String key);
}

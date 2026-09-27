package com.nexus.common.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.ai.domain.entity.AiDecisionAction;
import com.nexus.common.ai.domain.vo.AiDecisionActionVo;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

/** 动作持久化；列表使用轻量投影，避免读取 payloadJson/resultJson。 */
public interface AiDecisionActionMapper extends BaseMapperPlus<AiDecisionAction, AiDecisionActionVo> {

    static QueryWrapper<AiDecisionAction> summarySelection(QueryWrapper<AiDecisionAction> wrapper) {
        return wrapper.select("id", "run_id", "action_type", "title", "display_reason", "target_biz_type",
            "target_biz_id", "target_biz_code", "risk_level", "approval_required", "approval_status",
            "execution_status", "permission_code", "executor_code", "sequence_no", "idempotency_key",
            "approved_by", "approved_at", "approval_comment", "result_biz_type", "result_biz_id",
            "result_biz_code", "retry_count", "last_error_code", "last_error_message", "started_at",
            "finished_at", "version", "create_dept", "create_by", "create_time", "update_by", "update_time");
    }

    default <P extends IPage<AiDecisionActionVo>> P selectSummaryPage(IPage<AiDecisionAction> page,
                                                                         QueryWrapper<AiDecisionAction> wrapper) {
        return selectVoPage(page, summarySelection(wrapper));
    }
}

package com.nexus.common.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.ai.domain.vo.AiDecisionRunVo;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

/**
 * 决策运行持久化。BaseMapperPlus.selectVoPage 默认会查询整个实体，
 * 列表应调用 selectSummaryPage；详情可调用 selectVoById 获取 resultJson/outcomeJson。
 */
public interface AiDecisionRunMapper extends BaseMapperPlus<AiDecisionRun, AiDecisionRunVo> {

    static QueryWrapper<AiDecisionRun> summarySelection(QueryWrapper<AiDecisionRun> wrapper) {
        return wrapper.select("id", "tenant_id", "scene_code", "biz_type", "biz_id", "biz_code",
            "parent_run_id", "superseded_by_run_id", "trigger_type", "status", "risk_score", "risk_level",
            "confidence_level", "recommended_scenario_code", "input_snapshot_hash", "snapshot_time",
            "valid_until", "summary_mode", "model_id", "model_name", "prompt_version", "input_tokens",
            "output_tokens", "estimated_cost", "analysis_flow_instance_id", "execution_flow_instance_id",
            "error_code", "error_message", "started_at", "analysis_finished_at", "finished_at", "version",
            "create_dept", "create_by", "create_time", "update_by", "update_time");
    }

    default <P extends IPage<AiDecisionRunVo>> P selectSummaryPage(IPage<AiDecisionRun> page,
                                                                      QueryWrapper<AiDecisionRun> wrapper) {
        return selectVoPage(page, summarySelection(wrapper));
    }
}

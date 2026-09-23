package com.nexus.common.ai.mapper;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.nexus.common.ai.domain.entity.AiDecisionEvidence;
import com.nexus.common.ai.domain.vo.AiDecisionEvidenceVo;
import com.nexus.common.mybatis.core.mapper.BaseMapperPlus;

/** 证据持久化；列表使用轻量投影，不读取 derivedFromJson。 */
public interface AiDecisionEvidenceMapper extends BaseMapperPlus<AiDecisionEvidence, AiDecisionEvidenceVo> {

    static QueryWrapper<AiDecisionEvidence> summarySelection(QueryWrapper<AiDecisionEvidence> wrapper) {
        return wrapper.select("id", "run_id", "evidence_key", "source_domain", "source_type", "source_id",
            "source_code", "source_name", "metric_code", "metric_name", "value_type", "metric_value", "unit",
            "observed_at", "data_version", "confidence", "verifiable", "normative", "display_path",
            "create_dept", "create_by", "create_time", "update_by", "update_time");
    }

    default <P extends IPage<AiDecisionEvidenceVo>> P selectSummaryPage(IPage<AiDecisionEvidence> page,
                                                                           QueryWrapper<AiDecisionEvidence> wrapper) {
        return selectVoPage(page, summarySelection(wrapper));
    }
}

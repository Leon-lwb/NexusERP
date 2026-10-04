package com.nexus.ai.service.decision;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nexus.common.ai.domain.entity.AiDecisionRun;
import com.nexus.common.core.exception.ServiceException;

/** 只读运行冻结窗口；缺失、类型错误或越界时不能从方案依据补全。 */
public final class DecisionFrozenHorizon {
    // 此读取器仅解析配置，不序列化业务对象或雪花 ID。
    private static final ObjectMapper CONFIG_READER = new ObjectMapper();

    private DecisionFrozenHorizon() { }

    public static int requireHorizonDays(AiDecisionRun run) {
        if (run != null && run.getScoreConfigJson() != null) {
            try {
                var config = CONFIG_READER.readTree(run.getScoreConfigJson());
                var days = config == null ? null : config.path("horizonDays");
                if (days != null && days.isIntegralNumber() && days.canConvertToInt()
                    && days.intValue() >= 1 && days.intValue() <= 90) return days.intValue();
            } catch (JsonProcessingException ignored) {
                // 解析异常可能携带配置原文，不向调用方传播。
            }
        }
        throw new ServiceException("决策运行缺少可信的服务端冻结窗口");
    }
}

import type { DecisionConfig, DecisionRankingConfig, DecisionRiskConfig } from '@/api/ai/config';

/** 与后端 AiProperties.DecisionConfig 的初始值保持一致。 */
export function createDefaultDecisionConfig(): DecisionConfig {
  return {
    enabled: true,
    maxConcurrentRuns: 4,
    maxScenarios: 4,
    analysisTimeoutSeconds: 60,
    actionTimeoutSeconds: 30,
    staleThresholdSeconds: 300,
    requireApprovalLevels: ['HIGH', 'CRITICAL'],
    allowAutoLowRiskNotify: true,
    benchmarkMode: false,
    defaultHorizonDays: 30,
    maxHorizonDays: 90,
    policyKnowledgeId: null,
    risk: { tardinessPoints: 40, shortagePoints: 30, capacityPoints: 20, supplierPoints: 10 },
    ranking: {
      tardinessWeight: 0.3,
      delayedCountWeight: 0.2,
      shortageWeight: 0.15,
      utilizationWeight: 0.1,
      actionCostWeight: 0.1,
      planChangeWeight: 0.1,
      infeasibleWeight: 0.05
    }
  };
}

/** 兼容旧节点尚未返回 decision 分组的配置响应。 */
export function normalizeDecisionConfig(value?: Partial<DecisionConfig> | null): DecisionConfig {
  const defaults = createDefaultDecisionConfig();
  return {
    ...defaults,
    ...value,
    requireApprovalLevels: value?.requireApprovalLevels ? [...value.requireApprovalLevels] : [...defaults.requireApprovalLevels],
    policyKnowledgeId: value?.policyKnowledgeId == null ? null : String(value.policyKnowledgeId),
    risk: { ...defaults.risk, ...value?.risk },
    ranking: { ...defaults.ranking, ...value?.ranking }
  };
}

export function decisionRiskPointSum(risk: DecisionRiskConfig): number {
  return risk.tardinessPoints + risk.shortagePoints + risk.capacityPoints + risk.supplierPoints;
}

export function decisionRankingWeightSum(ranking: DecisionRankingConfig): number {
  return (
    Math.round(
      (ranking.tardinessWeight +
        ranking.delayedCountWeight +
        ranking.shortageWeight +
        ranking.utilizationWeight +
        ranking.actionCostWeight +
        ranking.planChangeWeight +
        ranking.infeasibleWeight) *
        1_000_000
    ) / 1_000_000
  );
}

/** 前端即时反馈；服务端必须独立重复验证，不能依赖此函数授权执行。 */
export function validateDecisionConfig(config: DecisionConfig): string | null {
  const points = Object.values(config.risk);
  if (points.some((point) => !Number.isInteger(point) || point < 0 || point > 100) || decisionRiskPointSum(config.risk) !== 100) {
    return '决策风险分值必须是非负整数且合计为 100';
  }
  const weights = Object.values(config.ranking);
  if (
    weights.some((weight) => !Number.isFinite(weight) || weight < 0 || weight > 1) ||
    Math.abs(decisionRankingWeightSum(config.ranking) - 1) > 0.000001
  ) {
    return '决策方案评分权重必须在 0～1 且合计为 1';
  }
  if (
    !Number.isInteger(config.defaultHorizonDays) ||
    !Number.isInteger(config.maxHorizonDays) ||
    config.defaultHorizonDays < 1 ||
    config.maxHorizonDays > 90 ||
    config.defaultHorizonDays > config.maxHorizonDays
  ) {
    return '推演跨度必须在 1～90 天且默认值不大于最大值';
  }
  if (
    !Number.isInteger(config.maxConcurrentRuns) ||
    config.maxConcurrentRuns < 1 ||
    config.maxConcurrentRuns > 16 ||
    !Number.isInteger(config.maxScenarios) ||
    config.maxScenarios < 2 ||
    config.maxScenarios > 8
  ) {
    return '并发上限或方案数量不在允许范围';
  }
  if (!Number.isInteger(config.analysisTimeoutSeconds) || config.analysisTimeoutSeconds < 10 || config.analysisTimeoutSeconds > 300) {
    return '分析超时必须在 10～300 秒';
  }
  if (!Number.isInteger(config.actionTimeoutSeconds) || config.actionTimeoutSeconds < 5 || config.actionTimeoutSeconds > 120) {
    return '动作超时必须在 5～120 秒';
  }
  if (!Number.isInteger(config.staleThresholdSeconds) || config.staleThresholdSeconds < 30 || config.staleThresholdSeconds > 3600) {
    return '证据过期阈值必须在 30～3600 秒';
  }
  if (!config.requireApprovalLevels.includes('HIGH') || !config.requireApprovalLevels.includes('CRITICAL')) {
    return '高风险及严重风险必须保留人工审批';
  }
  if (config.policyKnowledgeId) {
    if (!/^[1-9]\d*$/.test(config.policyKnowledgeId)) {
      return '企业制度知识库 ID 必须是正整数';
    }
    if (BigInt(config.policyKnowledgeId) > 9223372036854775807n) {
      return '企业制度知识库 ID 超出 Java Long 上限';
    }
  }
  return null;
}

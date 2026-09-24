import assert from 'node:assert/strict';
import {
  createDefaultDecisionConfig,
  decisionRiskPointSum,
  decisionRankingWeightSum,
  normalizeDecisionConfig,
  validateDecisionConfig
} from '../node_modules/.tmp/decision-config.mjs';

const defaults = createDefaultDecisionConfig();
assert.equal(decisionRiskPointSum(defaults.risk), 100);
assert.equal(decisionRankingWeightSum(defaults.ranking), 1);
assert.equal(validateDecisionConfig(defaults), null);

const badRisk = createDefaultDecisionConfig();
badRisk.risk.tardinessPoints = 45;
assert.match(validateDecisionConfig(badRisk), /100/);

const badWeight = createDefaultDecisionConfig();
badWeight.ranking.actionCostWeight = 0.4;
assert.match(validateDecisionConfig(badWeight), /1/);

const badRange = createDefaultDecisionConfig();
badRange.defaultHorizonDays = 91;
assert.match(validateDecisionConfig(badRange), /跨度/);

const badId = createDefaultDecisionConfig();
badId.policyKnowledgeId = '9007199254740993';
assert.equal(validateDecisionConfig(badId), null);
badId.policyKnowledgeId = '1.5';
assert.match(validateDecisionConfig(badId), /ID/);

const legacy = normalizeDecisionConfig(undefined);
assert.equal(legacy.maxScenarios, 4);
assert.deepEqual(legacy.requireApprovalLevels, ['HIGH', 'CRITICAL']);

const timeout = createDefaultDecisionConfig();
timeout.analysisTimeoutSeconds = 1;
assert.match(validateDecisionConfig(timeout), /分析超时/);
timeout.analysisTimeoutSeconds = 60;
timeout.actionTimeoutSeconds = 999;
assert.match(validateDecisionConfig(timeout), /动作超时/);
timeout.actionTimeoutSeconds = 30;
timeout.staleThresholdSeconds = 1;
assert.match(validateDecisionConfig(timeout), /证据过期/);

const overflowId = createDefaultDecisionConfig();
overflowId.policyKnowledgeId = '9223372036854775808';
assert.match(validateDecisionConfig(overflowId), /Long/);

process.stdout.write('Decision config checks: 10 passed' + String.fromCharCode(10));

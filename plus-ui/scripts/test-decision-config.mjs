import assert from 'node:assert/strict';
import {
  createDefaultDecisionConfig,
  decisionRiskPointSum,
  decisionRankingWeightSum,
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

process.stdout.write('Decision config checks: 5 passed' + String.fromCharCode(10));

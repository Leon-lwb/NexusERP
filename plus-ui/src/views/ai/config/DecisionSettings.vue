<template>
  <el-divider content-position="left">工业决策运行护栏</el-divider>
  <el-alert type="warning" show-icon :closable="false" class="mb-4" title="风险和方案评分由业务服务计算；高风险与严重风险必须人工审批" />
  <el-row :gutter="20">
    <el-col :span="8"
      ><el-form-item label="启用工业决策"><el-switch v-model="model.enabled" /></el-form-item
    ></el-col>
    <el-col :span="8">
      <el-form-item label="最大并发运行">
        <el-input-number v-model="model.maxConcurrentRuns" :min="1" :max="16" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="方案数量上限">
        <el-input-number v-model="model.maxScenarios" :min="2" :max="8" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
  <el-row :gutter="20">
    <el-col :span="8">
      <el-form-item label="分析超时(秒)">
        <el-input-number v-model="model.analysisTimeoutSeconds" :min="10" :max="300" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="动作超时(秒)">
        <el-input-number v-model="model.actionTimeoutSeconds" :min="5" :max="120" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="证据过期(秒)">
        <el-input-number v-model="model.staleThresholdSeconds" :min="30" :max="3600" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
  <el-row :gutter="20">
    <el-col :span="8">
      <el-form-item label="默认推演(天)">
        <el-input-number v-model="model.defaultHorizonDays" :min="1" :max="90" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="最大推演(天)">
        <el-input-number v-model="model.maxHorizonDays" :min="1" :max="90" controls-position="right" style="width: 100%" />
      </el-form-item>
    </el-col>
    <el-col :span="8">
      <el-form-item label="制度知识库 ID">
        <el-input v-model="model.policyKnowledgeId" clearable placeholder="留空跳过 SOP / RAG 检索" />
      </el-form-item>
    </el-col>
  </el-row>
  <el-row :gutter="20">
    <el-col :span="12"
      ><el-form-item label="低风险自动通知"><el-switch v-model="model.allowAutoLowRiskNotify" /></el-form-item
    ></el-col>
    <el-col :span="12"
      ><el-form-item label="基准测试模式"><el-switch v-model="model.benchmarkMode" /></el-form-item
    ></el-col>
  </el-row>
  <el-form-item label="人工审批等级">
    <el-checkbox-group v-model="model.requireApprovalLevels">
      <el-checkbox value="MEDIUM">中等风险</el-checkbox>
      <el-checkbox value="HIGH" disabled>高风险（必选）</el-checkbox>
      <el-checkbox value="CRITICAL" disabled>严重风险（必选）</el-checkbox>
    </el-checkbox-group>
  </el-form-item>

  <el-divider content-position="left">风险评分分值（合计 100）</el-divider>
  <el-row :gutter="20">
    <el-col v-for="field in riskFields" :key="field.key" :span="12">
      <el-form-item :label="field.label">
        <el-input-number v-model="model.risk[field.key]" :min="0" :max="100" :step="1" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
  <el-tag :type="riskPointTotal === 100 ? 'success' : 'danger'">当前总分：{{ riskPointTotal }} / 100</el-tag>

  <el-divider content-position="left">方案排名权重（合计 1）</el-divider>
  <el-row :gutter="20">
    <el-col v-for="field in rankingFields" :key="field.key" :span="12">
      <el-form-item :label="field.label">
        <el-input-number v-model="model.ranking[field.key]" :min="0" :max="1" :step="0.05" :precision="2" style="width: 100%" />
      </el-form-item>
    </el-col>
  </el-row>
  <el-tag :type="Math.abs(rankingWeightTotal - 1) < 0.000001 ? 'success' : 'danger'">当前权重：{{ rankingWeightTotal }} / 1</el-tag>
</template>

<script setup lang="ts">
import type { DecisionConfig, DecisionRankingConfig, DecisionRiskConfig } from '@/api/ai/config';
import { decisionRankingWeightSum, decisionRiskPointSum } from './decisionValidation';

const model = defineModel<DecisionConfig>({ required: true });

const riskFields: Array<{ key: keyof DecisionRiskConfig; label: string }> = [
  { key: 'tardinessPoints', label: '预计拖期' },
  { key: 'shortagePoints', label: '物料短缺' },
  { key: 'capacityPoints', label: '产能瓶颈' },
  { key: 'supplierPoints', label: '供应风险' }
];
const rankingFields: Array<{ key: keyof DecisionRankingConfig; label: string }> = [
  { key: 'tardinessWeight', label: '总拖期天数' },
  { key: 'delayedCountWeight', label: '拖期工单数' },
  { key: 'shortageWeight', label: '缺料缺口' },
  { key: 'utilizationWeight', label: '资源超负荷' },
  { key: 'actionCostWeight', label: '动作成本' },
  { key: 'planChangeWeight', label: '计划变更' },
  { key: 'infeasibleWeight', label: '不可行惩罚' }
];

const riskPointTotal = computed(() => decisionRiskPointSum(model.value.risk));
const rankingWeightTotal = computed(() => decisionRankingWeightSum(model.value.ranking));
</script>

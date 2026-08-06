import type { Component } from 'vue';
import { ChatDotRound, Cpu, Connection, MagicStick, SetUp } from '@element-plus/icons-vue';

/**
 * 流程节点类型注册表
 * <p>type → 图标/默认配置/展示配置。与后端 /ai/flow/node-types 的 type 对齐</p>
 */
export interface FlowNodeTypeConfig {
  type: string;
  label: string;
  icon: Component;
  color: string;
  /** 默认配置（新拖入节点时使用） */
  defaultConfig: Record<string, unknown>;
}

export const NODE_TYPE_CONFIGS: FlowNodeTypeConfig[] = [
  {
    type: 'llm',
    label: 'LLM 调用',
    icon: ChatDotRound,
    color: '#409eff',
    defaultConfig: {
      modelId: 0,
      promptTemplate: '',
      systemPrompt: '',
      temperature: 0.7,
      timeoutSeconds: 300
    }
  },
  {
    type: 'agent',
    label: 'Agent 智能体',
    icon: MagicStick,
    color: '#9254de',
    defaultConfig: {
      modelId: 0,
      queryTemplate: '',
      toolCategories: 'QUERY,ALL',
      maxAgentInvocations: 20
    }
  },
  {
    type: 'rag',
    label: 'RAG 检索',
    icon: Connection,
    color: '#36cfc9',
    defaultConfig: {
      knowledgeId: 0,
      queryTemplate: '',
      topK: 5,
      minScore: 0.5
    }
  },
  {
    type: 'if',
    label: '条件分支',
    icon: SetUp,
    color: '#faad14',
    defaultConfig: {
      logic: 'AND',
      conditions: [{ variable: '', operator: 'EQ', value: '' }]
    }
  },
  {
    type: 'code',
    label: '代码转换',
    icon: Cpu,
    color: '#52c41a',
    defaultConfig: {
      expressions: {}
    }
  }
];

/** 按类型取配置 */
export function getNodeTypeConfig(type: string): FlowNodeTypeConfig | undefined {
  return NODE_TYPE_CONFIGS.find((c) => c.type === type);
}

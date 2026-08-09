import type { Component } from 'vue';
import {
  Bell,
  ChatDotRound,
  Connection,
  Cpu,
  DataLine,
  EditPen,
  MagicStick,
  Message,
  Odometer,
  Promotion,
  Refresh,
  SetUp,
  Timer
} from '@element-plus/icons-vue';

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
  },
  {
    type: 'http',
    label: 'HTTP 请求',
    icon: Connection,
    color: '#13c2c2',
    defaultConfig: {
      method: 'GET',
      url: '',
      headers: {},
      body: '',
      timeoutSeconds: 15
    }
  },
  {
    type: 'notify',
    label: '站内通知',
    icon: Bell,
    color: '#fa8c16',
    defaultConfig: {
      userId: '',
      title: '',
      content: '',
      category: 'system'
    }
  },
  {
    type: 'sql',
    label: '数据库查询',
    icon: DataLine,
    color: '#1677ff',
    defaultConfig: {
      sqlTemplate: '',
      maxRows: 50
    }
  },
  {
    type: 'erp_tool',
    label: 'ERP 业务工具',
    icon: Odometer,
    color: '#722ed1',
    defaultConfig: {
      toolName: '',
      args: {}
    }
  },
  {
    type: 'mail',
    label: '邮件发送',
    icon: Message,
    color: '#13c2c2',
    defaultConfig: {
      to: '',
      subject: '',
      content: '',
      isHtml: false
    }
  },
  {
    type: 'sms',
    label: '短信发送',
    icon: Promotion,
    color: '#eb2f96',
    defaultConfig: {
      phones: '',
      templateId: '',
      params: {},
      configId: 'config1'
    }
  },
  {
    type: 'im_webhook',
    label: 'IM 群机器人',
    icon: Connection,
    color: '#2f54eb',
    defaultConfig: {
      channel: 'wecom',
      webhookUrl: '',
      content: '',
      timeoutSeconds: 10
    }
  },
  {
    type: 'set',
    label: '变量设置',
    icon: EditPen,
    color: '#52c41a',
    defaultConfig: {
      variables: {}
    }
  },
  {
    type: 'wait',
    label: '延迟等待',
    icon: Timer,
    color: '#faad14',
    defaultConfig: {
      waitSeconds: 5
    }
  },
  {
    type: 'merge',
    label: '合并汇总',
    icon: Connection,
    color: '#13c2c2',
    defaultConfig: {}
  },
  {
    type: 'split',
    label: '循环分批',
    icon: Refresh,
    color: '#2f54eb',
    defaultConfig: {
      arrayPath: 'input.items',
      batchSize: 1
    }
  }
];

/** 按类型取配置 */
export function getNodeTypeConfig(type: string): FlowNodeTypeConfig | undefined {
  return NODE_TYPE_CONFIGS.find((c) => c.type === type);
}

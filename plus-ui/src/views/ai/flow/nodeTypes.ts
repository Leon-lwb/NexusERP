import type { Component } from 'vue';
import {
  Bell,
  ChatDotRound,
  Connection,
  Cpu,
  DataLine,
  EditPen,
  FolderOpened,
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

/** 节点分类（面板分组 + 空画布引导） */
export type NodeCategory = 'ai' | 'data' | 'flow' | 'integration' | 'notification';

export const NODE_CATEGORIES: { key: NodeCategory; label: string }[] = [
  { key: 'ai', label: 'AI 智能' },
  { key: 'data', label: '数据处理' },
  { key: 'flow', label: '流程控制' },
  { key: 'integration', label: '集成连接' },
  { key: 'notification', label: '通知输出' }
];

export interface FlowNodeTypeConfig {
  type: string;
  label: string;
  icon: Component;
  color: string;
  /** 节点分类（面板分组） */
  category: NodeCategory;
  /** 一句话说明（面板 tooltip / 属性面板头部 / 空画布引导） */
  description: string;
  /** 默认配置（新拖入节点时使用） */
  defaultConfig: Record<string, unknown>;
}

export const NODE_TYPE_CONFIGS: FlowNodeTypeConfig[] = [
  {
    type: 'llm',
    label: 'LLM 调用',
    icon: ChatDotRound,
    color: '#409eff',
    category: 'ai',
    description: '调用大语言模型生成文本，prompt 支持 {{}} 变量模板',
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
    category: 'ai',
    description: '多轮工具调用智能体，可自动调用 ERP 查询类工具',
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
    category: 'ai',
    description: '从知识库向量检索相关内容，供下游 LLM 引用',
    defaultConfig: {
      knowledgeId: 0,
      queryTemplate: '',
      topK: 5,
      minScore: 0.5
    }
  },
  {
    type: 'erp_tool',
    label: 'ERP 业务工具',
    icon: Odometer,
    color: '#722ed1',
    category: 'ai',
    description: '调用 ERP 业务工具（库存查询/客户查询等），先选择工具再填参数',
    defaultConfig: {
      toolName: '',
      args: {}
    }
  },
  {
    type: 'sql',
    label: '数据库查询',
    icon: DataLine,
    color: '#1677ff',
    category: 'data',
    description: '在 ERP 库执行只读 SELECT（自动租户隔离 + 危险语句拦截）',
    defaultConfig: {
      sqlTemplate: '',
      maxRows: 50
    }
  },
  {
    type: 'code',
    label: '代码转换',
    icon: Cpu,
    color: '#52c41a',
    category: 'data',
    description: '用 JS 表达式对字段做转换（如格式化/拼接/计算）',
    defaultConfig: {
      expressions: {}
    }
  },
  {
    type: 'set',
    label: '变量设置',
    icon: EditPen,
    color: '#52c41a',
    category: 'data',
    description: '给输出变量赋值，可引用上游节点输出或运行入参',
    defaultConfig: {
      variables: {}
    }
  },
  {
    type: 'if',
    label: '条件分支',
    icon: SetUp,
    color: '#faad14',
    category: 'flow',
    description: '按条件判断分流：命中走 true 分支，否则走 false 分支',
    defaultConfig: {
      logic: 'AND',
      conditions: [{ variable: '', operator: 'EQ', value: '' }]
    }
  },
  {
    type: 'split',
    label: '循环分批',
    icon: Refresh,
    color: '#2f54eb',
    category: 'flow',
    description: '把数组按批拆分循环处理：loop 端口进循环体，done 端口汇总结束',
    defaultConfig: {
      arrayPath: 'input.items',
      batchSize: 1
    }
  },
  {
    type: 'merge',
    label: '合并汇总',
    icon: Connection,
    color: '#13c2c2',
    category: 'flow',
    description: '汇聚多个分支为单输出（动态输入端口，按实际入边数显示）',
    defaultConfig: {}
  },
  {
    type: 'aggregate',
    label: '聚合汇总',
    icon: DataLine,
    color: '#13c2c2',
    category: 'flow',
    description: '收集循环批次的输出为数组（配合 split 使用，自动溯源循环体）',
    defaultConfig: {
      mode: 'ALL_UPSTREAM'
    }
  },
  {
    type: 'wait',
    label: '延迟等待',
    icon: Timer,
    color: '#faad14',
    category: 'flow',
    description: '等待指定秒数后继续（用于限流/间隔调度）',
    defaultConfig: {
      waitSeconds: 5
    }
  },
  {
    type: 'http',
    label: 'HTTP 请求',
    icon: Connection,
    color: '#13c2c2',
    category: 'integration',
    description: '调用外部 HTTP 接口（GET/POST 等）',
    defaultConfig: {
      method: 'GET',
      url: '',
      headers: {},
      body: '',
      timeoutSeconds: 15
    }
  },
  {
    type: 'mq_send',
    label: 'MQ 消息发送',
    icon: Promotion,
    color: '#722ed1',
    category: 'integration',
    description: '发送 RabbitMQ 业务消息（路由键下拉选择）',
    defaultConfig: {
      routingKey: '',
      payload: '{}',
      exchange: 'erp.topic.exchange'
    }
  },
  {
    type: 'mail',
    label: '邮件发送',
    icon: Message,
    color: '#13c2c2',
    category: 'integration',
    description: '发送邮件（收件人/主题/正文，支持 HTML）',
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
    category: 'integration',
    description: '发送短信（模板参数键值对）',
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
    category: 'integration',
    description: '向 IM 群发送机器人消息（企业微信/钉钉）',
    defaultConfig: {
      channel: 'wecom',
      webhookUrl: '',
      content: '',
      timeoutSeconds: 10
    }
  },
  {
    type: 'oss',
    label: 'OSS 文件',
    icon: FolderOpened,
    color: '#fa8c16',
    category: 'integration',
    description: '上传文本到 OSS / 读取 OSS 对象内容',
    defaultConfig: {
      action: 'UPLOAD_TEXT',
      fileName: '',
      content: '',
      filePath: ''
    }
  },
  {
    type: 'notify',
    label: '站内通知',
    icon: Bell,
    color: '#fa8c16',
    category: 'notification',
    description: '发送站内通知（顶部铃铛 + 实时推送，可跳转流程详情）',
    defaultConfig: {
      userId: '',
      title: '',
      content: '',
      category: 'system'
    }
  }
];

/** 按类型取配置 */
export function getNodeTypeConfig(type: string): FlowNodeTypeConfig | undefined {
  return NODE_TYPE_CONFIGS.find((c) => c.type === type);
}

/** 按分类取配置（面板分组） */
export function getNodeTypesByCategory(category: NodeCategory): FlowNodeTypeConfig[] {
  return NODE_TYPE_CONFIGS.filter((c) => c.category === category);
}

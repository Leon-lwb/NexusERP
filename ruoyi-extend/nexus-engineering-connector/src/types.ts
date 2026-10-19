/** 连接器与Nexus协商的固定主协议。 */
export const PROTOCOL = 'nexus-engineering/1' as const;
/** 本机配置只含连接位置，不含身份凭证。 */
export interface ConnectorConfig {
  protocolVersion: typeof PROTOCOL;
  serverUrl: string;
  executable: string;
  workspaceRoot: string;
  /** 兼容旧配置保留；HTTP地址不再依赖此开关。 */
  development: boolean;
}
/** 服务端下发的受控动作，不接受任意代码或本地路径。 */
export type EngineeringAction = 'CHECK_CONNECTION' | 'OPEN_PROJECT' | 'CREATE_PCB_DRAFT' | 'EXPORT_ARTIFACTS' | 'RUN_DRC' | 'SUBMIT_DRAFT';
/** 当前租约的任务数据，ID按字符串无损传输。 */
export interface TaskLease {
  taskId: string;
  action: EngineeringAction;
  attempt: number;
  revision: number;
  leaseToken: string;
  leaseExpiresAt: string;
  deadlineAt: string;
  templateId?: string;
  templateVersion?: string;
  templateSha256?: string;
  parentTaskId?: string;
  parameters: Record<string, unknown>;
}
/** 本地文件清单；绝对路径只在连接器内部使用。 */
export interface LocalArtifact {
  type: string;
  relativePath: string;
  localPath: string;
  sha256: string;
  size: number;
  contentType: string;
  groupId?: string;
}

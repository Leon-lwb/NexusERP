export interface SerialNumberRuleQuery extends PageQuery {
  ruleCode?: string;
  ruleName?: string;
  ruleType?: string;
  status?: string;
}

export interface SerialNumberRuleVO {
  /** 服务端 CAS 原版本，Long 已序列化为字符串。 */
  ruleVersion: string;
  ruleId: number;
  ruleCode: string;
  ruleName: string;
  ruleType: string;
  prefix: string;
  dateFormat: string;
  seqLength: number;
  seqCycle: string;
  currentSeq: number;
  checkDigitRule: string;
  status: string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface SerialNumberRuleForm {
  /** 编辑时必须保留详情返回的原版本，新增由服务端分配。 */
  ruleVersion?: string;
  ruleId?: number;
  ruleCode: string;
  ruleName: string;
  ruleType: string;
  prefix?: string;
  dateFormat?: string;
  seqLength?: number;
  seqCycle?: string;
  currentSeq?: number;
  checkDigitRule?: string;
  status?: string;
  remark?: string;
}

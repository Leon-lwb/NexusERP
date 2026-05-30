export interface SerialNumberRuleQuery extends PageQuery {
  ruleCode?: string;
  ruleName?: string;
  ruleType?: string;
  status?: string;
}

export interface SerialNumberRuleVO {
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

export interface PutawayRuleQuery extends PageQuery {
  ruleCode?: string;
  ruleName?: string;
  warehouseId?: number;
  materialCategoryId?: number;
  strategyType?: string;
  status?: number;
}

export interface PutawayRuleVO {
  ruleId: number;
  ruleCode: string;
  ruleName: string;
  warehouseId: number;
  warehouseName: string;
  materialCategoryId: number;
  categoryName: string;
  strategyType: string;
  priority: number;
  status: number | string;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PutawayRuleForm {
  ruleId?: number;
  ruleCode?: string;
  ruleName?: string;
  warehouseId?: number;
  materialCategoryId?: number;
  strategyType?: string;
  priority?: number;
  status?: number | string;
  remark?: string;
}

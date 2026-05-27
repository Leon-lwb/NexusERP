export interface PickingRuleQuery extends PageQuery {
  ruleCode?: string;
  ruleName?: string;
  warehouseId?: number;
  categoryId?: number;
  strategyType?: string;
  status?: number;
}

export interface PickingRuleVO {
  ruleId: number;
  ruleCode: string;
  ruleName: string;
  warehouseId: number;
  warehouseName: string;
  categoryId: number;
  categoryName: string;
  strategyType: string;
  priority: number;
  status: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface PickingRuleForm {
  ruleId?: number;
  ruleCode: string;
  ruleName: string;
  warehouseId: number;
  categoryId?: number;
  strategyType: string;
  priority: number;
  status: number;
  remark?: string;
}

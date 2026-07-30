/** 会计期间查询参数 */
export interface PeriodQuery extends PageQuery {
  fiscalYear?: number;
  periodNo?: number;
  status?: string;
}

/** 会计期间视图对象 */
export interface PeriodVO {
  periodId: number;
  fiscalYear: number;
  periodNo: number;
  periodName: string;
  startDate: string;
  endDate: string;
  status: string;
  closedBy?: number;
  closedTime?: string;
}

/** 会计期间表单对象 */
export interface PeriodForm {
  periodId?: number;
  fiscalYear: number;
  periodNo: number;
  periodName?: string;
  startDate?: string;
  endDate?: string;
  status?: string;
}

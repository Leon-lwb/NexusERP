/** 会计科目查询参数 */
export interface AccountQuery {
  /** 科目编码 */
  accountCode?: string;
  /** 科目名称 */
  accountName?: string;
  /** 科目类型 */
  accountType?: string;
  /** 状态 0-正常 1-停用 */
  status?: string;
}

/** 会计科目视图对象 */
export interface AccountVO {
  accountId: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  balanceDir: string;
  parentId: number;
  levelNum: number;
  pathIds?: string;
  pathNames?: string;
  isLeaf: string;
  isRecon: string;
  reconType?: string;
  isOpenItem: string;
  currency?: string;
  status: string;
  sortOrder: number;
  children?: AccountVO[];
}

/** 会计科目表单对象 */
export interface AccountForm {
  accountId?: number;
  accountCode: string;
  accountName: string;
  accountType: string;
  balanceDir: string;
  parentId?: number;
  isLeaf?: string;
  isRecon?: string;
  reconType?: string;
  isOpenItem?: string;
  currency?: string;
  status?: string;
  sortOrder?: number;
}

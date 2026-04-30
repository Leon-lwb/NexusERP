export interface CategoryQuery extends PageQuery {
  categoryCode?: string;
  categoryName?: string;
  status?: number;
}

export interface CategoryVO {
  categoryId: number;
  parentId: number;
  categoryCode: string;
  categoryName: string;
  categoryDesc: string;
  levelNum: number;
  pathIds: string;
  pathNames: string;
  sortOrder: number;
  status: number;
  remark: string;
  createBy: number;
  createTime: string;
  updateBy: number;
  updateTime: string;
}

export interface CategoryForm {
  categoryId?: number;
  parentId?: number;
  categoryCode: string;
  categoryName: string;
  categoryDesc?: string;
  levelNum?: number;
  pathIds?: string;
  pathNames?: string;
  sortOrder?: number;
  status?: number;
  remark?: string;
}

export interface CategoryWithMaterialsVO {
  categories: CategoryVO[];
  materials: any[];
}

import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 发布包固件映射 */
export interface ReleasePkgFirmwareVO {
  mappingId?: number;
  packageId?: number;
  routingStepId?: number;
  processId?: number;
  processCode?: string;
  processName?: string;
  pcbaMaterialId?: number;
  pcbaMaterialCode?: string;
  pcbaMaterialName?: string;
  firmwareId: number;
  firmwareCode?: string;
  firmwareName?: string;
  firmwareVersion?: string;
  sortOrder?: number;
  remark?: string;
}

/** 发布包BOM选配 */
export interface ReleasePkgBomItemVO {
  itemId?: number;
  packageId?: number;
  actionType: string;
  childMaterialId?: number;
  childMaterialCode?: string;
  childMaterialName?: string;
  bomDetailId?: number;
  targetMaterialId?: number;
  targetMaterialCode?: string;
  targetMaterialName?: string;
  quantityPerParent?: number;
  sortOrder?: number;
  remark?: string;
}

/** 发布包配置项 */
export interface ReleasePkgConfigVO {
  configId?: number;
  packageId?: number;
  configGroup: string;
  configKey: string;
  configValue?: string;
  configType?: string;
  processId?: number;
  processCode?: string;
  sortOrder?: number;
  remark?: string;
}

/** 发布包视图对象 */
export interface ReleasePackageVO {
  packageId: number;
  packageCode: string;
  clientId: number;
  clientName?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  customerProductCode?: string;
  customerProductName?: string;
  version: string;
  releaseNotes?: string;
  status: string;
  effectiveDate?: string;
  expiryDate?: string;
  isDefault?: string;
  selectFlowId?: number;
  publishedBy?: number;
  publishedByName?: string;
  publishTime?: string;
  createBy?: number;
  createByName?: string;
  remark?: string;
  createTime?: string;
  firmwareMappings?: ReleasePkgFirmwareVO[];
  bomItems?: ReleasePkgBomItemVO[];
  configItems?: ReleasePkgConfigVO[];
}

/** 发布包查询条件 */
export interface ReleasePackageQuery extends PageQuery {
  packageCode?: string;
  clientId?: number;
  clientName?: string;
  materialId?: number;
  materialName?: string;
  customerProductCode?: string;
  status?: string;
}

/** 发布包表单(新增/编辑, 含三个子表) */
export interface ReleasePackageForm {
  packageId?: number;
  packageCode?: string;
  clientId: number;
  clientName?: string;
  materialId: number;
  materialCode?: string;
  materialName?: string;
  customerProductCode?: string;
  customerProductName?: string;
  version: string;
  releaseNotes?: string;
  effectiveDate?: string;
  expiryDate?: string;
  remark?: string;
  firmwareMappings?: ReleasePkgFirmwareVO[];
  bomItems?: ReleasePkgBomItemVO[];
  configItems?: ReleasePkgConfigVO[];
}

/** 分页查询发布包列表 */
export const listReleasePackage = (query?: ReleasePackageQuery): AxiosPromise<ReleasePackageVO[]> =>
  request({ url: '/mes/release/package/list', method: 'get', params: query });

/** 获取发布包详情(含固件映射/BOM选配/配置项子表) */
export const getReleasePackage = (packageId: number | string): AxiosPromise<ReleasePackageVO> =>
  request({ url: `/mes/release/package/${packageId}`, method: 'get' });

/** 新增发布包(编码自动生成 + 状态 DRAFT) */
export const addReleasePackage = (data: ReleasePackageForm) => request({ url: '/mes/release/package', method: 'post', data });

/** 修改发布包(仅 DRAFT/ARCHIVED 可编辑) */
export const updateReleasePackage = (data: ReleasePackageForm) => request({ url: '/mes/release/package', method: 'put', data });

/** 批量删除发布包(仅 DRAFT 可删) */
export const delReleasePackage = (packageIds: number | number[]) => request({ url: `/mes/release/package/${packageIds}`, method: 'delete' });

/** 提交审批: DRAFT -> IN_REVIEW (selectFlowId 非空走 Flowable) */
export const submitReleasePackage = (packageId: number | string, selectFlowId?: string) =>
  request({ url: `/mes/release/package/submit/${packageId}`, method: 'post', params: { selectFlowId } });

/** 兜底审批通过: IN_REVIEW -> RELEASED */
export const approveReleasePackage = (packageId: number | string, remark?: string) =>
  request({ url: `/mes/release/package/approve/${packageId}`, method: 'put', params: { remark } });

/** 兜底驳回: IN_REVIEW -> DRAFT */
export const rejectReleasePackage = (packageId: number | string, reason?: string) =>
  request({ url: `/mes/release/package/reject/${packageId}`, method: 'put', params: { reason } });

/** 归档: RELEASED -> ARCHIVED */
export const archiveReleasePackage = (packageId: number | string, reason?: string) =>
  request({ url: `/mes/release/package/archive/${packageId}`, method: 'put', params: { reason } });

/** 设为客户+物料默认发布包(仅 RELEASED) */
export const setDefaultReleasePackage = (packageId: number | string) => request({ url: `/mes/release/package/default/${packageId}`, method: 'put' });

/** 按客户+物料(+客户产品代号)匹配已发布发布包 */
export const matchReleasePackage = (clientId: number, materialId: number, customerProductCode?: string): AxiosPromise<ReleasePackageVO> =>
  request({ url: '/mes/release/package/match', method: 'get', params: { clientId, materialId, customerProductCode } });

/** 按客户+物料查询所有已发布(RELEASED)发布包 */
export const listReleasePackageByClientMaterial = (clientId: number, materialId: number): AxiosPromise<ReleasePackageVO[]> =>
  request({ url: '/mes/release/package/list-by-client-material', method: 'get', params: { clientId, materialId } });

/** 查询可发起审批流程列表(提交审批弹窗用) */
export const getReleasePackageFlows = (): AxiosPromise<any[]> => request({ url: '/mes/release/package/flows', method: 'get' });

/** 查询流程实例ID(审批表单页"查看流程"跳转用) */
export const getReleasePackageProcId = (packageId: number | string): AxiosPromise<string> =>
  request({ url: `/mes/release/package/${packageId}/proc`, method: 'get' });

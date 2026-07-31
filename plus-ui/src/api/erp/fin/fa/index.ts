import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** 资产类别 */
export interface FaAssetCategoryVO {
  categoryId: number;
  categoryCode: string;
  categoryName: string;
  depreciationMethod: string;
  usefulLife: number;
  salvageValueRate: number;
  debitAccountId: number;
  creditAccountId: number;
  disposalLossAccountId: number;
  disposalGainAccountId: number;
  status: string;
  sortOrder: number;
  debitAccountName: string;
  creditAccountName: string;
}

/** 资产卡片 */
export interface FaAssetVO {
  assetId: number;
  assetNo: string;
  assetName: string;
  categoryId: number;
  categoryName: string;
  originalValue: number;
  salvageValue: number;
  usefulLife: number;
  depreciationMethod: string;
  methodLabel: string;
  acquisitionDate: string;
  inUseDate: string;
  location: string;
  status: string;
  cumulativeDepreciation: number;
  netBookValue: number;
  netValue: number;
  version: number;
}

/** 折旧记录 */
export interface FaDepreciationVO {
  depreciationId: number;
  assetId: number;
  assetNo: string;
  assetName: string;
  periodId: number;
  periodName: string;
  depreciationAmount: number;
  cumulativeDepreciation: number;
  status: string;
  postedVoucherId: number;
  postedVoucherNo: string;
}

/** 处置记录 */
export interface FaDisposalVO {
  disposalId: number;
  assetId: number;
  assetNo: string;
  assetName: string;
  disposalType: string;
  disposalDate: string;
  disposalPrice: number;
  netBookValue: number;
  gainLoss: number;
  postedVoucherNo: string;
  remark: string;
}

// ===== 类别 =====
export const listCategory = (params: any): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/category/list', method: 'get', params });
};

export const listAllCategory = (): AxiosPromise<FaAssetCategoryVO[]> => {
  return request({ url: '/erp/fin/fa/category/all', method: 'get' });
};

export const getCategory = (categoryId: number): AxiosPromise<FaAssetCategoryVO> => {
  return request({ url: `/erp/fin/fa/category/${categoryId}`, method: 'get' });
};

export const addCategory = (data: Partial<FaAssetCategoryVO>): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/category', method: 'post', data });
};

export const updateCategory = (data: Partial<FaAssetCategoryVO>): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/category', method: 'put', data });
};

export const delCategory = (categoryIds: number[]): AxiosPromise<any> => {
  return request({ url: `/erp/fin/fa/category/${categoryIds.join(',')}`, method: 'delete' });
};

// ===== 资产卡片 =====
export const listAsset = (params: any): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/asset/list', method: 'get', params });
};

export const getAsset = (assetId: number): AxiosPromise<FaAssetVO> => {
  return request({ url: `/erp/fin/fa/asset/${assetId}`, method: 'get' });
};

export const addAsset = (data: Partial<FaAssetVO>): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/asset', method: 'post', data });
};

export const updateAsset = (data: Partial<FaAssetVO>): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/asset', method: 'put', data });
};

export const delAsset = (assetIds: number[]): AxiosPromise<any> => {
  return request({ url: `/erp/fin/fa/asset/${assetIds.join(',')}`, method: 'delete' });
};

export const adjustAssetValue = (assetId: number, newValue: number): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/asset/adjustValue', method: 'put', params: { assetId, newValue } });
};

export const activateAsset = (assetId: number): AxiosPromise<any> => {
  return request({ url: `/erp/fin/fa/asset/activate/${assetId}`, method: 'put' });
};

// ===== 折旧 =====
export const listDepreciation = (params: any): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/depreciation/list', method: 'get', params });
};

export const depreciationPlan = (periodId: number): AxiosPromise<FaDepreciationVO[]> => {
  return request({ url: '/erp/fin/fa/depreciation/plan', method: 'get', params: { periodId } });
};

export const runDepreciation = (periodId: number): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/depreciation/run', method: 'post', data: { periodId } });
};

// ===== 处置 =====
export const listDisposal = (params: any): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/disposal/list', method: 'get', params });
};

export const addDisposal = (data: Partial<FaDisposalVO>): AxiosPromise<any> => {
  return request({ url: '/erp/fin/fa/disposal', method: 'post', data });
};

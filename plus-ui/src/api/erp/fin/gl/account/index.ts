import request from '@/utils/request';
import { AxiosPromise } from 'axios';
import { AccountQuery, AccountVO, AccountForm } from './types';

/** 科目列表(树形,前端 handleTree 构建) */
export const listAccount = (query?: AccountQuery): AxiosPromise<AccountVO[]> => {
  return request({ url: '/erp/fin/gl/account/list', method: 'get', params: query });
};

/** 末级科目(凭证录入下拉) */
export const leafAccounts = (accountType?: string): AxiosPromise<AccountVO[]> => {
  return request({ url: '/erp/fin/gl/account/leaf', method: 'get', params: { accountType } });
};

/** 科目详情 */
export const getAccount = (accountId: string | number): AxiosPromise<AccountVO> => {
  return request({ url: '/erp/fin/gl/account/' + accountId, method: 'get' });
};

/** 新增科目 */
export const addAccount = (data: AccountForm) => {
  return request({ url: '/erp/fin/gl/account', method: 'post', data });
};

/** 修改科目 */
export const updateAccount = (data: AccountForm) => {
  return request({ url: '/erp/fin/gl/account', method: 'put', data });
};

/** 删除科目 */
export const delAccount = (accountId: string | number | (string | number)[]) => {
  return request({ url: '/erp/fin/gl/account/' + accountId, method: 'delete' });
};

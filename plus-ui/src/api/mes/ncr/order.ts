import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/** NCR不合格品视图对象 */
export interface NcrOrderVO {
  ncrId: number;
  ncrCode?: string;
  sourceType?: string;
  sourceTypeName?: string;
  woId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  defectQty?: number;
  status?: string;
  disposition?: string;
  reviewBy?: number;
  reviewByName?: string;
  reviewTime?: string;
  dispositionBy?: number;
  dispositionByName?: string;
  dispositionTime?: string;
  remark?: string;
  createTime?: string;
  createBy?: string;
}

/** NCR不合格品查询条件 */
export interface NcrOrderQuery extends PageQuery {
  ncrCode?: string;
  status?: string;
  sourceType?: string;
  woNo?: string;
  materialId?: number;
}

/** NCR不合格品表单 */
export interface NcrOrderForm {
  ncrId?: number;
  ncrCode?: string;
  sourceType?: string;
  woId?: number;
  woNo?: string;
  materialId?: number;
  materialCode?: string;
  materialName?: string;
  defectQty?: number;
  remark?: string;
}

/** 分页查询NCR列表 */
export const listOrder = (query?: NcrOrderQuery): AxiosPromise<NcrOrderVO[]> => request({ url: '/mes/ncr/order/list', method: 'get', params: query });

/** 获取NCR详情 */
export const getOrder = (ncrId: number | string): AxiosPromise<NcrOrderVO> => request({ url: `/mes/ncr/order/${ncrId}`, method: 'get' });

/** 新增NCR */
export const addOrder = (data: NcrOrderForm) => request({ url: '/mes/ncr/order', method: 'post', data });

/** 修改NCR */
export const updateOrder = (data: NcrOrderForm) => request({ url: '/mes/ncr/order', method: 'put', data });

/** 批量删除NCR */
export const delOrder = (ncrIds: number | number[]) => request({ url: `/mes/ncr/order/${ncrIds}`, method: 'delete' });

/** NCR评审(通过) */
export const reviewOrder = (ncrId: number | string) => request({ url: `/mes/ncr/order/review/${ncrId}`, method: 'put' });

/** NCR处置 */
export const disposeOrder = (ncrId: number | string, disposition: string, remark?: string) =>
  request({ url: `/mes/ncr/order/dispose/${ncrId}`, method: 'put', params: { disposition, remark } });

/**
 * NCR处置（带电子签名）
 *
 * <p>与 {@link disposeOrder} 的区别只有二级认证：处置是授权行为，后端 {@code SignAuthMethod}
 * <b>不接受 LOGIN_ONLY</b>（会直接拒绝），故 {@code authMethod} 固定传 {@code PASSWORD}，
 * {@code credential} 为当前登录密码 —— 只随本次请求体发送，不写 Pinia / localStorage / URL。</p>
 *
 * <p><b>凭证走请求体而非 URL query：</b>后端为 {@code @RequestBody NcrDisposeSignBo}。
 * 密码出现在 query 上会留痕浏览器历史/访问日志/代理日志，且会被操作日志切面明文落库。</p>
 *
 * <p>本接口是纯逻辑签名（只记签署人 / 时间 / IP / 哈希链），<b>没有印章参数</b>，不要加 sealId。</p>
 *
 * <p>后端拒签（如「签署密码错误」）返回 code=500，由 {@code utils/request.ts} 的拦截器统一
 * ElMessage 提示后以 {@code Error(msg)} 拒绝；调用方据此保持认证弹窗打开供改正重试，
 * <b>不要</b>再加 {@code ignoreErrorMsg} 头或本地 toast，否则用户会看到两条相同提示。</p>
 *
 * @param ncrId       不合格品报告ID
 * @param disposition 处置方式（REWORK / SCRAP / ACCEPT_AS_IS / RETURN）
 * @param remark      处置备注（可选）
 * @param authMethod  二级认证方式，固定 PASSWORD
 * @param credential  登录密码（二级认证凭证）
 */
export const disposeOrderWithSign = (
  ncrId: number | string,
  disposition: string,
  remark: string | undefined,
  authMethod: string,
  credential: string
) =>
  request({
    url: `/mes/ncr/order/dispose/${ncrId}/sign`,
    method: 'put',
    data: { disposition, remark, authMethod, credential }
  });

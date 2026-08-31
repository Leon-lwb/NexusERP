import request from '@/utils/request';
import { AxiosPromise } from 'axios';

/**
 * 支付测试专用接口（paytest 演示页）
 * <p>
 * 正式支付业务请使用 api/pay/order、api/pay/invoice；
 * 本模块仅收口 paytest 页面对 /pay 测试端点的直接请求。
 */

/** 获取支付链接/二维码内容（alipay 返回 https/表单，wechat 返回 codeUrl） */
export function getPayUrl(payType: string, orderNumber: string): AxiosPromise<string> {
  return request({
    url: `/pay/${payType}/url/${orderNumber}`,
    method: 'get'
  });
}

/** 查询订单支付状态 */
export function queryPayStatus(orderNumber: string): AxiosPromise<{ orderStatus: string }> {
  return request({
    url: `/pay/query/${orderNumber}`,
    method: 'post'
  });
}

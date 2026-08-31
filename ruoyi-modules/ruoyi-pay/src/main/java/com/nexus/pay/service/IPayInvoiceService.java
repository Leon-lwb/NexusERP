package com.nexus.pay.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.pay.domain.PayInvoice;
import com.nexus.pay.domain.bo.PayInvoiceBo;
import com.nexus.pay.domain.vo.PayInvoiceVo;

import java.util.List;

/**
 * 发票Service接口
 *
 * @author geek
 */
public interface IPayInvoiceService {

    /**
     * 分页查询发票列表。
     *
     * @param bo 发票查询条件（订单号、发票类型）
     * @param pageQuery 分页参数
     * @return 分页后的发票视图列表
     */
    TableDataInfo<PayInvoiceVo> selectPageList(PayInvoiceBo bo, PageQuery pageQuery);

    /**
     * 按条件查询全部发票列表（不分页）。
     *
     * @param bo 发票查询条件
     * @return 发票视图列表
     */
    List<PayInvoiceVo> selectList(PayInvoiceBo bo);

    /**
     * 根据发票 ID 查询发票详情。
     *
     * @param invoiceId 发票 ID
     * @return 发票视图对象
     */
    PayInvoiceVo selectById(Long invoiceId);

    /**
     * 新增发票记录。
     *
     * @param payInvoice 发票实体
     * @return 受影响行数
     */
    int insertPayInvoice(PayInvoice payInvoice);

    /**
     * 修改发票记录。
     *
     * @param payInvoice 发票实体
     * @return 受影响行数
     */
    int updatePayInvoice(PayInvoice payInvoice);

    /**
     * 批量删除发票记录。
     *
     * @param invoiceIds 待删除的发票 ID 数组
     * @return 受影响行数
     */
    int deleteByIds(Long[] invoiceIds);

}

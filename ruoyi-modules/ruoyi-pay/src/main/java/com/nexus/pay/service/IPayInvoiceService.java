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

    TableDataInfo<PayInvoiceVo> selectPageList(PayInvoiceBo bo, PageQuery pageQuery);

    List<PayInvoiceVo> selectList(PayInvoiceBo bo);

    PayInvoiceVo selectById(Long invoiceId);

    int insertPayInvoice(PayInvoice payInvoice);

    int updatePayInvoice(PayInvoice payInvoice);

    int deleteByIds(Long[] invoiceIds);

}

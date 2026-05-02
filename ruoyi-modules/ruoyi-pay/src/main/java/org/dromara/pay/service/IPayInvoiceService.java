package org.dromara.pay.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.pay.domain.PayInvoice;
import org.dromara.pay.domain.bo.PayInvoiceBo;
import org.dromara.pay.domain.vo.PayInvoiceVo;

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

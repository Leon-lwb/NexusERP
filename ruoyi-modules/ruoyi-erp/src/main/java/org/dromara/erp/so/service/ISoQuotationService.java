package org.dromara.erp.so.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.bo.SoQuotationBo;
import org.dromara.erp.so.domain.vo.SoQuotationVo;

import java.util.List;

public interface ISoQuotationService {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_CONFIRMED = "CONFIRMED";
    String STATUS_CONVERTED = "CONVERTED";
    String STATUS_CLOSED = "CLOSED";

    TableDataInfo<SoQuotationVo> selectPageList(SoQuotationBo bo, PageQuery pageQuery);

    List<SoQuotationVo> selectList(SoQuotationBo bo);

    SoQuotationVo selectById(Long id);

    int insertByBo(SoQuotationBo bo);

    int updateByBo(SoQuotationBo bo);

    int deleteByIds(Long[] ids);

    /**
     * 确认报价
     */
    void confirm(Long quotationId);

    /**
     * 报价转销售订单
     * @return 生成的销售订单ID
     */
    Long convertToSalesOrder(Long quotationId);
}

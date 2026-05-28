package com.nexus.erp.so.service;

import com.nexus.common.mybatis.core.page.PageQuery;
import com.nexus.common.mybatis.core.page.TableDataInfo;
import com.nexus.erp.so.domain.bo.SoQuotationBo;
import com.nexus.erp.so.domain.vo.SoQuotationVo;

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

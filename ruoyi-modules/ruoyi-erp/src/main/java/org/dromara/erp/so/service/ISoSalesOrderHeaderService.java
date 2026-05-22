package org.dromara.erp.so.service;

import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.erp.so.domain.bo.SoSalesOrderHeaderBo;
import org.dromara.erp.so.domain.vo.SoSalesOrderHeaderVo;
import org.dromara.erp.so.domain.vo.SoSalesOrderLineVo;

import java.util.List;

/**
 * 销售订单Service接口
 *
 * @author ruoyi
 */
public interface ISoSalesOrderHeaderService {

    String STATUS_DRAFT = "DRAFT";
    String STATUS_CONFIRMED = "CONFIRMED";
    String STATUS_FULFILLED = "FULFILLED";
    String STATUS_CLOSED = "CLOSED";
    String STATUS_CANCELLED = "CANCELLED";

    TableDataInfo<SoSalesOrderHeaderVo> selectPageList(SoSalesOrderHeaderBo bo, PageQuery pageQuery);

    List<SoSalesOrderHeaderVo> selectList(SoSalesOrderHeaderBo bo);

    SoSalesOrderHeaderVo selectById(Long id);

    int insertByBo(SoSalesOrderHeaderBo bo);

    int updateByBo(SoSalesOrderHeaderBo bo);

    int deleteByIds(Long[] ids);

    List<SoSalesOrderLineVo> selectLineListBySoNo(String soNo);

    void confirm(Long soId);

    void fulfill(Long soId);

    void close(Long soId);

    void cancel(Long soId, String reason);
}
